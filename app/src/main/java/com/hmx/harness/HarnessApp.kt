package com.hmx.harness

import android.app.Application
import android.content.Context
import android.webkit.WebView
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.room.Room
import com.hmx.harness.agent.AgentEngine
import com.hmx.harness.agent.TodoStore
import com.hmx.harness.data.ChatBackupManager
import com.hmx.harness.data.CheckpointStore
import com.hmx.harness.data.ImageStore
import com.hmx.harness.data.KeyStoreManager
import com.hmx.harness.data.ProviderRepository
import com.hmx.harness.data.SessionRepository
import com.hmx.harness.data.SettingsRepository
import com.hmx.harness.data.db.AppDatabase
import com.hmx.harness.llm.ProviderFactory
import com.hmx.harness.llm.effectiveApiKey
import com.hmx.harness.tools.ToolRegistry
import com.hmx.harness.workspace.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class HarnessApp : Application() {
    private val memoryScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        com.hmx.harness.local.GpuDeliveryFactory.attach(this)
    }
    lateinit var container: AppContainer
        private set
    var credentialStorageUnavailable: Boolean = false
        private set
    var credentialRecoveryMessage: String? = null
        private set

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate() {
        super.onCreate()
        // Paused LazyColumn prefetch crashes in LayoutNode.onChildRemoved when chat items change.
        ComposeFoundationFlags.isPausableCompositionInPrefetchEnabled = false
        WebView.enableSlowWholeDocumentDraw()
        // Before any workspace is resolved, so no tool ever sees a filtered shared-storage tree.
        com.hmx.harness.data.env.StorageGate.install(packageName,
            granted = { com.hmx.harness.data.env.StorageAccess.isGranted(this) })
        try {
            KeyStoreManager(this).verifyAvailable()
        } catch (error: Exception) {
            // Keep the original encrypted files intact. MainActivity explains recovery.
            credentialStorageUnavailable = true
            credentialRecoveryMessage = (error as? com.hmx.harness.data.CredentialStorageException)?.problem?.recoveryMessage
                ?: com.hmx.harness.data.CredentialStorageProblem.UNREADABLE_STORAGE.recoveryMessage
            return
        }
        container = AppContainer(this)
        com.hmx.harness.data.update.UpdateWorker.schedule(this)
        com.hmx.harness.data.env.StorageGate.installPrivilegedRoute { cwd ->
            container.deviceAccess.liveTier(cwd).let {
                it == com.hmx.harness.data.env.ExecutionTier.ROOT || it == com.hmx.harness.data.env.ExecutionTier.PRIVILEGED
            }
        }
        memoryScope.launch { container.settings.ensureRumbleCombo() }
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::container.isInitialized && level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            memoryScope.launch { container.localModels.releaseCachedModel() }
        }
    }
    override fun onLowMemory() {
        super.onLowMemory()
        if (::container.isInitialized) memoryScope.launch { container.localModels.releaseCachedModel() }
    }
}

class AppContainer(val appContext: Context) {
    /** Deep-link channel: run-result notifications deliver a session id here. */
    val pendingSessionId = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    val pendingSubagentChat = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String>?>(null)
    /** Successful repository imports open their own files, independent of the old chat's workspace. */
    val pendingImportedWorkspace = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Share-target channel: content awaiting a regular Chat destination and draft import. */
    val pendingShare = kotlinx.coroutines.flow.MutableStateFlow<com.hmx.harness.data.PendingShare?>(null)

    /** Selected-code prompt waiting for its specific chat composer. */
    val pendingCodeDraft = kotlinx.coroutines.flow.MutableStateFlow<com.hmx.harness.ui.files.CodeChatDraft?>(null)

    /** Build/test failure prompt waiting for a fresh chat to run it. */
    val pendingAgentPrompt = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** In-app nav channel: a screen asking Settings to scroll to a section. */
    val pendingSettingsScroll = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val keys = KeyStoreManager(appContext)
    init { com.hmx.harness.llm.ProviderNetwork.settings = keys::providerProxy }
    val settings = SettingsRepository(appContext)

