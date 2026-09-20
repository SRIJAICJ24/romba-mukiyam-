package com.rombamukiyam.vault.core.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256 exactly as specified in RFC 5869, built from the platform HMAC.
 * Used to derive independent sub-keys (database key, field key, history HMAC key) from the Vault Key.
 */
object Hkdf {
    private const val HMAC = "HmacSHA256"
    private const val HASH_LEN = 32

    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val realSalt = if (salt == null || salt.isEmpty()) ByteArray(HASH_LEN) else salt
        return hmac(realSalt, ikm)
    }

    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * HASH_LEN)) { "invalid HKDF output length" }
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
            val toCopy = minOf(previous.size, length - offset)
            System.arraycopy(previous, 0, out, offset, toCopy)
            offset += toCopy
            counter++
        }
        return out
    }

    fun derive(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int = 32): ByteArray =
        expand(extract(salt, ikm), info, length)

    fun derive(ikm: ByteArray, info: String, length: Int = 32): ByteArray =
        derive(ikm, null, info.toByteArray(Charsets.UTF_8), length)

    fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(key, HMAC))
        return mac.doFinal(data)
    }
}

/** Fixed HKDF "info" labels. Changing any of these is a breaking change to every existing vault. */
object KeyPurpose {
    const val DATABASE = "rombamukiyam/db-key/v1"
    const val FIELD = "rombamukiyam/field-key/v1"
    const val HISTORY_HMAC = "rombamukiyam/history-hmac/v1"
    const val RECOVERY_WRAP = "rombamukiyam/recovery-wrap/v1"
}
