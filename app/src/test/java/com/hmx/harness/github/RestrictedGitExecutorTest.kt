package com.hmx.harness.github

import java.nio.file.Files
import org.eclipse.jgit.api.Git
import org.junit.Assert.*
import org.junit.Test

class RestrictedGitExecutorTest {
    private val url = "https://github.com/owner/repository.git"

    @Test fun androidRepositoryDoesNotRunDesktopAutomaticMaintenance() {
        val root = Files.createTempDirectory("restricted-git-gc-").toFile().canonicalFile
        try {
            val executor = RestrictedGitExecutor()
            Git.init().setDirectory(root).call().close()
            executor.open(root).use { repo ->
                repo.autoGC(org.eclipse.jgit.lib.NullProgressMonitor.INSTANCE)
                assertFalse(java.io.File(root, ".git/gc.pid").exists())
                assertEquals("AndroidRepository", repo.javaClass.simpleName)
            }
            assertFalse(org.eclipse.jgit.util.SystemReader.getInstance().userConfig
                .getBoolean("jmx", "WindowCacheStats", false))
        } finally { root.deleteRecursively() }
    }

    @Test fun statusStageCommitAndBranchDoNotExecuteHooks() {
        val root = Files.createTempDirectory("restricted-git-").toFile().canonicalFile
        try {
            val executor = RestrictedGitExecutor()
            Git.init().setDirectory(root).call().use { git ->
                git.repository.config.setString("remote", "origin", "url", url)
                git.repository.config.save()
            }
            val hook = java.io.File(root, ".git/hooks/pre-commit")
            val marker = java.io.File(root, "hook-executed")
            hook.writeText("#!/bin/sh\ntouch '${marker.absolutePath}'\nexit 1\n")
            hook.setExecutable(true)
            java.io.File(root, "hello.txt").writeText("content")
            fun run(op: GitHubOperation, target: String = "") = executor.execute(root,
                GitHubCapabilityRequest(url, op, target, executor.head(root)), null) { false }
            assertTrue(run(GitHubOperation.STATUS).contains("hello.txt"))
            run(GitHubOperation.STAGE, "hello.txt")
            assertTrue(run(GitHubOperation.COMMIT, "fixture commit").startsWith("Committed"))
            assertFalse(marker.exists())
            run(GitHubOperation.CREATE_BRANCH, "feature")
            assertTrue(run(GitHubOperation.BRANCHES).contains("feature"))
            assertTrue(run(GitHubOperation.INSPECT_COMMIT).contains("fixture commit"))
        } finally { root.deleteRecursively() }
    }

    @Test fun maliciousConfigAndEscapingPathsAreRejected() {
        val root = Files.createTempDirectory("restricted-git-").toFile().canonicalFile
        try {
            val executor = RestrictedGitExecutor()
            Git.init().setDirectory(root).call().use { git ->
                git.repository.config.setString("remote", "origin", "url", url)
                git.repository.config.setString("credential", null, "helper", "!env")
                git.repository.config.save()
            }
            assertThrows(IllegalArgumentException::class.java) { executor.open(root) }
        } finally { root.deleteRecursively() }
    }
}
