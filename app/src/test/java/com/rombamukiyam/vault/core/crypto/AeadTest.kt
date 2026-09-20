package com.rombamukiyam.vault.core.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class AeadTest {

    private val key = Aead.newKey()
    private val plaintext = "Dad SBI login password: not-a-real-secret".toByteArray()
    private val aad = "context".toByteArray()

    @Test
    fun `round trip with associated data`() {
        val ct = Aead.encrypt(key, plaintext, aad)
        assertThat(Aead.decrypt(key, ct, aad)).isEqualTo(plaintext)
    }

    @Test
    fun `round trip without associated data`() {
        val ct = Aead.encrypt(key, plaintext)
        assertThat(Aead.decrypt(key, ct)).isEqualTo(plaintext)
    }

    @Test
    fun `wrong key is rejected`() {
        val ct = Aead.encrypt(key, plaintext, aad)
        assertThrows(AeadException::class.java) { Aead.decrypt(Aead.newKey(), ct, aad) }
    }

    @Test
    fun `tampered ciphertext byte is rejected`() {
        val ct = Aead.encrypt(key, plaintext, aad)
        val tampered = ct.ciphertextWithTag.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertThrows(AeadException::class.java) { Aead.decrypt(key, AeadCiphertext(ct.nonce, tampered), aad) }
    }

    @Test
    fun `tampered tag is rejected`() {
        val ct = Aead.encrypt(key, plaintext, aad)
        val tampered = ct.ciphertextWithTag.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0x80).toByte() }
        assertThrows(AeadException::class.java) { Aead.decrypt(key, AeadCiphertext(ct.nonce, tampered), aad) }
    }

    @Test
    fun `tampered nonce is rejected`() {
        val ct = Aead.encrypt(key, plaintext, aad)
        val nonce = ct.nonce.copyOf().also { it[3] = (it[3].toInt() xor 0x10).toByte() }
        assertThrows(AeadException::class.java) { Aead.decrypt(key, AeadCiphertext(nonce, ct.ciphertextWithTag), aad) }
    }

    @Test
    fun `different associated data is rejected`() {
        val ct = Aead.encrypt(key, plaintext, aad)
        assertThrows(AeadException::class.java) { Aead.decrypt(key, ct, "other".toByteArray()) }
    }

    @Test
    fun `nonces are unique across many encryptions`() {
        val seen = HashSet<String>()
        repeat(5_000) {
            val ct = Aead.encrypt(key, plaintext)
            assertThat(ct.nonce).hasLength(Aead.NONCE_BYTES)
            assertThat(seen.add(ct.nonce.toHex())).isTrue()
        }
    }

    @Test
    fun `same plaintext produces different ciphertext`() {
        val a = Aead.encrypt(key, plaintext)
        val b = Aead.encrypt(key, plaintext)
        assertThat(a.ciphertextWithTag).isNotEqualTo(b.ciphertextWithTag)
    }

    @Test
    fun `wire format round trips`() {
        val ct = Aead.encrypt(key, plaintext, aad)
        val restored = AeadCiphertext.fromBytes(ct.toBytes())
        assertThat(Aead.decrypt(key, restored, aad)).isEqualTo(plaintext)
    }

    @Test
    fun `short keys are refused`() {
        assertThrows(IllegalArgumentException::class.java) { Aead.encrypt(ByteArray(16), plaintext) }
    }

    @Test
    fun `truncated wire bytes are refused`() {
        assertThrows(IllegalArgumentException::class.java) { AeadCiphertext.fromBytes(ByteArray(20)) }
    }
}
