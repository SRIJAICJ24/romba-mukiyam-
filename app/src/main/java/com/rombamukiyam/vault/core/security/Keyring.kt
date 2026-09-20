package com.rombamukiyam.vault.core.security

import com.rombamukiyam.vault.core.crypto.KdfParams
import kotlinx.serialization.Serializable

/**
 * The keyring holds the Vault Key wrapped under one or more independent "slots".
 * It contains NO plaintext key material. Losing every slot's credential makes the vault unrecoverable.
 */
@Serializable
data class Keyring(
    val formatVersion: Int = FORMAT_VERSION,
    /** Random id for this vault; bound into every slot's AAD and into backups. */
    val vaultId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val slots: List<KeySlot>,
) {
    fun slot(type: KeySlotType): KeySlot? = slots.firstOrNull { it.type == type }

    fun withSlot(slot: KeySlot, now: Long): Keyring =
        copy(slots = slots.filterNot { it.type == slot.type } + slot, updatedAt = now)

    fun withoutSlot(type: KeySlotType, now: Long): Keyring =
        copy(slots = slots.filterNot { it.type == type }, updatedAt = now)

    companion object {
        const val FORMAT_VERSION = 1
    }
}

enum class KeySlotType { PASSPHRASE, RECOVERY, BIOMETRIC }

@Serializable
data class KeySlot(
    val type: KeySlotType,
    val createdAt: Long,
    /** Present for PASSPHRASE. RECOVERY uses HKDF; BIOMETRIC uses the Keystore. */
    val kdf: KdfParams? = null,
    /** HKDF salt (hex) for the RECOVERY slot. */
    val hkdfSaltHex: String? = null,
    /** Android Keystore alias for the BIOMETRIC slot. */
    val keystoreAlias: String? = null,
    /** GCM nonce (hex). */
    val nonceHex: String,
    /** Wrapped Vault Key: ciphertext || tag (hex). */
    val wrappedKeyHex: String,
) {
    /** Bytes authenticated (AAD) together with the wrapped key so slot metadata cannot be swapped. */
    fun associatedData(vaultId: String): ByteArray =
        "rombamukiyam/slot/v1|$vaultId|${type.name}|${kdf?.saltHex ?: ""}|${kdf?.memoryKib ?: 0}|${kdf?.iterations ?: 0}|${kdf?.parallelism ?: 0}|${hkdfSaltHex ?: ""}|${keystoreAlias ?: ""}"
            .toByteArray(Charsets.UTF_8)
}
