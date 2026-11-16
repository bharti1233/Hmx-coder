package com.hmx.harness.data

/** No original exception or storage contents are exposed to UI/log callers. */
enum class CredentialStorageProblem(val recoveryMessage: String) {
    MISSING_KEY("The device encryption key is missing. Saved encrypted credentials were preserved. Restore access to the original device key if possible; otherwise reconnect services using newly entered credentials."),
    INVALIDATED_KEY("Android invalidated the device encryption key. Saved encrypted credentials were preserved. Credentials must be re-entered if the key cannot be recovered."),
    UNREADABLE_STORAGE("Secure credential storage could not be read. Unlock the device and retry. If corruption persists, preserve the existing data before reconnecting services."),
}

class CredentialStorageException(val problem: CredentialStorageProblem) :
    IllegalStateException(problem.recoveryMessage)
