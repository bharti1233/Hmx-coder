package com.hmx.harness.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

data class ApkIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signerSha256: Set<String>,
)

object ApkVerificationPolicy {
    fun validate(
        installedPackage: String,
        expectedPackage: String,
        candidate: ApkIdentity,
        expectedVersionCode: Long,
        installedVersionCode: Long,
        installedSigners: Set<String>,
        trustedCertificates: Set<String>,
    ) {
        require(installedPackage == expectedPackage && candidate.packageName == expectedPackage) {
            "The APK belongs to a different application."
        }
        require(candidate.versionCode == expectedVersionCode && candidate.versionCode > installedVersionCode) {
            "The APK is not a newer compatible version."
        }
        require(candidate.signerSha256.isNotEmpty() && candidate.signerSha256.all { it in trustedCertificates }) {
            "The APK signing certificate is not trusted."
        }
        require(installedSigners.isNotEmpty() && candidate.signerSha256 == installedSigners) {
            "The APK signing identity does not match the installed application."
        }
    }
}

class ApkSignatureVerifier(private val context: Context) {
    fun verify(file: File, release: UpdateRelease) {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("The downloaded file is not a readable APK.")
        val identity = ApkIdentity(
            packageName = candidate.packageName,
            versionName = candidate.versionName.orEmpty(),
            versionCode = candidate.versionCodeLongCompat(),
            signerSha256 = candidate.signerDigests(),
        )
        val installedSigners = installed.signerDigests()
        ApkVerificationPolicy.validate(
            installedPackage = context.packageName,
            expectedPackage = UpdateContract.APPLICATION_ID,
            candidate = identity,
            expectedVersionCode = release.versionCode,
            installedVersionCode = installed.versionCodeLongCompat(),
            installedSigners = installedSigners,
            trustedCertificates = UpdateContract.TRUSTED_CERTIFICATE_SHA256,
        )
        require((candidate.applicationInfo?.minSdkVersion ?: 1) <= Build.VERSION.SDK_INT) {
            "This update requires a newer Android version."
        }
    }

    private fun PackageInfo.versionCodeLongCompat(): Long =
        if (Build.VERSION.SDK_INT >= 28) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()

    private fun PackageInfo.signerDigests(): Set<String> {
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= 28) {
            signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION") this.signatures.orEmpty()
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
