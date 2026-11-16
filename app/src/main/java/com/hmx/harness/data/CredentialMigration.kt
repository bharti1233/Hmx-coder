package com.hmx.harness.data

/** Read-back verification must complete before a recoverable legacy record is removed. */
internal object CredentialMigration {
    fun load(
        readSecure: () -> String?,
        readLegacy: () -> String?,
        validate: (String) -> Unit,
        writeSecure: (String) -> Unit,
        removeLegacy: () -> Unit,
    ): String? {
        val secure = readSecure()
        if (secure != null) {
            validate(secure)
            // Includes retry after a crash between verification and legacy cleanup.
            removeLegacy()
            return secure
        }
        val legacy = readLegacy() ?: return null
        validate(legacy)
        writeSecure(legacy)
        check(readSecure() == legacy) { "Credential migration could not be verified. Original data was preserved. Retry after unlocking the device." }
        removeLegacy()
        return legacy
    }
}
