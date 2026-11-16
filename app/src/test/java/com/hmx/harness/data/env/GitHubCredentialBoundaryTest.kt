package com.hmx.harness.data.env

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class GitHubCredentialBoundaryTest {
    @Test fun generalEnvironmentDoesNotInjectGithubCredentials() {
        val source = File("src/main/java/com/hmx/harness/data/env/LinuxEnvironment.kt").readText()
        assertFalse(source.contains("GitHubProvision.processEnvironment("))
    }

    @Test fun privilegedLegacyFormatsAreParsedWithoutShellEvaluation() {
        val secret = "synthetic-token"
        assertEquals(secret, GitHubProvision.legacyToken(null,
            "github.com:\n    oauth_token: $secret\n", null))
        assertEquals(secret, GitHubProvision.legacyToken(null, null,
            "[url \"https://x-access-token:$secret@github.com/\"]"))
        assertNull(GitHubProvision.legacyToken(null, null, "[safe]\n directory = *"))
        assertThrows(IllegalStateException::class.java) {
            GitHubProvision.legacyToken("one", "oauth_token: two", null)
        }
    }
}
