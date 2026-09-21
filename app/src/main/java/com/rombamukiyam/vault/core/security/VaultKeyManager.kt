package com.rombamukiyam.vault.core.security

import com.rombamukiyam.vault.core.common.BiometricUnavailableReason
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.common.SafeLog
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.core.common.VaultException
import com.rombamukiyam.vault.core.crypto.Aead
import com.rombamukiyam.vault.core.crypto.AeadCiphertext
import com.rombamukiyam.vault.core.crypto.AeadException
import com.rombamukiyam.vault.core.crypto.Hkdf
import com.rombamukiyam.vault.core.crypto.KdfParams
import com.rombamukiyam.vault.core.crypto.KeyPurpose
import com.rombamukiyam.vault.core.crypto.PassphraseKdf
import com.rombamukiyam.vault.core.crypto.RecoveryKeyCodec
import com.rombamukiyam.vault.core.crypto.SecureRandomProvider
import com.rombamukiyam.vault.core.crypto.hexToBytes
import com.rombamukiyam.vault.core.crypto.toHex
import com.rombamukiyam.vault.core.crypto.wipe
import java.util.UUID
import javax.crypto.Cipher

class NewVaultResult(val vaultKey: ByteArray, val recoveryKeyEncoded: String)

/**
 * Owns the key hierarchy:
 *
 *   Vault Key (random 256-bit) is wrapped with AES-256-GCM under independent slots:
 *     PASSPHRASE : Argon2id(passphrase)            -> wrap key
 *     RECOVERY   : HKDF(recovery key, salt)        -> wrap key
 *     BIOMETRIC  : Android Keystore key (auth-bound) wraps directly
 *
 * All heavy work (Argon2, AES) must be called from a background dispatcher.
 * This class never logs or persists any key material.
 */
