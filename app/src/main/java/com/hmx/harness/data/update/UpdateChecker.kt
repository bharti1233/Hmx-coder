package com.hmx.harness.data.update

/** The installed application, read once per check and after every install attempt. */
data class InstalledVersion(
    val applicationId: String,
    val versionName: String,
    val versionCode: Long,
)

enum class UpdateRejection(val message: String) {
    PACKAGE_MISMATCH("This release belongs to a different application."),
    DOWNGRADE("This release is older than the installed version."),
    INVALID_METADATA("This release has invalid update metadata."),
    UNSUPPORTED_CHANNEL("This release is not on the stable channel."),
    MINIMUM_VERSION("This device's version is too old for an in-place update."),
}

sealed interface UpdateDecision {
    data class Available(val metadata: UpdateMetadata, val mandatory: Boolean) : UpdateDecision
    data object UpToDate : UpdateDecision
    data class Rejected(val reason: UpdateRejection) : UpdateDecision
}

/**
 * Pure update policy. Version comparison is by `versionCode` only, so a
 * re-tagged release can never look newer, and a rebuilt 1.0.0 can never be
 * offered twice.
 */
object UpdateChecker {
    fun decide(
        installed: InstalledVersion,
        metadata: UpdateMetadata?,
        problem: UpdateMetadataProblem? = null,
    ): UpdateDecision {
        if (metadata == null) return UpdateDecision.Rejected(UpdateRejection.INVALID_METADATA)
        val structural = problem ?: metadata.problem()
        if (structural != null) {
            return UpdateDecision.Rejected(
                when (structural) {
                    UpdateMetadataProblem.PACKAGE_MISMATCH -> UpdateRejection.PACKAGE_MISMATCH
                    UpdateMetadataProblem.UNSUPPORTED_CHANNEL -> UpdateRejection.UNSUPPORTED_CHANNEL
                    else -> UpdateRejection.INVALID_METADATA
                },
            )
        }
        if (installed.applicationId != metadata.applicationId) {
            return UpdateDecision.Rejected(UpdateRejection.PACKAGE_MISMATCH)
        }
        return when {
            metadata.versionCode < installed.versionCode -> UpdateDecision.Rejected(UpdateRejection.DOWNGRADE)
            metadata.versionCode == installed.versionCode -> UpdateDecision.UpToDate
            installed.versionCode < metadata.minSupportedVersionCode ->
                UpdateDecision.Rejected(UpdateRejection.MINIMUM_VERSION)
            else -> UpdateDecision.Available(metadata, metadata.mandatory)
        }
    }
}
