package com.hmx.harness.data.update

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class UpdateManager(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") legacyAccess: Any? = null,
) {
    enum class Stage { CHECK, DOWNLOAD, VERIFY, INSTALL }

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val installed: InstalledVersion, val checkedAtMs: Long) : State
        data class Available(val release: UpdateRelease, val installed: InstalledVersion, val checkedAtMs: Long, val fromCache: Boolean) : State
        data class Downloading(val release: UpdateRelease, val bytes: Long, val total: Long, val percent: Int) : State
        data class DownloadComplete(val release: UpdateRelease, val apk: File) : State
        data class Verifying(val release: UpdateRelease, val apk: File) : State
        data class ReadyToInstall(val release: UpdateRelease, val apk: File) : State
        data class PermissionRequired(val release: UpdateRelease, val apk: File) : State
        data class Installing(val release: UpdateRelease) : State
        data class InstallCancelled(val release: UpdateRelease) : State
        data class Installed(val release: UpdateRelease) : State
        data class Failed(val stage: Stage, val message: String, val release: UpdateRelease? = null) : State
    }

    private val store = UpdateStore(context)
    private val repository = UpdateRepository(context, store = store)
    private val downloader = ApkDownloader(File(context.filesDir, "update"))
    private val integrity = ApkIntegrityVerifier()
    private val signatures = ApkSignatureVerifier(context)
    private val installer = UpdateInstaller(context, store)
    private val operation = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var downloadJob: Job? = null
    private var activeRelease: UpdateRelease? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // Kept as a short compatibility name for existing Compose call sites.
    val step: StateFlow<State> = state

    private val _lastCheckedAt = MutableStateFlow(store.lastCheckedAtMs)
    val lastCheckedAt: StateFlow<Long> = _lastCheckedAt.asStateFlow()

    init {
        scope.launch {
            UpdateInstallEvents.events.collect { event -> handleInstallEvent(event) }
        }
    }

    suspend fun checkOnForeground() { foregroundChecks.run { check() } }

    private val foregroundChecks = ForegroundUpdateChecks()

    suspend fun check(manual: Boolean = false, notify: Boolean = !manual): Boolean = withContextIo {
        if (_state.value is State.Downloading || _state.value is State.Verifying || _state.value is State.Installing) return@withContextIo true
        operation.withLock {
            _state.value = State.Checking
            val installed = installedVersion()
            val result = repository.latest()
            val now = System.currentTimeMillis()
            _lastCheckedAt.value = now
            when (result) {
                is UpdateRepository.Result.Failure -> {
                    store.rememberCheckFailure()
                    val cached = repository.cached()
                    val decision = UpdateChecker.decide(installed, cached?.metadata)
                    if (decision is UpdateDecision.Available && cached != null) {
                        activeRelease = cached
                        _state.value = State.Available(cached, installed, now, true)
                        true
                    } else {
                        _state.value = if (manual) State.Failed(Stage.CHECK, result.message) else State.Idle
                        false
                    }
                }
                is UpdateRepository.Result.Success -> {
                    store.rememberCheckSuccess(now)
                    val release = result.release
                    if (release == null) {
                        _state.value = State.UpToDate(installed, now)
                        return@withLock true
                    }
                    val decision = UpdateChecker.decide(installed, release.metadata)
                    when (decision) {
                        is UpdateDecision.Available -> {
                            checkNotNull(release)
                            activeRelease = release
                            _state.value = State.Available(release, installed, now, result.fromCache)
                            if (notify && store.lastNotifiedVersionCode < release.versionCode) {
                                if (UpdateNotificationManager(context).notifyAvailable(release)) {
                                    store.lastNotifiedVersionCode = release.versionCode
                                }
                            }
                        }
                        UpdateDecision.UpToDate -> _state.value = State.UpToDate(installed, now)
                        is UpdateDecision.Rejected -> _state.value = if (manual) State.Failed(Stage.CHECK, decision.reason.message) else State.Idle
                    }
                    true
                }
            }
        }
    }

    fun downloadUpdate() {
        val current = _state.value
        val release = when (current) {
            is State.Available -> current.release
            is State.Failed -> current.release
            is State.InstallCancelled -> current.release
            else -> null
        } ?: return
        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            operation.withLock {
                try {
                    _state.value = State.Downloading(release, 0, release.apkBytes, 0)
                    val apk = downloader.download(release) { bytes, total ->
                        val effectiveTotal = total.coerceAtLeast(release.apkBytes)
                        _state.value = State.Downloading(release, bytes, effectiveTotal, (bytes * 100 / effectiveTotal.coerceAtLeast(1)).toInt().coerceIn(0, 100))
                    }
                    _state.value = State.DownloadComplete(release, apk)
                    _state.value = State.Verifying(release, apk)
                    integrity.verify(apk, release)
                    signatures.verify(apk, release)
                    _state.value = State.ReadyToInstall(release, apk)
                } catch (cancelled: CancellationException) {
                    _state.value = State.Available(release, installedVersion(), System.currentTimeMillis(), false)
                } catch (error: Throwable) {
                    val stage = if (_state.value is State.Downloading) Stage.DOWNLOAD else Stage.VERIFY
                    _state.value = State.Failed(stage, error.message ?: "The update could not be completed.", release)
                }
            }
        }
    }

    fun cancelDownload() {
        downloader.cancel()
        downloadJob?.cancel()
    }

    fun installUpdate() {
        val current = _state.value
        val ready = when (current) {
            is State.ReadyToInstall -> current
            is State.PermissionRequired -> current
            else -> return
        }
        scope.launch(Dispatchers.IO) {
            when (val result = installer.install(ready.apk, ready.release)) {
                UpdateInstaller.Result.Started -> _state.value = State.Installing(ready.release)
                UpdateInstaller.Result.PermissionRequired -> _state.value = State.PermissionRequired(ready.release, ready.apk)
                is UpdateInstaller.Result.Failed -> _state.value = State.Failed(Stage.INSTALL, result.message, ready.release)
            }
        }
    }

    fun dismiss() {
        when (_state.value) {
            is State.Downloading, is State.Verifying -> cancelDownload()
            is State.Installing -> return
            else -> _state.value = State.Idle
        }
    }

    fun refreshInstallState() {
        val status = store.lastInstallStatus
        if (status == android.content.pm.PackageInstaller.STATUS_SUCCESS) {
            activeRelease?.let { _state.value = State.Installed(it) }
            store.clearInstallResult()
        }
    }

    private fun handleInstallEvent(event: InstallEvent) {
        val release = activeRelease ?: return
        if (event.versionCode != release.versionCode) return
        _state.value = when (val outcome = event.outcome) {
            InstallOutcome.Success -> {
                File(context.filesDir, "update/pending-update.apk").delete()
                UpdateNotificationManager(context).cancel()
                State.Installed(release)
            }
            InstallOutcome.Cancelled -> State.InstallCancelled(release)
            is InstallOutcome.Failed -> State.Failed(Stage.INSTALL, outcome.message, release)
        }
    }

    private fun installedVersion(): InstalledVersion {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        return InstalledVersion(context.packageName, info.versionName.orEmpty(), code)
    }

    private suspend fun <T> withContextIo(block: suspend () -> T): T = kotlinx.coroutines.withContext(Dispatchers.IO, block)
}
