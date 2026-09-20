package com.rombamukiyam.vault.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.rombamukiyam.vault.core.common.SafeLog
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.core.common.VaultException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

enum class KeySecurityLevel { STRONGBOX, TEE, SOFTWARE, UNKNOWN }

/**
 * Android Keystore wrapper for the biometric slot key.
 *
 *  - AES-256-GCM, non-exportable, generated inside the Keystore.
 *  - Requires STRONG biometric authentication for every use (timeout 0).
 *  - Invalidated when biometric enrolment changes; callers get [VaultError.KeystoreInvalidated] and must
 *    re-create the slot after a master-passphrase unlock. Nothing else is ever deleted.
 *  - StrongBox is attempted first and silently falls back to the TEE/software Keystore.
 */
class KeystoreManager {

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    fun hasKey(alias: String): Boolean = keyStore.containsAlias(alias)

    /** Generates (or replaces) the biometric-bound key. Returns the security level actually achieved. */
    fun createBiometricKey(alias: String): KeySecurityLevel {
        deleteKey(alias)
        val tryStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        if (tryStrongBox) {
            try {
                generate(alias, strongBox = true)
                return securityLevel(alias)
            } catch (e: StrongBoxUnavailableException) {
                SafeLog.i(TAG, "StrongBox unavailable; falling back to TEE keystore")
            } catch (e: Exception) {
                SafeLog.w(TAG, "StrongBox key generation failed; falling back", e)
            }
        }
        generate(alias, strongBox = false)
        return securityLevel(alias)
    }

    private fun generate(alias: String, strongBox: Boolean) {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setIsStrongBoxBacked(true)
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(builder.build())
        generator.generateKey()
    }

    fun deleteKey(alias: String) {
        try {
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        } catch (e: Exception) {
            SafeLog.w(TAG, "deleteEntry failed", e)
        }
    }

    /** Cipher ready for encryption; must be authenticated through BiometricPrompt before doFinal. */
    fun encryptCipher(alias: String): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(alias))
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw VaultException(VaultError.KeystoreInvalidated)
        }
        return cipher
    }

    /** Cipher ready for decryption of a previously wrapped key; must be authenticated through BiometricPrompt. */
    fun decryptCipher(alias: String, nonce: ByteArray): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey(alias), GCMParameterSpec(128, nonce))
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw VaultException(VaultError.KeystoreInvalidated)
        }
        return cipher
    }

    fun securityLevel(alias: String): KeySecurityLevel = try {
        val key = secretKey(alias)
        val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
        val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeySecurityLevel.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeySecurityLevel.TEE
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeySecurityLevel.SOFTWARE
                else -> KeySecurityLevel.UNKNOWN
            }
        } else {
            @Suppress("DEPRECATION")
            if (info.isInsideSecureHardware) KeySecurityLevel.TEE else KeySecurityLevel.SOFTWARE
        }
    } catch (e: Exception) {
        KeySecurityLevel.UNKNOWN
    }

    private fun secretKey(alias: String): SecretKey =
        (keyStore.getKey(alias, null) as? SecretKey)
            ?: throw VaultException(VaultError.BiometricUnavailable(com.rombamukiyam.vault.core.common.BiometricUnavailableReason.NOT_ENABLED))

    companion object {
        private const val TAG = "Keystore"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val BIOMETRIC_ALIAS = "rombamukiyam.vaultkey.biometric.v1"
    }
}
