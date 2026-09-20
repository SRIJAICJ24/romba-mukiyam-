package com.rombamukiyam.vault.core.security

import com.google.common.truth.Truth.assertThat
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.crypto.Hkdf
import com.rombamukiyam.vault.core.crypto.KdfParams
import com.rombamukiyam.vault.core.crypto.PassphraseKdf
import com.rombamukiyam.vault.core.crypto.RecoveryKeyCodec
import java.io.File
import java.nio.file.Files
import org.junit.Before
import org.junit.Test

/** Fast, deterministic stand-in for Argon2id so the key-hierarchy logic can be tested on the JVM. */
class FakeKdf : PassphraseKdf {
    override fun derive(passphrase: ByteArray, params: KdfParams): ByteArray =
        Hkdf.derive(passphrase, params.salt, "fake-kdf".toByteArray(), params.outputBytes)

    override fun calibrate(targetMillis: Long): KdfParams = KdfParams.freshDefault()
}

class FixedClock(var now: Long = 1_700_000_000_000L) : Clock {
    override fun nowMillis(): Long = now
}

class VaultKeyManagerTest {

    private lateinit var dir: File
    private lateinit var store: KeyringStore
    private lateinit var manager: VaultKeyManager
    private val clock = FixedClock()
    private val pass = "correct horse battery staple".toByteArray()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("rm-keys").toFile()
        store = KeyringStore(dir)
        manager = VaultKeyManager(store, FakeKdf(), KeystoreManager(), clock)
    }

    @Test
    fun `create then unlock with passphrase yields same vault key`() {
        val created = manager.createVault(pass)
        assertThat(manager.vaultExists()).isTrue()
        val unlocked = manager.unlockWithPassphrase(pass)
        assertThat(unlocked).isEqualTo(created.vaultKey)
    }

    @Test
    fun `wrong passphrase returns null and leaves keyring intact`() {
        manager.createVault(pass)
        assertThat(manager.unlockWithPassphrase("wrong".toByteArray())).isNull()
        assertThat(manager.unlockWithPassphrase(pass)).isNotNull()
    }

    @Test
    fun `recovery key unlocks the same vault key`() {
        val created = manager.createVault(pass)
        val rk = RecoveryKeyCodec.decode(created.recoveryKeyEncoded)!!
        assertThat(manager.unlockWithRecoveryKey(rk)).isEqualTo(created.vaultKey)
        assertThat(manager.unlockWithRecoveryKey(RecoveryKeyCodec.generate())).isNull()
    }

    @Test
    fun `change passphrase re-wraps same vault key and invalidates old passphrase`() {
        val created = manager.createVault(pass)
        val newPass = "a completely different phrase".toByteArray()
        manager.changePassphrase(created.vaultKey, newPass)
        assertThat(manager.unlockWithPassphrase(pass)).isNull()
        assertThat(manager.unlockWithPassphrase(newPass)).isEqualTo(created.vaultKey)
        // Recovery key still valid (wraps the same key).
        val rk = RecoveryKeyCodec.decode(created.recoveryKeyEncoded)!!
        assertThat(manager.unlockWithRecoveryKey(rk)).isEqualTo(created.vaultKey)
    }

    @Test
    fun `change passphrase uses a fresh salt`() {
        val created = manager.createVault(pass)
        val saltBefore = store.read()!!.slot(KeySlotType.PASSPHRASE)!!.kdf!!.saltHex
        manager.changePassphrase(created.vaultKey, pass)
        val saltAfter = store.read()!!.slot(KeySlotType.PASSPHRASE)!!.kdf!!.saltHex
        assertThat(saltAfter).isNotEqualTo(saltBefore)
    }

    @Test
    fun `regenerating recovery key invalidates the old one`() {
        val created = manager.createVault(pass)
        val oldRk = RecoveryKeyCodec.decode(created.recoveryKeyEncoded)!!
        val newText = manager.regenerateRecoveryKey(created.vaultKey)
        val newRk = RecoveryKeyCodec.decode(newText)!!
        assertThat(manager.unlockWithRecoveryKey(oldRk)).isNull()
        assertThat(manager.unlockWithRecoveryKey(newRk)).isEqualTo(created.vaultKey)
    }

    @Test
    fun `slot metadata is authenticated - tampering the salt breaks unwrap`() {
        manager.createVault(pass)
        val ring = store.read()!!
        val slot = ring.slot(KeySlotType.PASSPHRASE)!!
        val tampered = slot.copy(kdf = slot.kdf!!.copy(iterations = slot.kdf!!.iterations + 1))
        store.write(ring.withSlot(tampered, clock.nowMillis()))
        assertThat(manager.unlockWithPassphrase(pass)).isNull()
    }

    @Test
    fun `keyring file contains no plaintext key material`() {
        val created = manager.createVault(pass)
        val json = File(dir, KeyringStore.FILE_NAME).readText()
        assertThat(json).doesNotContain(created.vaultKey.toHexString())
        assertThat(json).doesNotContain(String(pass))
        assertThat(json).doesNotContain(created.recoveryKeyEncoded)
        assertThat(json).doesNotContain(RecoveryKeyCodec.decode(created.recoveryKeyEncoded)!!.toHexString())
    }

    @Test
    fun `keyring store keeps a backup copy and recovers from a corrupt primary`() {
        val created = manager.createVault(pass)
        manager.changePassphrase(created.vaultKey, "second".toByteArray())
        assertThat(File(dir, "${KeyringStore.FILE_NAME}.bak").exists()).isTrue()
        // Corrupt the primary: fallback to .bak must work (previous passphrase generation).
        File(dir, KeyringStore.FILE_NAME).writeText("{ garbage")
        assertThat(store.read()).isNotNull()
        assertThat(manager.unlockWithPassphrase(pass)).isEqualTo(created.vaultKey)
    }

    @Test
    fun `KDF parameters never go below the floor`() {
        val p = KdfParams.freshDefault()
        assertThat(p.memoryKib).isAtLeast(64 * 1024)
        assertThat(p.iterations).isAtLeast(3)
        assertThat(p.parallelism).isAtLeast(1)
        assertThat(p.salt).hasLength(16)
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}
