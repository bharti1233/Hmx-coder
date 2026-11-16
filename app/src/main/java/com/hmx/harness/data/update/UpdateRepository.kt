package com.hmx.harness.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class UpdateRepository(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val store: UpdateStore = UpdateStore(context),
) {
    sealed interface Result {
        data class Success(val release: UpdateRelease?, val fromCache: Boolean = false) : Result
        data class Failure(val message: String, val rateLimited: Boolean = false) : Result
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): Result = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(UpdateContract.LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply { store.cachedEtag?.let { header("If-None-Match", it) } }
            .build()

        val response = runCatching { execute(request) }.getOrElse {
            return@withContext Result.Failure("Could not reach GitHub. Check your connection.")
        }
        response.use { result ->
            if (result.code == 304) return@withContext cachedOrFailure("GitHub returned no cached release.")
            if (result.code == 404) return@withContext Result.Success(null)
            if (!result.isSuccessful) {
                val limited = result.code == 403 || result.code == 429
                val reset = result.header("X-RateLimit-Reset")?.toLongOrNull()
                val suffix = reset?.let { " Try again after ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date(it * 1000))}." }.orEmpty()
                return@withContext if (limited) Result.Failure("GitHub rate limit reached.$suffix", true)
                else Result.Failure("GitHub update check failed (HTTP ${result.code}).")
            }
            val body = result.body?.string().orEmpty()
            return@withContext try {
                parseRelease(body, result.header("ETag"))
            } catch (error: Throwable) {
                Result.Failure(error.message ?: "The published update could not be read.")
            }
        }
    }

    fun cached(): UpdateRelease? = runCatching {
        val metadata = store.cachedMetadataJson?.let { UpdateMetadata.parse(it) }
            as? UpdateMetadata.ParseResult.Valid ?: return null
        UpdateRelease(
            metadata = metadata.metadata,
            tag = store.cachedTag.orEmpty(),
            name = store.cachedName.orEmpty().ifBlank { metadata.metadata.versionName },
            body = metadata.metadata.releaseNotes,
            htmlUrl = store.cachedHtmlUrl.orEmpty(),
            apkUrl = metadata.metadata.apkUrl,
            apkName = metadata.metadata.apkAssetName,
            apkBytes = metadata.metadata.apkSizeBytes,
            apkSha256 = metadata.metadata.apkSha256,
        )
    }.getOrNull()

    private fun cachedOrFailure(message: String): Result {
        val cached = cached()
        return if (cached != null) Result.Success(cached, fromCache = true) else Result.Failure(message)
    }

    private suspend fun parseRelease(text: String, etag: String?): Result {
        val root = json.parseToJsonElement(text).jsonObject
        if (root["draft"]?.jsonPrimitive?.booleanOrNull == true ||
            root["prerelease"]?.jsonPrimitive?.booleanOrNull == true
        ) return Result.Success(null)

        val assets = root["assets"]?.jsonArray ?: return Result.Success(null)
        val metadataAsset = assets.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull == UpdateContract.METADATA_ASSET }
            ?: return Result.Success(null)
        val metadataUrl = metadataAsset.jsonObject["browser_download_url"]?.jsonPrimitive?.contentOrNull
            ?: return Result.Failure("The stable release metadata asset has no download URL.")
        if (!UpdateContract.trustedDownloadUrl(metadataUrl)) return Result.Failure("The release metadata URL is not trusted.")

        val metadataText = fetchText(metadataUrl)
        val parsed = when (val value = UpdateMetadata.parse(metadataText)) {
            is UpdateMetadata.ParseResult.Valid -> value.metadata
            is UpdateMetadata.ParseResult.Invalid -> return Result.Failure(value.problem.message)
        }
        val apkAsset = assets.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull == parsed.apkAssetName }
            ?: return Result.Failure("The release APK asset is missing.")
        val browserUrl = apkAsset.jsonObject["browser_download_url"]?.jsonPrimitive?.contentOrNull
            ?: return Result.Failure("The release APK has no download URL.")
        val size = apkAsset.jsonObject["size"]?.jsonPrimitive?.longOrNull ?: 0L
        if (browserUrl != parsed.apkUrl || (size > 0 && size != parsed.apkSizeBytes)) {
            return Result.Failure("The release APK asset does not match its metadata.")
        }
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val name = root["name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { tag }
        val html = root["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        store.rememberRelease(metadataText, tag, name, html, etag)
        return Result.Success(
            UpdateRelease(parsed, tag, name, root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { parsed.releaseNotes }, html,
                parsed.apkUrl, parsed.apkAssetName, parsed.apkSizeBytes, parsed.apkSha256),
        )
    }

    private suspend fun fetchText(url: String): String {
        val response = execute(Request.Builder().url(url).header("Accept", "application/octet-stream").build())
        response.use {
            check(it.isSuccessful) { "Metadata download failed (HTTP ${it.code})." }
            val body = it.body ?: error("The metadata asset is empty.")
            require(body.contentLength() <= UpdateContract.MAX_METADATA_BYTES || body.contentLength() < 0) { "The metadata asset is too large." }
            val source = body.source()
            val bytes = source.readByteArray(UpdateContract.MAX_METADATA_BYTES.toLong() + 1)
            require(bytes.size <= UpdateContract.MAX_METADATA_BYTES) { "The metadata asset is too large." }
            return bytes.toString(Charsets.UTF_8)
        }
    }

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (continuation.isActive) continuation.resume(response)
                else response.close()
            }
        })
    }
}