class VaultKeyManager(
    private val store: KeyringStore,
    private val kdf: PassphraseKdf,
    private val keystore: KeystoreManager,
    private val clock: Clock,
) {

    fun vaultExists(): Boolean = store.exists() && store.read()?.slot(KeySlotType.PASSPHRASE) != null

    fun keyring(): Keyring = store.read() ?: throw VaultException(VaultError.DatabaseUnavailable())

    fun vaultId(): String = keyring().vaultId

    // ---------------------------------------------------------------- creation

    /** Creates a brand-new vault: fresh Vault Key, passphrase slot, recovery slot. */
    fun createVault(passphrase: ByteArray, kdfParams: KdfParams = kdf.calibrate()): NewVaultResult {
        check(!vaultExists()) { "vault already exists" }
        val now = clock.nowMillis()
        val vaultId = UUID.randomUUID().toString()
        val vaultKey = Aead.newKey()
        val recoveryKey = RecoveryKeyCodec.generate()
        try {
            val passSlot = buildPassphraseSlot(vaultId, vaultKey, passphrase, KdfParams.withFreshSalt(kdfParams), now)
            val recSlot = buildRecoverySlot(vaultId, vaultKey, recoveryKey, now)
            store.write(Keyring(vaultId = vaultId, createdAt = now, updatedAt = now, slots = listOf(passSlot, recSlot)))
            return NewVaultResult(vaultKey, RecoveryKeyCodec.encode(recoveryKey))
        } finally {
            recoveryKey.wipe()
        }
    }

    /** Rollback for [createVault] when the database could not be created. Refuses if a database exists. */
    fun rollbackFailedCreation(databaseExists: Boolean) {
        if (databaseExists) return
        store.deleteAllForFailedCreation()
    }

    // ---------------------------------------------------------------- unlock

    /** @return the Vault Key, or null when the passphrase is wrong. */
    fun unlockWithPassphrase(passphrase: ByteArray): ByteArray? {
        val ring = keyring()
        val slot = ring.slot(KeySlotType.PASSPHRASE) ?: throw VaultException(VaultError.DatabaseUnavailable())
        val params = slot.kdf ?: throw VaultException(VaultError.DatabaseUnavailable())
        val wrapKey = kdf.derive(passphrase, params)
        return try {
            unwrap(ring.vaultId, slot, wrapKey)
        } finally {
            wrapKey.wipe()
        }
    }

    /** @return the Vault Key, or null when the recovery key is wrong. */
    fun unlockWithRecoveryKey(recoveryKey: ByteArray): ByteArray? {
        val ring = keyring()
        val slot = ring.slot(KeySlotType.RECOVERY) ?: return null
        val wrapKey = recoveryWrapKey(recoveryKey, slot.hkdfSaltHex!!.hexToBytes())
        return try {
            unwrap(ring.vaultId, slot, wrapKey)
        } finally {
            wrapKey.wipe()
        }
    }

    /** True when [passphrase] unwraps the Vault Key. The key material is wiped immediately. */
    fun verifyPassphrase(passphrase: ByteArray): Boolean = unlockWithPassphrase(passphrase)?.also { it.wipe() } != null

    /** KDF parameters currently used for the passphrase slot (already calibrated for this device). */
    fun currentKdfParams(): KdfParams = keyring().slot(KeySlotType.PASSPHRASE)?.kdf ?: KdfParams.freshDefault()

    // ---------------------------------------------------------------- passphrase & recovery management

    /** Re-wraps the same Vault Key under a new passphrase with a fresh salt. Atomic keyring replacement. */
    fun changePassphrase(vaultKey: ByteArray, newPassphrase: ByteArray, kdfParams: KdfParams? = null) {
        val ring = keyring()
        val now = clock.nowMillis()
        val base = kdfParams ?: ring.slot(KeySlotType.PASSPHRASE)?.kdf ?: KdfParams.freshDefault()
        val slot = buildPassphraseSlot(ring.vaultId, vaultKey, newPassphrase, KdfParams.withFreshSalt(base), now)
        // Verify before commit.
        val check = kdf.derive(newPassphrase, slot.kdf!!)
        try {
            requireNotNull(unwrap(ring.vaultId, slot, check)) { "self-check failed" }.wipe()
        } finally {
            check.wipe()
        }
        store.write(ring.withSlot(slot, now))
    }

    /** Replaces the recovery slot; returns the new encoded recovery key (shown once). */
    fun regenerateRecoveryKey(vaultKey: ByteArray): String {
        val ring = keyring()
        val now = clock.nowMillis()
        val recoveryKey = RecoveryKeyCodec.generate()
        try {
            store.write(ring.withSlot(buildRecoverySlot(ring.vaultId, vaultKey, recoveryKey, now), now))
            return RecoveryKeyCodec.encode(recoveryKey)
        } finally {
            recoveryKey.wipe()
        }
    }

    // ---------------------------------------------------------------- biometric slot

    fun biometricEnabled(): Boolean {
        val slot = store.read()?.slot(KeySlotType.BIOMETRIC) ?: return false
        return slot.keystoreAlias?.let { keystore.hasKey(it) } == true
    }

    /** Step 1 of enrolment: create the Keystore key and return a cipher that must be biometric-authenticated. */
    fun prepareBiometricEnrollment(): Cipher {
        keystore.createBiometricKey(KeystoreManager.BIOMETRIC_ALIAS)
        return keystore.encryptCipher(KeystoreManager.BIOMETRIC_ALIAS)
    }

    /** Step 2 of enrolment: wrap the Vault Key with the authenticated cipher and persist the slot. */
    fun completeBiometricEnrollment(authenticatedCipher: Cipher, vaultKey: ByteArray) {
        val ring = keyring()
        val now = clock.nowMillis()
        val template = KeySlot(
            type = KeySlotType.BIOMETRIC, createdAt = now, keystoreAlias = KeystoreManager.BIOMETRIC_ALIAS,
            nonceHex = "", wrappedKeyHex = "",
        )
        val ct = Aead.encryptWithCipher(authenticatedCipher, vaultKey, template.associatedData(ring.vaultId))
        val slot = template.copy(nonceHex = ct.nonce.toHex(), wrappedKeyHex = ct.ciphertextWithTag.toHex())
        store.write(ring.withSlot(slot, now))
    }

    /** Step 1 of unlock: cipher for the stored nonce; throws KeystoreInvalidated if enrolment changed. */
    fun prepareBiometricUnlock(): Cipher {
        val slot = keyring().slot(KeySlotType.BIOMETRIC)
            ?: throw VaultException(VaultError.BiometricUnavailable(BiometricUnavailableReason.NOT_ENABLED))
        val alias = slot.keystoreAlias ?: KeystoreManager.BIOMETRIC_ALIAS
        if (!keystore.hasKey(alias)) throw VaultException(VaultError.KeystoreInvalidated)
        return keystore.decryptCipher(alias, slot.nonceHex.hexToBytes())
    }

    /** Step 2 of unlock. */
    fun completeBiometricUnlock(authenticatedCipher: Cipher): ByteArray {
        val ring = keyring()
        val slot = ring.slot(KeySlotType.BIOMETRIC) ?: throw VaultException(VaultError.KeystoreInvalidated)
        return try {
            Aead.decryptWithCipher(
                authenticatedCipher,
                AeadCiphertext(slot.nonceHex.hexToBytes(), slot.wrappedKeyHex.hexToBytes()),
                slot.associatedData(ring.vaultId),
            )
        } catch (e: AeadException) {
            throw VaultException(VaultError.KeystoreInvalidated)
        }
    }

    /** Removes only the biometric slot and its Keystore key. The vault itself is untouched. */
    fun disableBiometric() {
        val ring = store.read() ?: return
        keystore.deleteKey(KeystoreManager.BIOMETRIC_ALIAS)
        if (ring.slot(KeySlotType.BIOMETRIC) != null) store.write(ring.withoutSlot(KeySlotType.BIOMETRIC, clock.nowMillis()))
        SafeLog.i(TAG, "biometric slot removed")
    }

    // ---------------------------------------------------------------- helpers

    private fun buildPassphraseSlot(vaultId: String, vaultKey: ByteArray, passphrase: ByteArray, params: KdfParams, now: Long): KeySlot {
        val template = KeySlot(type = KeySlotType.PASSPHRASE, createdAt = now, kdf = params, nonceHex = "", wrappedKeyHex = "")
        val wrapKey = kdf.derive(passphrase, params)
        try {
            return wrap(vaultId, template, wrapKey, vaultKey)
        } finally {
            wrapKey.wipe()
        }
    }

    private fun buildRecoverySlot(vaultId: String, vaultKey: ByteArray, recoveryKey: ByteArray, now: Long): KeySlot {
        val salt = SecureRandomProvider.nextBytes(16)
        val template = KeySlot(type = KeySlotType.RECOVERY, createdAt = now, hkdfSaltHex = salt.toHex(), nonceHex = "", wrappedKeyHex = "")
        val wrapKey = recoveryWrapKey(recoveryKey, salt)
        try {
            return wrap(vaultId, template, wrapKey, vaultKey)
        } finally {
            wrapKey.wipe()
        }
    }

    private fun recoveryWrapKey(recoveryKey: ByteArray, salt: ByteArray): ByteArray =
        Hkdf.derive(recoveryKey, salt, KeyPurpose.RECOVERY_WRAP.toByteArray(), 32)

    private fun wrap(vaultId: String, template: KeySlot, wrapKey: ByteArray, vaultKey: ByteArray): KeySlot {
        val ct = Aead.encrypt(wrapKey, vaultKey, template.associatedData(vaultId))
        return template.copy(nonceHex = ct.nonce.toHex(), wrappedKeyHex = ct.ciphertextWithTag.toHex())
    }

    private fun unwrap(vaultId: String, slot: KeySlot, wrapKey: ByteArray): ByteArray? = try {
        Aead.decrypt(wrapKey, AeadCiphertext(slot.nonceHex.hexToBytes(), slot.wrappedKeyHex.hexToBytes()), slot.associatedData(vaultId))
    } catch (e: AeadException) {
        null
    }

    companion object {
        private const val TAG = "VaultKeyManager"
    }
}
