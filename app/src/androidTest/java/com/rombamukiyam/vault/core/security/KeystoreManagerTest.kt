package com.rombamukiyam.vault.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreManagerTest {

    private val alias = "rombamukiyam.test.biometric"
    private val keystore = KeystoreManager()

    @After
    fun tearDown() = keystore.deleteKey(alias)

    @Test
    fun biometricKeyIsCreatedInsideKeystore() {
        val level = try {
            keystore.createBiometricKey(alias)
        } catch (e: Exception) {
            // Devices/emulators with no biometric enrolled may refuse auth-bound key generation.
            assumeTrue("auth-bound key generation unsupported here: ${e.javaClass.simpleName}", false)
            return
        }
        assertThat(keystore.hasKey(alias)).isTrue()
        assertThat(level).isNotEqualTo(KeySecurityLevel.UNKNOWN)
    }

    @Test
    fun unauthenticatedUseOfBiometricKeyIsRefused() {
        try {
            keystore.createBiometricKey(alias)
        } catch (e: Exception) {
            assumeTrue("auth-bound key generation unsupported here", false)
            return
        }
        // Without a BiometricPrompt authentication the key must NOT be usable.
        val threw = try {
            val cipher = keystore.encryptCipher(alias)
            cipher.doFinal(ByteArray(32))
            false
        } catch (e: Exception) {
            true
        }
        assertThat(threw).isTrue()
    }

    @Test
    fun deleteKeyRemovesAlias() {
        try {
            keystore.createBiometricKey(alias)
        } catch (e: Exception) {
            assumeTrue("auth-bound key generation unsupported here", false)
            return
        }
        keystore.deleteKey(alias)
        assertThat(keystore.hasKey(alias)).isFalse()
    }
}
