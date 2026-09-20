package com.rombamukiyam.vault.core.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** RFC 5869 Appendix A test vectors for HKDF-SHA256. */
class HkdfTest {

    @Test
    fun `rfc5869 test case 1`() {
        val ikm = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b".hexToBytes()
        val salt = "000102030405060708090a0b0c".hexToBytes()
        val info = "f0f1f2f3f4f5f6f7f8f9".hexToBytes()
        val prk = Hkdf.extract(salt, ikm)
        assertThat(prk.toHex()).isEqualTo("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5")
        val okm = Hkdf.expand(prk, info, 42)
        assertThat(okm.toHex()).isEqualTo(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
        )
    }

    @Test
    fun `rfc5869 test case 2 long inputs`() {
        val ikm = (0x00..0x4f).map { it.toByte() }.toByteArray()
        val salt = (0x60..0xaf).map { it.toByte() }.toByteArray()
        val info = (0xb0..0xff).map { it.toByte() }.toByteArray()
        val okm = Hkdf.derive(ikm, salt, info, 82)
        assertThat(okm.toHex()).isEqualTo(
            "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c" +
                "59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71" +
                "cc30c58179ec3e87c14c01d5c1f3434f1d87",
        )
    }

    @Test
    fun `rfc5869 test case 3 empty salt and info`() {
        val ikm = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b".hexToBytes()
        val okm = Hkdf.derive(ikm, null, ByteArray(0), 42)
        assertThat(okm.toHex()).isEqualTo(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
        )
    }

    @Test
    fun `purpose labels derive independent keys`() {
        val vk = Aead.newKey()
        val db = Hkdf.derive(vk, KeyPurpose.DATABASE)
        val field = Hkdf.derive(vk, KeyPurpose.FIELD)
        val hist = Hkdf.derive(vk, KeyPurpose.HISTORY_HMAC)
        assertThat(db).isNotEqualTo(field)
        assertThat(db).isNotEqualTo(hist)
        assertThat(field).isNotEqualTo(hist)
        assertThat(db).isNotEqualTo(vk)
        // Deterministic
        assertThat(Hkdf.derive(vk, KeyPurpose.DATABASE)).isEqualTo(db)
    }
}
