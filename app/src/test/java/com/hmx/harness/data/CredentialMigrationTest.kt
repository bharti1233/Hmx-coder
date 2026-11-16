package com.hmx.harness.data

import org.junit.Assert.*
import org.junit.Test

class CredentialMigrationTest {
    @Test fun persistsAndVerifiesBeforeRemovingLegacy() {
        var secure: String? = null
        var legacy: String? = "secret"
        val result = CredentialMigration.load({ secure }, { legacy }, {}, { secure = it }, {
            assertEquals("secret", secure)
            legacy = null
        })
        assertEquals("secret", result)
        assertNull(legacy)
    }

    @Test fun failedWritePreservesLegacyAndCanRetry() {
        var secure: String? = null
        var removed = false
        assertThrows(IllegalStateException::class.java) {
            CredentialMigration.load({ secure }, { "secret" }, {}, { error("unavailable") }, { removed = true })
        }
        assertFalse(removed)
        assertEquals("secret", CredentialMigration.load({ secure }, { "secret" }, {}, { secure = it }, { removed = true }))
        assertTrue(removed)
    }

    @Test fun readBackMismatchDoesNotDeleteOriginal() {
        var reads = 0
        var removed = false
        assertThrows(IllegalStateException::class.java) {
            CredentialMigration.load({ if (reads++ == 0) null else "wrong" }, { "secret" }, {}, {}, { removed = true })
        }
        assertFalse(removed)
    }

    @Test fun interruptedCleanupRetriesWithoutOverwritingSecureRecord() {
        var writes = 0
        var removed = false
        assertEquals("new secret", CredentialMigration.load({ "new secret" }, { "old secret" }, {}, { writes++ }, { removed = true }))
        assertEquals(0, writes)
        assertTrue(removed)
    }

    @Test fun unavailableKeyDoesNotFallBackOrDelete() {
        var accessedLegacy = false
        assertThrows(IllegalStateException::class.java) {
            CredentialMigration.load({ error("key unavailable") }, { accessedLegacy = true; "secret" }, {}, {}, { fail("deleted") })
        }
        assertFalse(accessedLegacy)
    }

    @Test fun missingCredentialDoesNotWriteOrDelete() {
        assertNull(CredentialMigration.load({ null }, { null }, {}, { fail("wrote") }, { fail("deleted") }))
    }

    @Test fun invalidLegacyIsPreserved() {
        assertThrows(IllegalArgumentException::class.java) {
            CredentialMigration.load({ null }, { "invalid" }, { throw IllegalArgumentException() }, { fail("wrote") }, { fail("deleted") })
        }
    }
}
