package com.hmx.harness.github

/** Structured authorization only; never accepts an executable or a shell command. */
internal enum class GitHubPermission { READ_ONLY, CONTROLLED_WRITE, ADVANCED }
internal enum class GitHubOperation {
    CLONE, FETCH, PULL, STATUS, DIFF, BRANCHES, INSPECT_COMMIT,
    CREATE_BRANCH, STAGE, COMMIT, PUSH, FORCE_PUSH, DELETE_REMOTE_BRANCH, CHANGE_REMOTE,
}
internal data class GitHubCapabilityRequest(
    val repository: String,
    val operation: GitHubOperation,
    val target: String,
    val expectedHead: String,
)

/**
 * Approval callback belongs to the UI/run manager, not model-supplied arguments.
 * The executor receives no reusable approval or token. Unsupported executors fail
 * before any credential access. Wiring a production executor remains mandatory.
 */
internal class GitHubCapabilityGateway(
    private val permission: () -> GitHubPermission,
    private val repositories: () -> Set<String>,
    private val approveExact: suspend (GitHubCapabilityRequest) -> Boolean,
    private val executeAuthorized: suspend (GitHubCapabilityRequest) -> String,
) {
    suspend fun execute(request: GitHubCapabilityRequest): String {
        val canonical = gitHubRepositoryUrl(request.repository)
        require(request.repository == canonical) { "Use the canonical GitHub HTTPS repository address." }
        require(request.target.length <= 4096 && request.target.none { it.isISOControl() }) { "Invalid operation target." }
        require(request.expectedHead.isEmpty() || request.expectedHead.matches(Regex("[0-9a-f]{40,64}"))) { "Invalid repository revision." }
        authorize(request)
        if (request.operation in CONFIRMED) {
            check(approveExact(request)) { "This exact GitHub operation requires user approval." }
            authorize(request) // Permissions may have changed while the dialog was open.
        }
        return executeAuthorized(request)
    }

    private fun authorize(request: GitHubCapabilityRequest) {
        check(request.repository in repositories()) { "Repository is outside the authorized scope." }
        val mode = permission()
        check(when (request.operation) {
            in ADVANCED -> mode == GitHubPermission.ADVANCED
            in WRITE -> mode != GitHubPermission.READ_ONLY
            else -> true
        }) { "GitHub permission does not allow this operation." }
    }

    companion object {
        private val ADVANCED = setOf(GitHubOperation.FORCE_PUSH, GitHubOperation.DELETE_REMOTE_BRANCH, GitHubOperation.CHANGE_REMOTE)
        private val WRITE = setOf(GitHubOperation.CREATE_BRANCH, GitHubOperation.STAGE, GitHubOperation.COMMIT, GitHubOperation.PUSH)
        private val CONFIRMED = ADVANCED + GitHubOperation.PUSH
    }
}
