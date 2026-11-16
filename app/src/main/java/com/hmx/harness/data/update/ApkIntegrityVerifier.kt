package com.hmx.harness.data.update

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class ApkIntegrityVerifier {
    fun verify(file: File, release: UpdateRelease) {
        require(file.isFile && file.length() == release.apkBytes) { "The staged APK size is invalid." }
        require(release.apkSha256.matches(Regex("[0-9a-fA-F]{64}"))) { "The release checksum is invalid." }
        val actual = sha256(file)
        require(MessageDigest.isEqual(actual.decodeHex(), release.apkSha256.lowercase().decodeHex())) {
            "The APK checksum did not match the signed release metadata."
        }
        runCatching {
            ZipFile(file).use { zip -> require(zip.getEntry("AndroidManifest.xml") != null) { "The staged file is not a valid APK." } }
        }.getOrElse { throw IllegalArgumentException("The staged file is not a valid APK.", it) }
    }

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun String.decodeHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
