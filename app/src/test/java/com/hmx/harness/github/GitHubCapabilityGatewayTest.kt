package com.hmx.harness.github

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GitHubCapabilityGatewayTest {
    private val repo = "https://github.com/owner/repository.git"
    private fun request(operation: GitHubOperation) = GitHubCapabilityRequest(repo, operation, "main", "a".repeat(40))

    @Test fun permissionAndScopeAreCheckedBeforeExecution() = runBlocking {
        var executed = false
        val gateway = GitHubCapabilityGateway({ GitHubPermission.READ_ONLY }, { setOf(repo) }, { true }, { executed = true; "ok" })
        assertEquals("ok", gateway.execute(request(GitHubOperation.FETCH)))
        executed = false
        try { gateway.execute(request(GitHubOperation.PUSH)); fail("allowed write") } catch (_: IllegalStateException) { }
        assertFalse(executed)
        try { gateway.execute(request(GitHubOperation.FETCH).copy(repository = "https://github.com/other/repo.git")); fail("allowed scope") }
        catch (_: IllegalStateException) { }
        assertFalse(executed)
    }

    @Test fun pushApprovalBindsEveryRequestAndIsNotCached() = runBlocking {
        val approvals = mutableListOf<GitHubCapabilityRequest>()
        val gateway = GitHubCapabilityGateway({ GitHubPermission.CONTROLLED_WRITE }, { setOf(repo) }, { approvals += it; true }, { "ok" })
        val first = request(GitHubOperation.PUSH)
        val second = first.copy(target = "other")
        gateway.execute(first); gateway.execute(second)
        assertEquals(listOf(first, second), approvals)
    }

    @Test fun advancedAndRevokedPermissionsFailClosed() = runBlocking {
        var mode = GitHubPermission.ADVANCED
        var executed = false
        val gateway = GitHubCapabilityGateway({ mode }, { setOf(repo) }, { mode = GitHubPermission.READ_ONLY; true }, { executed = true; "ok" })
        try { gateway.execute(request(GitHubOperation.FORCE_PUSH)); fail("permission revoked") } catch (_: IllegalStateException) { }
        assertFalse(executed)
    }

    @Test fun arbitraryShellIsNotARepositoryCapability() = runBlocking {
        val gateway = GitHubCapabilityGateway({ GitHubPermission.ADVANCED }, { setOf(repo) }, { fail("approval"); false }, { fail("execution"); "" })
        for (value in listOf("git clone $repo", "$repo; env", "sh -c git", "$repo | cat", "\$(env)")) {
            try { gateway.execute(request(GitHubOperation.CLONE).copy(repository = value)); fail("accepted shell") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
