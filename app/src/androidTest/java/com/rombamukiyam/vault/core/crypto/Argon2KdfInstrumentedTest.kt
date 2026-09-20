package com.rombamukiyam.vault.core.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Argon2KdfInstrumentedTest {

    private val kdf = Argon2Kdf()

    @Test
    fun deriveIsDeterministicForSameInputs() {
        val params = KdfParams.freshDefault()
        val a = kdf.derive("family passphrase".toByteArray(), params)
        val b = kdf.derive("family passphrase".toByteArray(), params)
        assertThat(a).hasLength(32)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun differentSaltOrPassphraseChangesOutput() {
        val params = KdfParams.freshDefault()
        val a = kdf.derive("family passphrase".toByteArray(), params)
        val b = kdf.derive("family passphrase".toByteArray(), KdfParams.withFreshSalt(params))
        val c = kdf.derive("family passphrasE".toByteArray(), params)
        assertThat(a).isNotEqualTo(b)
        assertThat(a).isNotEqualTo(c)
    }

    @Test
    fun outputIsStableAcrossIndependentInstances() {
        // JNI state must not leak between calls or instances. (RFC 9106 vectors use the secret/AD inputs that
        // argon2kt does not expose and a memory cost below our floor, so they cannot be replayed here.)
        val params = KdfParams(saltHex = "somesalt".toByteArray().toHex(), memoryKib = 65536, iterations = 3, parallelism = 1)
        val a = kdf.derive("password".toByteArray(), params)
        val b = Argon2Kdf().derive("password".toByteArray(), params)
        assertThat(a.toHex()).isEqualTo(b.toHex())
    }

    @Test
    fun parametersBelowFloorAreRefused() {
        val weak = KdfParams.freshDefault().copy(memoryKib = 1024)
        assertThrows(IllegalArgumentException::class.java) { kdf.derive("x".toByteArray(), weak) }
        val weakIter = KdfParams.freshDefault().copy(iterations = 1)
        assertThrows(IllegalArgumentException::class.java) { kdf.derive("x".toByteArray(), weakIter) }
    }

    @Test
    fun calibrationNeverGoesBelowFloor() {
        val params = kdf.calibrate(targetMillis = 200)
        assertThat(params.memoryKib).isAtLeast(KdfParams.MIN_MEMORY_KIB)
        assertThat(params.iterations).isAtLeast(KdfParams.MIN_ITERATIONS)
        assertThat(params.parallelism).isAtLeast(KdfParams.MIN_PARALLELISM)
    }
}
