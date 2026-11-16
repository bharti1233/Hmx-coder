package com.hmx.harness.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private fun metadata(
        packageName: String = UpdateContract.APPLICATION_ID,
        versionCode: Long = 2,
        channel: String = UpdateContract.STABLE_CHANNEL,
        checksum: String = "a".repeat(64),
    ) = UpdateMetadata(
        schemaVersion = UpdateContract.SCHEMA_VERSION,
        applicationId = packageName,
        versionName = "1.1.0",
        versionCode = versionCode,
        channel = channel,
        apkAssetName = "HMXHARNESS-release.apk",
        apkUrl = "https://github.com/${UpdateContract.REPOSITORY}/releases/download/v1.1.0/HMXHARNESS-release.apk",
        apkSha256 = checksum,
        apkSizeBytes = 1024,
        releaseNotes = "Notes",
        minSupportedVersionCode = 1,
        mandatory = false,
    )

    @Test fun metadataRejectsBadSchemaPackageChannelAndChecksum() {
        assertEquals(UpdateMetadataProblem.INVALID_SCHEMA, metadata().copy(schemaVersion = 2).problem())
        assertEquals(UpdateMetadataProblem.PACKAGE_MISMATCH, metadata(packageName = "com.other.app").problem())
        assertEquals(UpdateMetadataProblem.UNSUPPORTED_CHANNEL, metadata(channel = "beta").problem())
        assertEquals(UpdateMetadataProblem.INVALID_SHA256, metadata(checksum = "bad").problem())
        assertTrue(UpdateMetadata.parse("{}").let { it is UpdateMetadata.ParseResult.Invalid })
    }

    @Test fun versionPolicyUsesVersionCodeAndRejectsDowngradesAndIdentityChanges() {
        val installed = InstalledVersion(UpdateContract.APPLICATION_ID, "1.0.0", 1)
        assertTrue(UpdateChecker.decide(installed, metadata()) is UpdateDecision.Available)
        assertEquals(UpdateDecision.UpToDate, UpdateChecker.decide(installed.copy(versionCode = 2), metadata()))
        assertEquals(UpdateRejection.DOWNGRADE, (UpdateChecker.decide(installed.copy(versionCode = 3), metadata()) as UpdateDecision.Rejected).reason)
        assertEquals(UpdateRejection.PACKAGE_MISMATCH,
            (UpdateChecker.decide(installed, metadata(packageName = "com.other.app")) as UpdateDecision.Rejected).reason)
        assertEquals(UpdateRejection.MINIMUM_VERSION,
            (UpdateChecker.decide(installed.copy(versionCode = 1), metadata().copy(minSupportedVersionCode = 2)) as UpdateDecision.Rejected).reason)
    }

    @Test fun urlsAreRestrictedToThisRepositoriesReleaseAssets() {
        assertTrue(UpdateContract.trustedDownloadUrl("https://github.com/${UpdateContract.REPOSITORY}/releases/download/v1/app.apk"))
        assertFalse(UpdateContract.trustedDownloadUrl("https://github.com.evil/${UpdateContract.REPOSITORY}/releases/download/v1/app.apk"))
        assertFalse(UpdateContract.trustedDownloadUrl("https://github.com/${UpdateContract.REPOSITORY}/releases/download/v1/app.apk?token=x"))
        assertFalse(UpdateContract.trustedDownloadUrl("https://github.com/elsewhere/repo/releases/download/v1/app.apk"))
    }

    @Test fun verifierRejectsPackageVersionAndCertificateMismatch() {
        val candidate = ApkIdentity(UpdateContract.APPLICATION_ID, "1.1.0", 2, setOf("trusted"))
        fun validate(identity: ApkIdentity, installed: Set<String> = setOf("trusted")) = ApkVerificationPolicy.validate(
            UpdateContract.APPLICATION_ID, UpdateContract.APPLICATION_ID, identity, 2, 1, installed, setOf("trusted"),
        )
        assertTrue(runCatching { validate(candidate) }.isSuccess)
        assertThrows(IllegalArgumentException::class.java) { validate(candidate.copy(packageName = "com.other.app")) }
        assertThrows(IllegalArgumentException::class.java) { validate(candidate.copy(versionCode = 1)) }
        assertThrows(IllegalArgumentException::class.java) { validate(candidate.copy(signerSha256 = setOf("foreign"))) }
        assertThrows(IllegalArgumentException::class.java) { validate(candidate, installed = setOf("other")) }
    }

    @Test fun downloaderUsesOnlyAValidPartialFileAsResumeOffset() {
        assertEquals(123L, ApkDownloader.resumeOffset(123L, 1000L))
        assertEquals(0L, ApkDownloader.resumeOffset(1000L, 1000L))
        assertEquals(0L, ApkDownloader.resumeOffset(0L, 1000L))
    }
}
