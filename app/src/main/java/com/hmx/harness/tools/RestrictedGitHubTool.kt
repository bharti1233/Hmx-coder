package com.hmx.harness.tools

import com.hmx.harness.github.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File

/** One structured capability, separate from unauthenticated shell git_* tools. */
class RestrictedGitHubTool(private val token: suspend () -> String?) : Tool {
    override val name = "github_git"
    override val description = "Perform restricted GitHub Git operations in the selected local workspace. " +
        "Supports clone, fetch, pull (fast-forward only), status, diff (file summary), branches, inspect_commit, " +
        "create_branch, stage, commit, push, force_push and delete_remote_branch. " +
        "Every call asks for exact repository/operation approval; no shell syntax or credentials are accepted. " +
        "Clone requires a new relative folder and explicit branch. Unsupported on SSH/SAF/privileged-only workspaces."
    override val isReadOnly = false
    override val parametersSchema = Schema.obj(mapOf(
        "operation" to Schema.string("clone | fetch | pull | status | diff | branches | inspect_commit | create_branch | stage | commit | push | force_push | delete_remote_branch"),
        "repository" to Schema.string("Canonical https://github.com/owner/repository.git URL."),
        "directory" to Schema.string("Relative repository directory; default '.'; clone requires a new folder."),
        "target" to Schema.string("Exact branch for network/branch operations; file path for stage; message for commit."),
    ), required = listOf("operation", "repository"))

    override fun isAvailable(ctx: ToolContext) = ctx.workspace.shellRoot != null &&
        ctx.workspace !is com.hmx.harness.workspace.SshFs && !ctx.workspace.isSaf

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        require(isAvailable(ctx)) { "Restricted Git requires a locally accessible workspace." }
        require(args.keys.all { it in setOf("operation", "repository", "directory", "target") }) { "Unknown GitHub argument." }
        fun text(key: String) = args[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        val operation = GitHubOperation.valueOf(text("operation").uppercase())
        require(operation != GitHubOperation.CHANGE_REMOTE) { "Remote modification is not supported." }
        val url = text("repository")
        require(url == gitHubRepositoryUrl(url)) { "Use a canonical GitHub HTTPS URL ending in .git." }
        val root = requireNotNull(ctx.workspace.shellRoot).canonicalFile
        val relative = text("directory").ifBlank { "." }
        require(!File(relative).isAbsolute && relative.split('/').none { it == ".." || it == ".git" }) { "Invalid repository path." }
        val directory = File(root, relative).canonicalFile
        require(directory.toPath().startsWith(root.toPath())) { "Repository is outside the selected workspace." }
        val target = text("target")
        val engine = RestrictedGitExecutor()
        return lock.withLock {
            try {
                val head = if (operation == GitHubOperation.CLONE) "" else withContext(Dispatchers.IO) {
                    require(engine.remote(directory) == url) { "Repository scope mismatch." }
                    engine.head(directory)
                }
                val request = GitHubCapabilityRequest(url, operation, target, head)
                // All calls grant only this request, never a reusable repository permission.
                val approved = withTimeout(300_000) { ctx.approveGitHub(
                    "Repository: $url\nOperation: $operation\nTarget: $target\nDirectory: $relative\nHEAD: $head\n" +
                        "Remote changes: ${operation in setOf(GitHubOperation.PUSH, GitHubOperation.FORCE_PUSH, GitHubOperation.DELETE_REMOTE_BRANCH)}\n" +
                        "Destructive: ${operation in setOf(GitHubOperation.FORCE_PUSH, GitHubOperation.DELETE_REMOTE_BRANCH)}") }
                if (!approved) return@withLock ToolResult(false, "GitHub operation denied.")
                val job = currentCoroutineContext()[Job]
                val gateway = GitHubCapabilityGateway(
                    permission = { if (ctx.sandboxOff) GitHubPermission.ADVANCED else GitHubPermission.CONTROLLED_WRITE },
                    repositories = { setOf(url) }, approveExact = { it == request && approved },
                    executeAuthorized = { checked ->
                        require(directory.canonicalFile.toPath().startsWith(root.toPath())) { "Workspace path changed after approval." }
                        val secret = if (operation in NETWORK) token() else null
                        try {
                            withTimeout(180_000) {
                                runInterruptible(Dispatchers.IO) {
                                    engine.execute(directory, checked, secret) { job?.isActive != true || Thread.currentThread().isInterrupted }
                                }
                            }.let { output -> if (secret.isNullOrEmpty()) output else output.replace(secret, "[redacted]") }
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { error("Git operation failed. Check connection, repository configuration, branch and account permissions. Local changes were not reset; inspect state before retrying.") }
                    },
                )
                ToolResult(true, SecretRedactor.redact(gateway.execute(request)).take(24_000))
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { ToolResult(false, "Restricted Git operation was refused or failed. Check workspace access, repository configuration, target and GitHub connection. No automatic retry was performed.") }
        }
    }

    companion object {
        private val lock = Mutex()
        private val NETWORK = setOf(GitHubOperation.CLONE, GitHubOperation.FETCH, GitHubOperation.PULL,
            GitHubOperation.PUSH, GitHubOperation.FORCE_PUSH, GitHubOperation.DELETE_REMOTE_BRANCH)
    }
}
