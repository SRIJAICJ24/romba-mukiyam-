package com.rombamukiyam.vault.core.crypto

import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Authenticated ciphertext. [nonce] is always freshly random per encryption. */
class AeadCiphertext(val nonce: ByteArray, val ciphertextWithTag: ByteArray) {
    /** Compact wire form: nonce || ciphertext || tag. */
    fun toBytes(): ByteArray = nonce + ciphertextWithTag

    companion object {
        fun fromBytes(bytes: ByteArray): AeadCiphertext {
            require(bytes.size > Aead.NONCE_BYTES + Aead.TAG_BYTES) { "ciphertext too short" }
            return AeadCiphertext(
                nonce = bytes.copyOfRange(0, Aead.NONCE_BYTES),
                ciphertextWithTag = bytes.copyOfRange(Aead.NONCE_BYTES, bytes.size),
            )
        }
    }
}

class AeadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * AES-256-GCM via the platform provider. No custom crypto.
 *
 *  - 12-byte random nonce per call (never reused with the same key by construction).
 *  - 128-bit authentication tag.
 *  - Optional associated data binds the ciphertext to its context (e.g. a key-slot header).
 */
object Aead {
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    const val TAG_BITS = 128
    const val TAG_BYTES = TAG_BITS / 8
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun newKey(): ByteArray = SecureRandomProvider.nextBytes(KEY_BYTES)

    fun encrypt(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray? = null): AeadCiphertext {
        requireKey(key)
        val nonce = SecureRandomProvider.nextBytes(NONCE_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key.asSecretKey(), GCMParameterSpec(TAG_BITS, nonce))
        associatedData?.let { cipher.updateAAD(it) }
        return AeadCiphertext(nonce, cipher.doFinal(plaintext))
    }

    /** @throws AeadException if the key is wrong or the data was tampered with. */
    fun decrypt(key: ByteArray, ciphertext: AeadCiphertext, associatedData: ByteArray? = null): ByteArray {
        requireKey(key)
        require(ciphertext.nonce.size == NONCE_BYTES) { "bad nonce length" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key.asSecretKey(), GCMParameterSpec(TAG_BITS, ciphertext.nonce))
        associatedData?.let { cipher.updateAAD(it) }
        return try {
            cipher.doFinal(ciphertext.ciphertextWithTag)
        } catch (e: AEADBadTagException) {
            throw AeadException("authentication failed", e)
        }
    }

    /** Encrypt using an already-initialised cipher (used for Keystore-bound keys with BiometricPrompt). */
    fun encryptWithCipher(cipher: Cipher, plaintext: ByteArray, associatedData: ByteArray? = null): AeadCiphertext {
        associatedData?.let { cipher.updateAAD(it) }
        return AeadCiphertext(cipher.iv.copyOf(), cipher.doFinal(plaintext))
    }

    fun decryptWithCipher(cipher: Cipher, ciphertext: AeadCiphertext, associatedData: ByteArray? = null): ByteArray {
        associatedData?.let { cipher.updateAAD(it) }
        return try {
            cipher.doFinal(ciphertext.ciphertextWithTag)
        } catch (e: AEADBadTagException) {
            throw AeadException("authentication failed", e)
        }
    }

    private fun requireKey(key: ByteArray) = require(key.size == KEY_BYTES) { "AES-256 key must be 32 bytes" }

    private fun ByteArray.asSecretKey(): SecretKey = SecretKeySpec(this, "AES")
}
