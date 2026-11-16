package com.hmx.harness.data.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * `update.json`, published as an asset of every stable GitHub Release.
 *
 * The document is the single source of truth for an available update. The
 * release tag and marketing name are display data; the version code, package
 * id, checksum and certificate are the parts policy is enforced on.
 */
@Serializable
data class UpdateMetadata(
    val schemaVersion: Int = 0,
    val applicationId: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val channel: String = "",
    val apkAssetName: String = "",
    val apkUrl: String = "",
    val apkSha256: String = "",
    val apkSizeBytes: Long = 0,
    val releaseNotes: String = "",
    val minSupportedVersionCode: Long = 0,
    val mandatory: Boolean = false,
    val publishedAt: String? = null,
) {
    /** First structural problem, or null when the document is acceptable. */
    fun problem(): UpdateMetadataProblem? = when {
        schemaVersion != UpdateContract.SCHEMA_VERSION -> UpdateMetadataProblem.INVALID_SCHEMA
        applicationId != UpdateContract.APPLICATION_ID -> UpdateMetadataProblem.PACKAGE_MISMATCH
        channel != UpdateContract.STABLE_CHANNEL -> UpdateMetadataProblem.UNSUPPORTED_CHANNEL
        versionCode <= 0 -> UpdateMetadataProblem.INVALID_VERSION_CODE
        !VERSION_NAME.matches(versionName) -> UpdateMetadataProblem.INVALID_VERSION_NAME
        !UpdateContract.safeAssetName(apkAssetName) -> UpdateMetadataProblem.INVALID_ASSET
        !UpdateContract.trustedDownloadUrl(apkUrl) -> UpdateMetadataProblem.UNTRUSTED_URL
        !SHA256.matches(apkSha256) -> UpdateMetadataProblem.INVALID_SHA256
        apkSizeBytes <= 0 || apkSizeBytes > UpdateContract.MAX_APK_BYTES -> UpdateMetadataProblem.INVALID_SIZE
        minSupportedVersionCode < 0 || minSupportedVersionCode > versionCode -> UpdateMetadataProblem.INVALID_MIN_VERSION
        releaseNotes.length > UpdateContract.MAX_NOTES_CHARS -> UpdateMetadataProblem.NOTES_TOO_LONG
        else -> null
    }

    companion object {
        /** 1, 1.2, 1.2.3, 1.2.3-beta.1 — never a free-form string. */
        private val VERSION_NAME = Regex("""[0-9]+(?:\.[0-9]+){0,3}(?:[.-][A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?""")
        private val SHA256 = Regex("[0-9a-fA-F]{64}")

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parses and validates in one step. Callers get either a usable
         * document or the exact reason it was refused — never a partial one.
         */
        fun parse(text: String): ParseResult {
            if (text.isBlank() || text.length > 256 * 1024) return ParseResult.Invalid(UpdateMetadataProblem.MALFORMED)
            val metadata = runCatching { json.decodeFromString(serializer(), text) }
                .getOrElse { return ParseResult.Invalid(UpdateMetadataProblem.MALFORMED) }
            metadata.problem()?.let { return ParseResult.Invalid(it) }
            return ParseResult.Valid(metadata)
        }

        /** Extracts the asset list of a GitHub release without trusting its ordering. */
        fun requiredString(root: JsonObject, key: String): String? =
            (root[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        fun flag(root: JsonObject, key: String): Boolean =
            (root[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false

        fun releaseObject(text: String): JsonObject? =
            runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
    }

    sealed interface ParseResult {
        data class Valid(val metadata: UpdateMetadata) : ParseResult
        data class Invalid(val problem: UpdateMetadataProblem) : ParseResult
    }
}

/** Why a metadata document was refused. Each maps to one user-facing sentence. */
enum class UpdateMetadataProblem(val message: String) {
    INVALID_SCHEMA("The update metadata uses an unsupported schema."),
    PACKAGE_MISMATCH("The update is for a different application."),
    UNSUPPORTED_CHANNEL("The update is not published on the stable channel."),
    INVALID_VERSION_CODE("The update does not declare a positive version code."),
    INVALID_VERSION_NAME("The update does not declare a valid version name."),
    INVALID_ASSET("The update does not name a valid APK asset."),
    UNTRUSTED_URL("The update points at a download URL outside this project's releases."),
    INVALID_SHA256("The update does not include a valid SHA-256 checksum."),
    INVALID_SIZE("The update declares an impossible APK size."),
    INVALID_MIN_VERSION("The update declares an impossible minimum version."),
    NOTES_TOO_LONG("The release notes are too large to display."),
    MALFORMED("The update metadata could not be read."),
}

/**
 * A validated release ready to be shown, downloaded and installed.
 *
 * Field names deliberately match what the existing update dialog renders.
 */
data class UpdateRelease(
    val metadata: UpdateMetadata,
    val tag: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val apkUrl: String,
    val apkName: String,
    val apkBytes: Long,
    val apkSha256: String,
) {
    val versionCode: Long get() = metadata.versionCode
    val versionName: String get() = metadata.versionName
    val mandatory: Boolean get() = metadata.mandatory
}
