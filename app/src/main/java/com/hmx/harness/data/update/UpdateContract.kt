package com.hmx.harness.data.update

import java.net.URI

/**
 * Fixed contract for the GitHub Releases update channel.
 *
 * Everything the updater trusts is anchored here: the repository it may talk
 * to, the application id an update must carry, the channel name and the
 * signing certificates a release APK must be signed with. A metadata document
 * cannot widen any of these.
 */
object UpdateContract {
    const val APPLICATION_ID = "com.hmx.harness"
    const val REPOSITORY = "bharti1233/Hmx-coder"
    const val SCHEMA_VERSION = 1
    const val STABLE_CHANNEL = "stable"
    const val METADATA_ASSET = "update.json"
    const val RELEASES_API = "https://api.github.com/repos/$REPOSITORY/releases"
    const val LATEST_RELEASE_API = "$RELEASES_API/latest"

    /** Hard ceiling for a downloaded APK; releases are ~64 MB. */
    const val MAX_APK_BYTES = 512L * 1024 * 1024

    /** Release notes are rendered as markdown; refuse pathological documents. */
    const val MAX_NOTES_CHARS = 120_000

    /** `update.json` is a small document; a bigger one is not ours. */
    const val MAX_METADATA_BYTES = 256 * 1024

    /**
     * SHA-256 of the HMXHARNESS release signing certificate (DER), the same
     * certificate the installed app is signed with:
     * CN=hmxpanel, OU=HMXPANEL, O=HMXPANEL, L=mumbai, ST=Maharashtra, C=IN.
     *
     * A set, not a single value, so a planned key rotation can add the new
     * certificate here in the same release that starts signing with it.
     */
    val TRUSTED_CERTIFICATE_SHA256: Set<String> = setOf(
        "eee6dd0e68a80af4a7270fe62bbf525b921280c3471fba1960fca976c0478fc7",
    )

    private const val DOWNLOAD_PREFIX = "/$REPOSITORY/releases/download/"

    /**
     * Only `https://github.com/bharti1233/Hmx-coder/releases/download/…` is
     * accepted: no other host, no credentials, no query string and no
     * fragment that could redirect the download somewhere else.
     */
    fun trustedDownloadUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" &&
            uri.host == "github.com" &&
            uri.port in listOf(-1, 443) &&
            uri.userInfo == null &&
            uri.query == null &&
            uri.fragment == null &&
            uri.path.startsWith(DOWNLOAD_PREFIX) &&
            uri.path.length > DOWNLOAD_PREFIX.length
    }.getOrDefault(false)

    /** An asset name we are willing to look up and stage under our own name. */
    fun safeAssetName(name: String): Boolean =
        name.isNotBlank() &&
            name.length <= 120 &&
            name.endsWith(".apk", ignoreCase = true) &&
            !name.startsWith(".") &&
            name.none { it == '/' || it == '\\' || it == ':' || it == '\u0000' || it.isWhitespace() }

    /** Release tags are display strings only; they never drive version logic. */
    fun safeTag(tag: String): Boolean =
        tag.isNotBlank() && tag.length <= 80 && tag.none { it.isWhitespace() || it == '<' || it == '>' }
}
