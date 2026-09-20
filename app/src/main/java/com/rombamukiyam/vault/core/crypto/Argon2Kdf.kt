package com.rombamukiyam.vault.core.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.lambdapioneer.argon2kt.Argon2Version
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer

/**
 * Parameters stored alongside every passphrase-derived key. All values are persisted so that a vault
 * created on one device can be unlocked on another regardless of that device's benchmark.
 */
@Serializable
data class KdfParams(
    val algorithm: String = ALGORITHM_ARGON2ID,
    val version: Int = 0x13,
    val saltHex: String,
    val memoryKib: Int,
    val iterations: Int,
    val parallelism: Int,
    val outputBytes: Int = 32,
) {
    val salt: ByteArray get() = saltHex.hexToBytes()

    companion object {
        const val ALGORITHM_ARGON2ID = "argon2id"
        const val SALT_BYTES = 16

        /** Floor. Never go below these; the benchmark may only raise them. (>= OWASP 2023 minimums.) */
        const val MIN_MEMORY_KIB = 64 * 1024
        const val MIN_ITERATIONS = 3
        const val MIN_PARALLELISM = 1

        fun freshDefault(): KdfParams = KdfParams(
            saltHex = SecureRandomProvider.nextBytes(SALT_BYTES).toHex(),
            memoryKib = MIN_MEMORY_KIB,
            iterations = MIN_ITERATIONS,
            parallelism = MIN_PARALLELISM,
        )

        fun withFreshSalt(base: KdfParams): KdfParams = base.copy(saltHex = SecureRandomProvider.nextBytes(SALT_BYTES).toHex())
    }
}

interface PassphraseKdf {
    /** Derives [KdfParams.outputBytes] bytes. Must be called off the main thread. */
    fun derive(passphrase: ByteArray, params: KdfParams): ByteArray

    /**
     * Picks parameters for THIS device: starts from the floor and raises iterations until one derivation
     * takes at least [targetMillis]. Never returns values below the floor.
     */
    fun calibrate(targetMillis: Long = DEFAULT_TARGET_MILLIS): KdfParams

    companion object {
        const val DEFAULT_TARGET_MILLIS = 700L
        const val MAX_CALIBRATION_ITERATIONS = 10
    }
}

/** Argon2id via argon2kt (JNI binding over the reference implementation). */
class Argon2Kdf(private val argon2: Argon2Kt = Argon2Kt()) : PassphraseKdf {

    override fun derive(passphrase: ByteArray, params: KdfParams): ByteArray {
        require(params.algorithm == KdfParams.ALGORITHM_ARGON2ID) { "unsupported KDF" }
        require(params.memoryKib >= KdfParams.MIN_MEMORY_KIB) { "memory cost below floor" }
        require(params.iterations >= KdfParams.MIN_ITERATIONS) { "iterations below floor" }
        require(params.parallelism >= KdfParams.MIN_PARALLELISM) { "parallelism below floor" }
        val version = when (params.version) {
            0x10 -> Argon2Version.V10
            0x13 -> Argon2Version.V13
            else -> error("unsupported Argon2 version")
        }
        // Direct buffers so the native side can read them without copies we cannot clear.
        val pw = ByteBuffer.allocateDirect(passphrase.size).put(passphrase).apply { flip() }
        val salt = params.salt
        val saltBuf = ByteBuffer.allocateDirect(salt.size).put(salt).apply { flip() }
        try {
            val result = argon2.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = pw,
                salt = saltBuf,
                tCostInIterations = params.iterations,
                mCostInKibibyte = params.memoryKib,
                parallelism = params.parallelism,
                hashLengthInBytes = params.outputBytes,
                version = version,
            )
            val out = result.rawHashAsByteArray()
            result.rawHash.clear()
            for (i in 0 until result.rawHash.capacity()) result.rawHash.put(i, 0)
            return out
        } finally {
            for (i in 0 until pw.capacity()) pw.put(i, 0)
        }
    }

    override fun calibrate(targetMillis: Long): KdfParams {
        var params = KdfParams.freshDefault()
        val probe = "calibration-probe".toByteArray()
        repeat(PassphraseKdf.MAX_CALIBRATION_ITERATIONS) {
            val start = System.nanoTime()
            derive(probe, params).wipe()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            if (elapsed >= targetMillis) return params
            params = params.copy(iterations = params.iterations + 1)
        }
        return params
    }
}