    /** Durable request log; opened lazily so a user who never enables it pays nothing. */
    val debugLog: com.hmx.harness.llm.debug.DebugLogRepository by lazy {
        com.hmx.harness.llm.debug.DebugLogRepository(
            com.hmx.harness.llm.debug.DebugLogDatabase.open(appContext).dao(),
        ) { id -> db.dao().session(id)?.title }
    }

    @Volatile private var debugPersist = false

    init {
        // Provider debug console: persisted switches. History lives in its own database,
        // so nothing has to be restored into memory at startup.
        val debugCapture = com.hmx.harness.llm.debug.DebugCapture
        // The file-based archive is gone; drop its leftover so no old log lingers.
        runCatching { java.io.File(appContext.filesDir, "provider-debug-traces.enc").delete() }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            settings.settings.collect { value ->
                debugCapture.enabled = value.debugConsoleEnabled
                debugCapture.captureBodies = value.debugConsoleBodies
                debugPersist = value.debugConsolePersist && value.debugConsoleEnabled
            }
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            // A live stream republishes every half second; debounce the write so a
            // running answer does not hit SQLite on every event.
            var pending: kotlinx.coroutines.Job? = null
            debugCapture.store.traces.collect { traces ->
                if (!debugPersist) return@collect
                pending?.cancel()
                pending = launch {
                    kotlinx.coroutines.delay(800)
                    debugLog.save(traces)
                    debugLog.prune()
                }
            }
        }
    }
    private val configurationRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val pluginManager = com.hmx.harness.plugins.PluginManager(appContext, configurationRevision,
        com.hmx.harness.plugins.PluginDownloads(githubToken = { github.accessToken() })) { pluginRuntime }
    private val pluginRuntime by lazy {
        object : com.hmx.harness.plugins.PluginRuntime {
            override val ready get() = linuxEnv.isReady
            override suspend fun available(wanted: List<String>): List<String> {
                val index = linuxEnv.getPackageIndex()
                // A fresh environment needs its core shell before any runtime can start.
                val core = if (linuxEnv.bashExecutable() == null) linuxEnv.corePackages else emptyList()
                return (core + wanted).distinct().filter { it in index }
            }
            override suspend fun install(packages: List<String>, requester: String,
                progress: suspend (com.hmx.harness.data.env.PackageInstallStatus) -> Unit) {
                if (packages.isNotEmpty()) linuxEnv.install(packages, requester, progress).getOrThrow()
            }
            override fun process(command: String) = linuxEnv.shellProcessBuilder(command)
        }
    }
    val configuration = com.hmx.harness.config.HarnessConfigRepository(appContext, keys, pluginManager, configurationRevision)
    val pluginHooks = com.hmx.harness.config.PluginHooks(appContext) { linuxEnv.shellProcessBuilder(it) }
    internal val runtimePolicy = AppRuntimePolicy(appContext, settings)
    /** Voice providers (speech-to-text and text-to-speech) with per-provider keys. */
    val voiceProviders = com.hmx.harness.data.audio.VoiceProviderRepository(appContext, keys)
    /** Microsoft Edge voice catalogue, cached on disk for a day. */
    val edgeVoices = com.hmx.harness.data.audio.EdgeVoiceCatalog(appContext)
    /** Keeps the advertised Edge build current so read-aloud is not refused. */
    val edgeVersion = com.hmx.harness.data.audio.EdgeVersionProvider(appContext)
    /** Reads assistant replies with Android TTS or the selected voice provider. */
    val tts = com.hmx.harness.data.audio.TtsController(appContext, settings, keys, voiceProviders, edgeVoices, edgeVersion)
    internal val editorScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val editorDrafts = com.hmx.harness.ui.files.EditorDraftStore(
        java.io.File(appContext.noBackupFilesDir, "editor-drafts"), editorScope)
    internal val editorTabs = com.hmx.harness.ui.files.EditorTabsStore(
        java.io.File(appContext.noBackupFilesDir, "editor-tabs"), editorScope)
    val localModels = com.hmx.harness.local.LocalModelManager(appContext)
    val chatGpt = com.hmx.harness.chatgpt.ChatGptAccounts(keys::chatGptCredentials, keys::putChatGptCredentials,
        onConnected = { config ->
            settings.setActiveSelection(config.id, config.model)
        }, onDisconnected = { id ->
            runManager.runningSessionIds.value.filter {
                val saved = runManager.controls.flow(it).value
                saved.provider?.id == id || id in saved.usedProviderIds ||
                    runManager.subagents.runs.value.any { run -> run.sessionId == it && run.status.active && run.providerId == id }
            }
                .forEach { runManager.stopAndJoin(it) }
        })
    val providers: ProviderRepository = ProviderRepository(appContext, keys, localModels, chatGpt, settings)

    init {
        com.hmx.harness.llm.RotatingKeyProvider.pools = providers::controls
        ProviderFactory.chatGptProvider = com.hmx.harness.chatgpt.ChatGptProvider(chatGpt::accessToken,
            modelEntry = { id, model -> chatGpt.state.value.accounts.firstOrNull { it.providerId == id }?.models?.firstOrNull { it.id == model } })
        com.hmx.harness.llm.ModelCatalog.chatGptModels = { chatGpt.refreshModels(it) }
        ProviderFactory.localProvider = com.hmx.harness.local.LocalModelProvider(localModels) {
            settings.settings.first().localModelAgentContext
        }
    }
    val screenshotPolicy = com.hmx.harness.data.ScreenshotPolicy()

    private val db = Room.databaseBuilder(appContext, AppDatabase::class.java, "harness.db")
        .addMigrations(
            AppDatabase.MIGRATION_25_26, AppDatabase.MIGRATION_24_25, AppDatabase.MIGRATION_23_24, AppDatabase.MIGRATION_22_23, AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_18_19, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14, AppDatabase.MIGRATION_14_15, AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18,
        )
        .addCallback(AppDatabase.OVERSIZED_ROW_SANITIZER)
        // Only kicks in when no migration path exists (pre-v4 databases);
        // the v4→v5 path above preserves sessions and usage totals.
        .fallbackToDestructiveMigration(true)
        .build()
    val images = ImageStore(appContext)
    val sessions = SessionRepository(db, images)
    val memory = com.hmx.harness.data.MemoryRepository(db, settings)
    val chatModels = com.hmx.harness.data.ChatModelRepository(settings, providers, sessions)
    suspend fun startChatAgent(profile: com.hmx.harness.data.ChatAgentProfile): String {
        profile.validated()
        val id = sessions.createSession(profile.name, workspace.currentProjectOnce().id,
            com.hmx.harness.data.ConversationConfig(kind = com.hmx.harness.data.ConversationKind.CHAT, agent = profile))
        chatModels.initialize(id)
        settings.setLastActiveSessionId(id)
        return id
    }

    private suspend fun canRecallChat(from: String?, target: String): Boolean {
        val source = from?.let { sessions.session(it) } ?: return false
        val destination = sessions.session(target) ?: return false
        val config = com.hmx.harness.data.ConversationConfig.decode(source.conversationJson)
        return com.hmx.harness.data.ChatHistoryPolicy.canRead(config,
            com.hmx.harness.data.ConversationConfig.decode(destination.conversationJson), settings.settings.first())
    }
    val chatBackup by lazy { ChatBackupManager(db, appContext, workspace) }
    val snippets = com.hmx.harness.data.SnippetRepository(db.dao())

    val sshConnections = com.hmx.harness.workspace.SshConnections(keys)
    val workspace = WorkspaceManager(appContext, db.dao(), sshConnections)
    val fileHistory = com.hmx.harness.data.WorkspaceFileHistory(java.io.File(appContext.filesDir, "workspace-file-history"))
    val checkpoints = CheckpointStore(db.dao(), fileHistory)

    val todoStore = TodoStore()
    val repoMap = com.hmx.harness.repomap.RepoMapCache()
    private val fetchClient = OkHttpClient()
    val linuxEnv =
        com.hmx.harness.data.env.LinuxEnvironmentManager(appContext, githubTokenSaver = keys::migrateGitHubToken) { keys.githubToken() }
    val wirelessAdb = com.hmx.harness.data.env.adb.WirelessAdbManager(appContext)
    val shizuku = com.hmx.harness.data.env.ShizukuManager(appContext, wireless = wirelessAdb)
    val dhizuku = com.hmx.harness.data.env.DhizukuManager(appContext)
    val deviceAccess = com.hmx.harness.data.env.DeviceAccessManager(appContext, shizuku, linuxEnv,
        projectForSession = { sessions.session(it)?.projectId }, dhizuku = dhizuku)
    val shellRouter = com.hmx.harness.data.env.ShellTierRouter(appContext, shizuku, linuxEnv, deviceAccess)
    val codeGraph = com.hmx.harness.data.env.CodeGraphManager(linuxEnv, shellRouter)

    init {
        // Package-set changes must invalidate the deployed-copy cache, or the
        // privileged tier keeps serving the old toolchain until a restart.
        linuxEnv.deployStateListener = { shizuku.invalidateDeployState() }
    }

    val updates = com.hmx.harness.data.update.UpdateManager(appContext, deviceAccess)
    internal val sync by lazy { com.hmx.harness.data.transfer.SyncManager(keys, sshConnections) { fs, action ->
        runManager.withIdleWorkspace(fs, "Stop running tasks in this workspace before syncing files.", action)
    } }
    internal val ftpShare by lazy { com.hmx.harness.data.transfer.FtpShareManager(appContext) { fs, action ->
        runManager.withIdleWorkspace(fs, "Stop running tasks in this workspace before changing shared files.", action)
    } }
    val backgroundProcesses = com.hmx.harness.data.BgProcessStore(
        appContext, linuxEnv, shizuku, workspaceRoot = workspace.appPrivateRoot, deviceAccess = deviceAccess,
    )

    init { deviceAccess.stopBackgroundTasks = backgroundProcesses::stopPrivilegedTasks }

    @Volatile
    private var projectSkillsDir: java.io.File? = null
    private val disabledSkills = AtomicReference<Set<String>>(emptySet())
    private val webSearchProvider = AtomicReference("keyless")
    private val searchKeyMigrated = AtomicBoolean(false)

    /**
     * web_search backend config, resolved per call so Settings changes apply
     * to the very next search without a restart. Null = keyless engines.
     */
    val searchApiConfig: com.hmx.harness.tools.SearchApiConfig?
        get() {
            val provider = webSearchProvider.get()
            if (provider == "keyless") return null
            return searchApiConfigFor(provider, rotate = false).takeIf { it.keys.isNotEmpty() }
        }

    /**
     * Enabled keys in this call's order: rotation moves the start key, the rest are
     * failover. Tool descriptions read it without [rotate] so they do not skip a turn.
     */
    private fun searchApiConfigFor(provider: String, rotate: Boolean = true): com.hmx.harness.tools.SearchApiConfig {
        val pool = runCatching { keys.searchKeyPool(provider) }.getOrNull()
            ?: return com.hmx.harness.tools.SearchApiConfig(provider, keys.searchApiKey(provider).orEmpty())
        val ordered = (if (rotate) com.hmx.harness.llm.RotatingKeyProvider.orderedKeys("search:$provider", pool)
            else pool.keys.filter { it.enabled }).map { it.value }
        return com.hmx.harness.tools.SearchApiConfig(provider, ordered.firstOrNull().orEmpty(), ordered.drop(1))
    }

    private suspend fun resolveSearchApiConfig(): com.hmx.harness.tools.SearchApiConfig? {
        val provider = settings.settings.first().webSearchProvider
        keys.migrateLegacySearchKey(provider)
        webSearchProvider.set(provider)
        if (provider == "keyless") return null
        return searchApiConfigFor(provider)
    }

    private val cavemanSettings = AtomicReference(com.hmx.harness.data.AppSettings())
    val skills = com.hmx.harness.skills.SkillStore(
        bundled = com.hmx.harness.skills.SkillAssets.load(appContext.assets),
        userDir = java.io.File(appContext.filesDir, "skills").apply { mkdirs() },
        projectDir = { projectSkillsDir },
        disabled = { disabledSkills.get() },
        optionalBundled = {
            if (cavemanSettings.get().cavemanInstalled) com.hmx.harness.caveman.CavemanPolicy.skills else emptyMap()
        },
    )
    val browser = com.hmx.harness.browser.BrowserController(appContext, images)
    val registry = ToolRegistry.default(
        fetchClient, todoStore, backgroundProcesses, linuxEnv, shizuku, shellRouter, skills, codeGraph,
        imageStore = images,
        browserController = browser,
        searchApi = { searchApiConfig },
        searchApiRefresh = ::resolveSearchApiConfig,
        artifactLoader = sessions::artifacts,
        toolOutputLoader = sessions::toolOutput,
        fileHistory = fileHistory,
        memory = memory,
        dhizuku = dhizuku,
        deviceAccess = deviceAccess,
        // Declared below; resolved on each call, after construction.
        pullRequests = { githubRepositories },
        sessionMessages = { sessions.messages(it) },
        appSettings = settings,
    )
    val mcp = com.hmx.harness.tools.mcp.McpManager(appContext, linuxEnv, keys, codeGraph, configuration)
    val github = com.hmx.harness.github.GitHubAuth(BuildConfig.GITHUB_CLIENT_ID,
        keys::githubCredential, keys::putGitHubCredential, { refreshGitHubAuth() })
    fun routedProvider(): com.hmx.harness.llm.LlmProvider =
        com.hmx.harness.llm.FallbackProvider(
                factory = ProviderFactory::create,
                resolve = { target ->
                    providers.providers.first().firstOrNull { it.id == target.providerId && it.enabled }
                        ?.takeIf { provider -> com.hmx.harness.llm.ProviderModelSearch.models(provider, providers.catalog(target.providerId)).any { it.id == target.model } }
                        ?.copy(model = target.model)
                },
                keyFor = { config ->
                    if (config.id == com.hmx.harness.llm.HarnessProvider.ID) providers.harnessApiKey()
                    else {
                        val live = providers.providers.first().firstOrNull { it.id == config.id }
                        if (live == null || live.baseUrl != config.baseUrl || live.type != config.type) ""
                        else config.effectiveApiKey(providers.apiKey(config.id))
                    }
                },
                destination = { config ->
                    if (config.id == com.hmx.harness.llm.HarnessProvider.ID)
                        com.hmx.harness.llm.HarnessProvider.pool[config.model]?.baseUrl ?: config.baseUrl
                    else config.baseUrl
                },
        )

    val engine: AgentEngine = AgentEngine(
        providerFactory = { routedProvider() },
        registry = registry,
        checkpointer = checkpoints,
        imageStore = images,
        linuxEnv = linuxEnv,
        shizuku = shizuku,
        skills = skills,
        resolveConfiguration = { configuration.load(it) },
        pluginHooks = pluginHooks,
        todoStore = todoStore,
        repoMap = repoMap,
        cavemanSettings = { settings.settings.first().also { cavemanSettings.set(it); disabledSkills.set(it.disabledSkills) } },
        configuredSubagent = {
            com.hmx.harness.agent.SubagentConfiguration.configured(
                settings.settings.first(), providers.providers.first(), providers::apiKey,
            )?.let { connection ->
                com.hmx.harness.agent.SubagentConnection(
                    providers.resolveHarnessConfig(connection.config), connection.apiKey,
                )
            }
        },
        prepareGitHub = { github.refreshIfNeeded() },
        deviceAccess = deviceAccess,
        universalMemory = memory,
        chatOnlyTools = listOf(com.hmx.harness.tools.ReadChatTextTool(sessions::messages),
            com.hmx.harness.tools.ViewChatImageTool(images),
            com.hmx.harness.tools.BrowserReadTool(browser),
            com.hmx.harness.tools.SearchChatsTool { query, fuzzy, limit, offset ->
                sessions.searchRecallMessages(query, fuzzy, limit, offset)
            }.apply { canAccess = ::canRecallChat },
            com.hmx.harness.tools.RecentChatsTool(sessions.sessions).apply { canAccess = ::canRecallChat },
            com.hmx.harness.tools.ReadChatSnippetTool(sessions::messages, sessions::session).apply { canAccess = ::canRecallChat },
            com.hmx.harness.tools.OfferCodeHandoffTool(),
            com.hmx.harness.tools.GitHubApiTool(fetchClient) { github.accessToken() },
            com.hmx.harness.tools.GitHubSearchTool(fetchClient) { github.accessToken() },
            com.hmx.harness.tools.GitHubFileTool(fetchClient) { github.accessToken() }),
    )
    val runManager = com.hmx.harness.agent.RunManager(
        context = appContext,
        engine = engine,
        sessions = sessions,
        checkpoints = checkpoints,
        workspace = workspace,
        linuxEnv = linuxEnv,
        settings = settings,
        todoStore = todoStore,
        providers = providers,
        mcp = mcp,
        repoMap = repoMap,
        chatModels = chatModels,
        canRun = runtimePolicy::canRun,
    )
    val githubRepositories = com.hmx.harness.github.GitHubWorkspaces(this)
    val automation = com.hmx.harness.automation.AutomationManager(this)
    val projectDeletion = com.hmx.harness.workspace.ProjectDeletion(this)
    internal var telegram = com.hmx.harness.telegram.TelegramManager(this)
    val interactiveTerminal = com.hmx.harness.terminal.InteractiveTerminalManager(linuxEnv, runManager)
    val terminal = com.hmx.harness.data.TerminalManager(appContext, linuxEnv, shizuku, runManager, shellRouter, interactiveTerminal)
    val storage = com.hmx.harness.data.storage.StorageManager(this)
    val projectChecks = com.hmx.harness.data.ProjectCheckStore(appContext, terminal)

    /**
     * Applies a GitHub token change end-to-end: re-materialize the prefix
     * copies (token file + gitconfig rewrite), then redeploy the shell-tier
     * toolchain so the new auth is live without an app restart.
     */
    val nineRouter by lazy { com.hmx.harness.router.NineRouterManager(appContext, linuxEnv,
        runManager::acquireBackgroundKeepalive, runManager::releaseBackgroundKeepalive, providers, settings) }

    suspend fun refreshGitHubAuth() = linuxEnv.refreshGitHub(shizuku, strict = true)

    init {
        runtimePolicy.start(onBackgroundDisabled = {
            runManager.stopIfBackgroundDisabled()
            if (!runtimePolicy.canRun()) {
                // One unavailable device backend must not disable the policy observer.
                runCatching { telegram.suspendForBackground() }
                runCatching { ftpShare.stop() }
                runCatching { terminal.stopTerminal() }
                runCatching { backgroundProcesses.killAll() }
                AgentService.stop(appContext)
            }
        }, onAvailable = { telegram.restoreIfEnabled() })
        // models.dev thinking-capability catalog: serve the cached copy
        // synchronously, then refresh in the background (daily cadence).
        com.hmx.harness.llm.ModelsDev.load(appContext)
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            combine(settings.settings, providers.providers) { saved, available -> saved to available.map { it.id }.toSet() }
                .collect { (saved, availableIds) ->
                    val selected = listOf(saved.activeProviderId, saved.planningProviderId, saved.executionProviderId, saved.subagentProviderId)
                    if (selected.any { it != null && (com.hmx.harness.local.LocalModelCatalog.isLocal(it) || com.hmx.harness.chatgpt.ChatGptProtocol.isProvider(it)) && it !in availableIds }) {
                        settings.clearMissingLocalProviderSelections(availableIds)
                    }
                }
        }
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Chats that held several workspaces before projects existed each move into a project of their own.
            runCatching { workspace.adoptUnfiledChats() }
        }
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Learned wire per Harness model (chat vs anthropic vs responses),
            // persisted by the first-request probe; mirrored into memory so
            // every request path reads it synchronously.
            providers.harnessWires.collect { com.hmx.harness.llm.HarnessProvider.pins = it }
        }
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Restore routes before fetching; failed refreshes leave the cache intact.
            providers.providers.first()
            when (val result = com.hmx.harness.llm.ModelCatalog.listModels(
                com.hmx.harness.llm.HarnessProvider.config,
                com.hmx.harness.llm.HarnessProvider.KEYLESS,
            )) {
                is com.hmx.harness.llm.ModelCatalog.Result.Models ->
                    providers.saveCatalog(com.hmx.harness.llm.HarnessProvider.ID, result.models)
                is com.hmx.harness.llm.ModelCatalog.Result.Failed -> Unit
            }
        }
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            com.hmx.harness.llm.ModelsDev.refresh(appContext)
        }
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            settings.settings.collect {
                disabledSkills.set(it.disabledSkills)
                cavemanSettings.set(it)
                // The search key store moved to per-provider slots; attribute
                // the pre-split key to whichever provider was active first.
                if (searchKeyMigrated.compareAndSet(false, true)) {
                    keys.migrateLegacySearchKey(it.webSearchProvider)
                }
                webSearchProvider.set(it.webSearchProvider)
            }
        }
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            workspace.current.collect { fs ->
                projectSkillsDir = fs.shellRoot?.resolve(".harness/skills")
            }
        }
        // Regenerate the linker shims the moment the environment is ready: an
        // older format (broken argv[0] for coreutils applets) must not survive
        // an app update, and the header check makes this a no-op when current.
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            linuxEnv.state.collect {
                if (it is com.hmx.harness.data.env.EnvState.Ready) linuxEnv.ensureShims()
            }
        }
        // Eagerly deploy the shell-user toolchain copy the moment both the
        // Linux environment and Shizuku are ready, so every tier works from
        // the first command (foreground shell, background spawn, terminal).
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // include serviceState so a retry fires once the user service binds
            combine(linuxEnv.state, shizuku.state, shizuku.serviceState) { e, s, v -> Triple(e, s, v) }
                .collect { (e, s, v) ->
                    if (e is com.hmx.harness.data.env.EnvState.Ready &&
                        s == com.hmx.harness.data.env.ShizukuState.GRANTED &&
                        v.isReady
                    ) {
                        linuxEnv.ensureShellDeploy(shizuku)
                    }
                }
        }
        // Self-heal prefixes installed by older builds: relink dangling
        // termux-absolute symlinks, rewrite Termux shebangs, resume installs
        // that were interrupted and reinstall packages whose binaries
        // vanished. Silent when the prefix is already healthy; network
        // failures never demote a Ready environment (retried next launch).
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val repaired = linuxEnv.repairIfNeeded()
            // A repair changes prefix content (reinstalled pip, rewritten
            // shebangs) without necessarily changing the package-set hash, and
            // StateFlow conflates the unchanged Ready state so the deploy
            // collector above never re-fires, re-stage the shell-tier copy
            // explicitly so it picks the fixes up.
            if (repaired != null &&
                shizuku.state.value == com.hmx.harness.data.env.ShizukuState.GRANTED &&
                shizuku.serviceState.value.isReady
            ) {
                linuxEnv.ensureShellDeploy(shizuku)
            }
        }
    }
}
