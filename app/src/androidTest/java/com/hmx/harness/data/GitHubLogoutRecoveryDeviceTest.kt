package com.hmx.harness.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Isolated preference names: never alters a user's live credentials or generation selector. */
@RunWith(AndroidJUnit4::class)
class GitHubLogoutRecoveryDeviceTest {
    @Test fun logoutSurvivesManagerReloadAndBlocksLegacyRecovery() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "phase12-test-${UUID.randomUUID()}-"
        val touched = mutableSetOf<String>()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                touched += prefix + name
                return base.getSharedPreferences(prefix + name, mode)
            }
        }
        try {
            val store = KeyStoreManager(context)
            store.migrateGitHubToken("synthetic-token")
            assertEquals("synthetic-token", KeyStoreManager(context).githubToken())
            store.removeGitHubToken()
            val restarted = KeyStoreManager(context)
            assertNull(restarted.githubCredential())
            assertThrows(IllegalStateException::class.java) { restarted.migrateGitHubToken("synthetic-token") }
            assertNull(restarted.githubToken())
            restarted.putGitHubToken("synthetic-replacement")
            assertEquals("synthetic-replacement", KeyStoreManager(context).githubToken())
        } finally { touched.forEach { base.deleteSharedPreferences(it) } }
    }
}
