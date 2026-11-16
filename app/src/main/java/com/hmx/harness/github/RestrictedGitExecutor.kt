package com.hmx.harness.github

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.*
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.FS_POSIX
import org.eclipse.jgit.util.ProcessResult

/** No shell, hooks, helper, filter, SSH or submodule execution in this path. */
internal class RestrictedGitExecutor {
    init { RestrictedGitRuntime.initialize() }
    /** Desktop GC uses java.lang.management for PID locks, unavailable on Android.
     * Maintenance remains an explicit public shell operation, outside authenticated transport.
     */
    private class AndroidRepository(builder: FileRepositoryBuilder) :
        org.eclipse.jgit.internal.storage.file.FileRepository(builder) {
        override fun autoGC(monitor: org.eclipse.jgit.lib.ProgressMonitor) { }
    }
    private class NoPrograms : FS_POSIX() {
        init { setUmask(0x3f) }
        override fun newInstance(): FS = NoPrograms()
        override fun discoverGitExe(): File? = null
        override fun runInShell(command: String, args: Array<out String>): ProcessBuilder =
            error("External programs are disabled for restricted Git operations.")
        override fun runHookIfPresent(repository: Repository, hookName: String, args: Array<out String>,
            out: OutputStream?, err: OutputStream?, input: String?): ProcessResult =
            ProcessResult(0, ProcessResult.Status.NOT_PRESENT)
    }

    fun open(root: File): Repository {
        val directory = File(root, ".git")
        require(directory.isDirectory && !Files.isSymbolicLink(directory.toPath())) { "Select a normal local repository root; linked worktrees are unsupported." }
        require(!File(directory, "objects/info/alternates").exists()) { "Repositories with alternate object stores are unsupported." }
        // Reject metadata symlinks before opening refs/objects/config through JGit.
        Files.walk(directory.toPath()).use { paths ->
            require(paths.noneMatch { Files.isSymbolicLink(it) }) { "Symlink repository metadata is unsupported." }
        }
        val builder = FileRepositoryBuilder().setFS(NoPrograms()).setGitDir(directory).setWorkTree(root)
            .setMustExist(true).setup()
        return AndroidRepository(builder).also { repo ->
                try { validate(repo) } catch (error: Exception) { repo.close(); throw error }
            }
    }

    private fun validate(repo: Repository) {
        val config = repo.config
        val forbidden = setOf("include", "includeif", "filter", "credential", "url", "http", "https", "submodule", "diff", "merge", "alias")
        require(config.sections.none { it.lowercase() in forbidden }) { "Repository configuration contains unsupported execution or transport overrides." }
        require(config.getNames("core").none { it.lowercase() in setOf("hookspath", "sshcommand", "worktree", "attributesfile") }) {
            "Repository execution/path overrides are unsupported."
        }
        require(config.getString("remote", "origin", "pushurl") == null) { "Separate push URLs are unsupported." }
        require(config.getString("remote", "origin", "uploadpack") == null && config.getString("remote", "origin", "receivepack") == null) {
            "Remote executable overrides are unsupported."
        }
    }

    fun remote(root: File): String = open(root).use { repo ->
        val url = repo.config.getString("remote", "origin", "url") ?: error("Repository has no origin.")
        require(url == gitHubRepositoryUrl(url)) { "Origin must be a canonical GitHub HTTPS URL ending in .git." }
        url
    }

    fun head(root: File): String = open(root).use { it.resolve("HEAD")?.name.orEmpty() }

