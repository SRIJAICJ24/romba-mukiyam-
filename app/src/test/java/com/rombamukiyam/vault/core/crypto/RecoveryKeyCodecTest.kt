package com.rombamukiyam.vault.core.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecoveryKeyCodecTest {

    @Test
    fun `encode decode round trip for many random keys`() {
        repeat(500) {
            val key = RecoveryKeyCodec.generate()
            val text = RecoveryKeyCodec.encode(key)
            assertThat(text).startsWith("RM-")
            assertThat(text.removePrefix("RM-").replace("-", "")).hasLength(56)
            assertThat(RecoveryKeyCodec.decode(text)).isEqualTo(key)
        }
    }

    @Test
    fun `decode tolerates lowercase spaces and confusable letters`() {
        val key = RecoveryKeyCodec.generate()
        val text = RecoveryKeyCodec.encode(key)
        val messy = text.lowercase().replace("-", " ").replace('0', 'o').replace('1', 'l')
        assertThat(RecoveryKeyCodec.decode(messy)).isEqualTo(key)
    }

    @Test
    fun `single character typo is rejected by checksum`() {
        val key = RecoveryKeyCodec.generate()
        val text = RecoveryKeyCodec.encode(key)
        // Flip one data character (index 5 is inside the first group after "RM-").
        val idx = 5
        val original = text[idx]
        val replacement = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".first { it != original }
        val typo = text.substring(0, idx) + replacement + text.substring(idx + 1)
        assertThat(RecoveryKeyCodec.decode(typo)).isNull()
    }

    @Test
    fun `wrong length and invalid characters are rejected`() {
        assertThat(RecoveryKeyCodec.decode("RM-ABCD")).isNull()
        assertThat(RecoveryKeyCodec.decode("")).isNull()
        val key = RecoveryKeyCodec.generate()
        val text = RecoveryKeyCodec.encode(key)
        assertThat(RecoveryKeyCodec.decode(text.replace(text[4], 'U'))).isNull()
    }

    @Test
    fun `display lines split into two halves`() {
        val text = RecoveryKeyCodec.encode(RecoveryKeyCodec.generate())
        val lines = RecoveryKeyCodec.displayLines(text)
        assertThat(lines).hasSize(2)
        assertThat(lines[0].split('-')).hasSize(7)
        assertThat(lines[1].split('-')).hasSize(7)
    }
}
