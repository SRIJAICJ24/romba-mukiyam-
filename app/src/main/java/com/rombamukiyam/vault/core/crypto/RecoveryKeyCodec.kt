package com.rombamukiyam.vault.core.crypto

import java.security.MessageDigest

/**
 * Human-transcribable encoding of the 256-bit recovery key.
 *
 * Format:  RM-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX
 *  - 52 Crockford base32 characters carry the 256-bit key (last symbol has 4 padding bits = 0)
 *  - 4 Crockford characters carry a 20-bit checksum (first 20 bits of SHA-256(key)) so typos are caught
 *  - Crockford alphabet avoids I, L, O, U; decoding maps i/l→1, o→0 and is case-insensitive.
 */
object RecoveryKeyCodec {
    const val KEY_BYTES = 32
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val DATA_CHARS = 52
    private const val CHECK_CHARS = 4
    private const val GROUP = 4
    const val PREFIX = "RM"

    fun generate(): ByteArray = SecureRandomProvider.nextBytes(KEY_BYTES)

    fun encode(key: ByteArray): String {
        require(key.size == KEY_BYTES)
        val chars = StringBuilder(DATA_CHARS + CHECK_CHARS)
        chars.append(base32(key, DATA_CHARS))
        chars.append(base32(checksum(key), CHECK_CHARS))
        return buildString {
            append(PREFIX)
            chars.chunked(GROUP).forEach { append('-').append(it) }
        }
    }

    /** Groups only (no prefix) for two-line display. */
    fun displayLines(encoded: String): List<String> {
        val groups = encoded.removePrefix("$PREFIX-").split('-')
        val half = groups.size / 2
        return listOf(groups.take(half).joinToString("-"), groups.drop(half).joinToString("-"))
    }

    /** @return the key or null if the string is malformed or fails its checksum. */
    fun decode(input: String): ByteArray? {
        val cleaned = input.uppercase()
            .replace(Regex("[\\s\\-]"), "")
            .removePrefix(PREFIX)
            .map { normalise(it) }
            .joinToString("")
        if (cleaned.length != DATA_CHARS + CHECK_CHARS) return null
        if (cleaned.any { ALPHABET.indexOf(it) < 0 }) return null
        val key = unbase32(cleaned.substring(0, DATA_CHARS), KEY_BYTES) ?: return null
        val expectedCheck = base32(checksum(key), CHECK_CHARS)
        if (!expectedCheck.toByteArray().constantTimeEquals(cleaned.substring(DATA_CHARS).toByteArray())) return null
        return key
    }

    private fun normalise(c: Char): Char = when (c) {
        'O' -> '0'
        'I', 'L' -> '1'
        else -> c
    }

    private fun checksum(key: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(key).copyOf(3)

    private fun base32(bytes: ByteArray, outChars: Int): String {
        val sb = StringBuilder(outChars)
        var buffer = 0
        var bits = 0
        var i = 0
        while (sb.length < outChars) {
            if (bits < 5) {
                buffer = (buffer shl 8) or (if (i < bytes.size) bytes[i++].toInt() and 0xFF else 0)
                bits += 8
            }
            val idx = (buffer shr (bits - 5)) and 0x1F
            bits -= 5
            sb.append(ALPHABET[idx])
        }
        return sb.toString()
    }

    private fun unbase32(chars: String, outBytes: Int): ByteArray? {
        val out = ByteArray(outBytes)
        var buffer = 0
        var bits = 0
        var o = 0
        for (c in chars) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                if (o < outBytes) out[o++] = ((buffer shr (bits - 8)) and 0xFF).toByte()
                bits -= 8
            }
        }
        // Remaining padding bits must be zero for a canonical encoding.
        if (bits > 0 && (buffer and ((1 shl bits) - 1)) != 0) return null
        return if (o == outBytes) out else null
    }
}