    fun execute(root: File, request: GitHubCapabilityRequest, token: String?, cancelled: () -> Boolean): String {
        require(root.canonicalFile == root.absoluteFile) { "Symlink workspace paths are unsupported." }
        require(!cancelled()) { "Git operation cancelled." }
        val expected = gitHubRepositoryUrl(request.repository)
        val credentials = object : CredentialsProvider() {
            override fun isInteractive() = false
            override fun supports(vararg items: CredentialItem): Boolean = items.all {
                it is CredentialItem.Username || it is CredentialItem.Password
            }
            override fun get(uri: URIish, vararg items: CredentialItem): Boolean {
                if (cancelled() || token.isNullOrBlank() || !supports(*items) || uri.toString() != expected) return false
                items.forEach { when (it) {
                    is CredentialItem.Username -> it.value = "x-access-token"
                    is CredentialItem.Password -> it.value = token.toCharArray()
                } }
                return true
            }
        }
        val transport = org.eclipse.jgit.api.TransportConfigCallback { t ->
            require(t is TransportHttp && t.uri.toString() == expected) { "Only the approved GitHub HTTPS transport is allowed." }
            t.setCredentialsProvider(credentials)
            t.timeout = 30
        }
        val monitor = object : org.eclipse.jgit.lib.ProgressMonitor {
            override fun start(totalTasks: Int) { }
            override fun beginTask(title: String, totalWork: Int) { }
            override fun update(completed: Int) { }
            override fun endTask() { }
            override fun isCancelled() = cancelled() || Thread.currentThread().isInterrupted
        }
        if (request.operation == GitHubOperation.CLONE) {
            require(!root.exists()) { "Clone destination already exists." }
            require(Repository.isValidRefName("refs/heads/${request.target}")) { "Specify the branch to clone." }
            Git.init().setDirectory(root).setFs(NoPrograms()).call().use { git ->
                    git.repository.config.setString("remote", "origin", "url", expected)
                    git.repository.config.save()
                }
            execute(root, request.copy(operation = GitHubOperation.FETCH, expectedHead = ""), token, cancelled)
            open(root).use { repo ->
                Git(repo).checkout().setCreateBranch(true).setName(request.target)
                    .setStartPoint("refs/remotes/origin/${request.target}").call()
            }
            return "Cloned ${request.target}."
        }
        return open(root).use { repo ->
            require(repo.config.getString("remote", "origin", "url") == expected) { "Repository scope changed." }
            require(request.expectedHead == (repo.resolve("HEAD")?.name.orEmpty())) { "Repository HEAD changed; review the operation again." }
            // In-memory override only; never write credential or HTTP settings to the repository.
            repo.config.setString("http", null, "followRedirects", "false")
            repo.config.setBoolean("http", null, "sslVerify", true)
            val git = Git(repo)
            fun branch(): String = request.target.also {
                require(Repository.isValidRefName("refs/heads/$it") && !it.startsWith("-")) { "Invalid branch." }
            }
            when (request.operation) {
                GitHubOperation.STATUS -> git.status().call().let { "Added: ${it.added}\nModified: ${it.modified}\nMissing: ${it.missing}\nUntracked: ${it.untracked}" }
                GitHubOperation.DIFF -> git.diff().call().joinToString("\n") { "${it.changeType}: ${it.oldPath} -> ${it.newPath}" }
                GitHubOperation.BRANCHES -> git.branchList().call().joinToString("\n") { it.name }
                GitHubOperation.INSPECT_COMMIT -> git.log().setMaxCount(20).call().joinToString("\n") { "${it.name} ${it.shortMessage}" }
                GitHubOperation.FETCH, GitHubOperation.PULL -> {
                    git.fetch().setRemote(expected).setRefSpecs(RefSpec("+refs/heads/${branch()}:refs/remotes/origin/${branch()}"))
                        .setProgressMonitor(monitor)
                        .setCredentialsProvider(credentials).setTransportConfigCallback(transport).setTimeout(30).call()
                    if (request.operation == GitHubOperation.PULL) {
                        require(repo.branch == branch()) { "Pull target must be the currently checked-out branch." }
                        val result = git.merge().include(repo.resolve("refs/remotes/origin/${branch()}"))
                            .setFastForward(MergeCommand.FastForwardMode.FF_ONLY).call()
                        check(result.mergeStatus.isSuccessful) { "Fast-forward pull was refused; resolve local changes explicitly." }
                    }
                    "${request.operation} completed for ${branch()}."
                }
                GitHubOperation.CREATE_BRANCH -> { git.branchCreate().setName(branch()).call(); "Branch created." }
                GitHubOperation.STAGE -> {
                    val path = request.target
                    require(path.isNotBlank() && !File(path).isAbsolute && path.split('/').none { it in setOf("..", ".git", ".harness") }) { "Invalid stage path." }
                    require(File(root, path).canonicalFile.toPath().startsWith(root.toPath())) { "Stage path escapes workspace." }
                    git.add().addFilepattern(path).call(); "Selected path staged."
                }
                GitHubOperation.COMMIT -> {
                    require(request.target.isNotBlank()) { "Commit message is required." }
                    git.commit().setMessage(request.target).setAuthor("HMXHARNESS", "harness@android.local")
                        .setCommitter("HMXHARNESS", "harness@android.local").call().let { "Committed ${it.name}." }
                }
                GitHubOperation.PUSH, GitHubOperation.FORCE_PUSH, GitHubOperation.DELETE_REMOTE_BRANCH -> {
                    val ref = if (request.operation == GitHubOperation.DELETE_REMOTE_BRANCH) ":refs/heads/${branch()}"
                        else "${request.expectedHead}:refs/heads/${branch()}"
                    val results = git.push().setRemote(expected).setRefSpecs(RefSpec(ref))
                        .setProgressMonitor(monitor)
                        .setForce(request.operation == GitHubOperation.FORCE_PUSH).setCredentialsProvider(credentials)
                        .setTransportConfigCallback(transport).setTimeout(30).call()
                    check(results.flatMap { it.remoteUpdates }.all { it.status in setOf(RemoteRefUpdate.Status.OK, RemoteRefUpdate.Status.UP_TO_DATE) }) {
                        "Remote rejected the operation. Check repository access and branch protection."
                    }
                    "Remote operation completed for ${branch()}."
                }
                else -> error("Operation is unsupported by restricted Git.")
            }
        }
    }
}
