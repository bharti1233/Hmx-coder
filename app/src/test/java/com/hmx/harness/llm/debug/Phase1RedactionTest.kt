package com.hmx.harness.llm.debug

import org.junit.Assert.*
import org.junit.Test

class Phase1RedactionTest {
    @Test fun headerCaptureDoesNotRetainCredentialPrefixes() {
        val secret = "synthetic-credential-123456"
        for (name in listOf("Authorization", "Proxy-Authorization", "x-api-key", "Cookie", "refresh_token")) {
            val value = DebugRedact.header(name, secret)
            assertFalse(value.contains(secret))
            assertFalse(value.contains(secret.take(4)))
        }
    }

    @Test fun shortKeysEchoedInErrorsAreRedacted() {
        val secrets = DebugRedact.secretsOf(listOf("x-api-key" to "short"))
        assertEquals("provider rejected [redacted]", DebugRedact.body("provider rejected short", secrets))
    }
}
