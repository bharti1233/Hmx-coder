package com.hmx.harness.data.update

import android.content.Context

/**
 * Small persistent state for the updater: the last check, the last cached
 * release, the notification dedupe marker and the outcome of the last install
 * session. Nothing here is sensitive; the downloaded APK and its metadata are
 * validated again before every install.
 */
class UpdateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app-updates", Context.MODE_PRIVATE)

    var lastCheckedAtMs: Long
        get() = prefs.getLong(KEY_LAST_CHECKED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECKED, value).apply()

    var lastCheckFailed: Boolean
        get() = prefs.getBoolean(KEY_LAST_CHECK_FAILED, false)
        set(value) = prefs.edit().putBoolean(KEY_LAST_CHECK_FAILED, value).apply()

    /** Highest version code the user has already been notified about. */
    var lastNotifiedVersionCode: Long
        get() = prefs.getLong(KEY_NOTIFIED, 0L)
        set(value) = prefs.edit().putLong(KEY_NOTIFIED, value).apply()

    var cachedMetadataJson: String?
        get() = prefs.getString(KEY_METADATA, null)
        set(value) = prefs.edit().putString(KEY_METADATA, value).apply()

    var cachedTag: String?
        get() = prefs.getString(KEY_TAG, null)
        set(value) = prefs.edit().putString(KEY_TAG, value).apply()

    var cachedName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var cachedHtmlUrl: String?
        get() = prefs.getString(KEY_HTML, null)
        set(value) = prefs.edit().putString(KEY_HTML, value).apply()

    var cachedEtag: String?
        get() = prefs.getString(KEY_ETAG, null)
        set(value) = prefs.edit().putString(KEY_ETAG, value).apply()

    /** PackageInstaller status of the most recent session, or [NO_INSTALL_RESULT]. */
    var lastInstallStatus: Int
        get() = prefs.getInt(KEY_INSTALL_STATUS, NO_INSTALL_RESULT)
        set(value) = prefs.edit().putInt(KEY_INSTALL_STATUS, value).apply()

    var lastInstallVersionCode: Long
        get() = prefs.getLong(KEY_INSTALL_VERSION, 0L)
        set(value) = prefs.edit().putLong(KEY_INSTALL_VERSION, value).apply()

    var lastInstallMessage: String?
        get() = prefs.getString(KEY_INSTALL_MESSAGE, null)
        set(value) = prefs.edit().putString(KEY_INSTALL_MESSAGE, value).apply()

    var lastInstallAtMs: Long
        get() = prefs.getLong(KEY_INSTALL_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_INSTALL_AT, value).apply()

    fun rememberCheckSuccess(at: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_CHECKED, at).putBoolean(KEY_LAST_CHECK_FAILED, false).apply()
    }

    fun rememberCheckFailure() {
        prefs.edit().putBoolean(KEY_LAST_CHECK_FAILED, true).apply()
    }

    fun rememberRelease(metadataJson: String, tag: String, name: String, htmlUrl: String, etag: String?) {
        prefs.edit()
            .putString(KEY_METADATA, metadataJson)
            .putString(KEY_TAG, tag)
            .putString(KEY_NAME, name)
            .putString(KEY_HTML, htmlUrl)
            .apply { if (etag != null) putString(KEY_ETAG, etag) }
            .apply()
    }

    fun rememberInstallResult(status: Int, versionCode: Long, message: String?) {
        prefs.edit()
            .putInt(KEY_INSTALL_STATUS, status)
            .putLong(KEY_INSTALL_VERSION, versionCode)
            .putString(KEY_INSTALL_MESSAGE, message)
            .putLong(KEY_INSTALL_AT, System.currentTimeMillis())
            .apply()
    }

    fun clearInstallResult() {
        prefs.edit().remove(KEY_INSTALL_STATUS).remove(KEY_INSTALL_VERSION)
            .remove(KEY_INSTALL_MESSAGE).remove(KEY_INSTALL_AT).apply()
    }

    /** Re-arms the notification for a version the user was already told about. */
    fun resetNotificationFor(versionCode: Long) {
        if (lastNotifiedVersionCode == versionCode) prefs.edit().putLong(KEY_NOTIFIED, 0L).apply()
    }

    companion object {
        const val NO_INSTALL_RESULT = Int.MIN_VALUE
        private const val KEY_LAST_CHECKED = "last_checked_at"
        private const val KEY_LAST_CHECK_FAILED = "last_check_failed"
        private const val KEY_NOTIFIED = "last_notified_version_code"
        private const val KEY_METADATA = "cached_metadata"
        private const val KEY_TAG = "cached_tag"
        private const val KEY_NAME = "cached_name"
        private const val KEY_HTML = "cached_html_url"
        private const val KEY_ETAG = "cached_etag"
        private const val KEY_INSTALL_STATUS = "install_status"
        private const val KEY_INSTALL_VERSION = "install_version"
        private const val KEY_INSTALL_MESSAGE = "install_message"
        private const val KEY_INSTALL_AT = "install_at"
    }
}
