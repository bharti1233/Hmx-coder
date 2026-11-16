package com.hmx.harness.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

class ApkDownloader(
    private val directory: File,
    private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build(),
) {
    private val activeCall = AtomicReference<okhttp3.Call?>(null)

    suspend fun download(release: UpdateRelease, onProgress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val output = File(directory, "pending-update.apk")
        val partial = File(directory, "pending-update.apk.part")
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                val offset = partial.length().takeIf { it > 0L && it < release.apkBytes }
                val request = Request.Builder().url(release.apkUrl).apply {
                    if (offset != null) header("Range", "bytes=$offset-")
                }.build()
                val call = client.newCall(request)
                activeCall.set(call)
                call.execute().use { response ->
                    if (!response.isSuccessful && response.code !in listOf(416)) error("Download failed: HTTP ${response.code}")
                    if (response.code == 416) {
                        partial.delete()
                        error("The server rejected the resume request.")
                    }
                    val append = offset != null && response.code == 206
                    val total = release.apkBytes.takeIf { it > 0 } ?: response.body?.contentLength()?.let { if (append) it + offset!! else it } ?: 0L
                    var read = if (append) offset!! else 0L
                    response.body?.byteStream()?.use { input ->
                        val outputStream = java.io.FileOutputStream(partial, append)
                        outputStream.use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                read += count
                                require(read <= UpdateContract.MAX_APK_BYTES) { "The downloaded APK is too large." }
                                out.write(buffer, 0, count)
                                onProgress(read, total)
                            }
                            out.fd.sync()
                        }
                    } ?: error("The download response is empty.")
                    require(read == release.apkBytes) { "The download was incomplete." }
                    require(partial.renameTo(output)) { "Could not stage the downloaded APK." }
                }
                activeCall.set(null)
                return@withContext output
            } catch (error: Throwable) {
                activeCall.set(null)
                lastError = error
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (attempt < 2) kotlinx.coroutines.delay((attempt + 1) * 1000L)
            }
        }
        throw IOException(lastError?.message ?: "The update download failed.", lastError)
    }

    fun cancel() { activeCall.getAndSet(null)?.cancel() }

    companion object {
        fun resumeOffset(partialBytes: Long, expectedBytes: Long): Long =
            partialBytes.takeIf { it > 0 && it < expectedBytes } ?: 0L
    }
}
