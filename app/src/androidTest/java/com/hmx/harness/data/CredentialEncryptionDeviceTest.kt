package com.hmx.harness.data

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

/** Exercises the actual Android cryptographic storage used by KeyStoreManager. */
@RunWith(AndroidJUnit4::class)
class CredentialEncryptionDeviceTest {
    @Test fun keystoreBackedValuesRoundTripAndRepeatedWritesProduceDifferentCiphertext() {
        withStore { name, alias ->
            val prefs = encrypted(name, alias)
            assertNull(prefs.getString("missing", null))
            assertTrue(prefs.edit().putString("test", "synthetic credential").commit())
            assertEquals("synthetic credential", prefs.getString("test", null))
            val raw = context.getSharedPreferences(name, 0)
            val first = raw.all.toMap()
            assertTrue(prefs.edit().putString("test", "synthetic credential").commit())
            assertNotEquals(first, raw.all.toMap())
            assertFalse(raw.all.toString().contains("synthetic credential"))
            assertTrue(prefs.edit().putString("test", "replacement").commit())
            assertEquals("replacement", encrypted(name, alias).getString("test", null))
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertTrue(ks.containsAlias(alias))
            assertNull(ks.getKey(alias, null).encoded)
        }
    }

    @Test fun corruptedCiphertextIsRejectedWithoutDeletingTheRecord() {
        withStore { name, alias ->
            val prefs = encrypted(name, alias)
            assertTrue(prefs.edit().putString("test", "synthetic credential").commit())
            val raw = context.getSharedPreferences(name, 0)
            val key = raw.all.keys.single { !it.startsWith("__androidx_security_crypto") }
            val bytes = android.util.Base64.decode(raw.getString(key, null), android.util.Base64.DEFAULT)
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            assertTrue(raw.edit().putString(key, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)).commit())
            assertThrows(Exception::class.java) { prefs.getString("test", null) }
            assertTrue(raw.contains(key))
            assertTrue(raw.edit().putString(key, "invalid ciphertext").commit())
            assertThrows(Exception::class.java) { prefs.getString("test", null) }
            assertTrue(raw.contains(key))
        }
    }

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun encrypted(name: String, alias: String) = EncryptedSharedPreferences.create(
        context, name, MasterKey.Builder(context, alias).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun withStore(block: (String, String) -> Unit) {
        val name = "phase1-test-${UUID.randomUUID()}"
        val alias = "$name-key"
        try { block(name, alias) } finally {
            context.deleteSharedPreferences(name)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
}
