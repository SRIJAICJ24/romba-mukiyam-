package com.rombamukiyam.vault.core.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.common.InvalidBackupReason
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.core.common.VaultException
import com.rombamukiyam.vault.core.crypto.Hkdf
import com.rombamukiyam.vault.core.crypto.KdfParams
import com.rombamukiyam.vault.core.crypto.PassphraseKdf
import com.rombamukiyam.vault.core.database.DatabaseFactory
import com.rombamukiyam.vault.core.database.IntegrityChecker
import com.rombamukiyam.vault.core.security.KeyringStore
import com.rombamukiyam.vault.core.security.KeystoreManager
import com.rombamukiyam.vault.core.security.VaultKeyManager
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.data.repository.FamilyRepositoryImpl
import com.rombamukiyam.vault.data.repository.VaultRepositoryImpl
import com.rombamukiyam.vault.domain.model.AvatarType
import com.rombamukiyam.vault.domain.model.FieldDraft
import com.rombamukiyam.vault.domain.model.FieldType
import com.rombamukiyam.vault.domain.model.Relationship
import com.rombamukiyam.vault.domain.model.VaultCategory
import com.rombamukiyam.vault.domain.model.VaultItemDraft
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Fast KDF so the suite stays quick; the real Argon2 path is covered by Argon2KdfInstrumentedTest. */
private class FastKdf : PassphraseKdf {
    override fun derive(passphrase: ByteArray, params: KdfParams): ByteArray = Hkdf.derive(passphrase, params.salt, "test".toByteArray(), 32)
    override fun calibrate(targetMillis: Long): KdfParams = KdfParams.freshDefault()
}

@RunWith(AndroidJUnit4::class)
class BackupInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var work: File
    private lateinit var dbFile: File
    private lateinit var session: VaultSession
    private lateinit var keys: VaultKeyManager
    private lateinit var manager: BackupManager
    private lateinit var vault: VaultRepositoryImpl
    private val pass = "family-passphrase-1".toByteArray()
    private var now = 1_800_000_000_000L
    private val clock = object : Clock { override fun nowMillis() = now }

    private class TestFactory(context: Context, private val file: File) : DatabaseFactory(context) {
        override fun databaseFile(): File = file
    }

    @Before
    fun setUp() {
        work = File(context.cacheDir, "bk-${System.nanoTime()}").apply { mkdirs() }
        dbFile = File(work, "vault.db")
        val factory = TestFactory(context, dbFile)
        val store = KeyringStore(File(work, "keys"))
        keys = VaultKeyManager(store, FastKdf(), KeystoreManager(), clock)
        session = VaultSession(factory, IntegrityChecker(), clock)
        val created = keys.createVault(pass)
        session.unlock(created.vaultKey)
        manager = BackupManager(context, session, keys, FastKdf(), factory, store, IntegrityChecker(), clock)
        vault = VaultRepositoryImpl(session, clock)
        runBlocking {
            val owner = FamilyRepositoryImpl(session, clock).create("Dad", Relationship.DAD, AvatarType.INITIALS, 1L).id
            repeat(3) { i ->
                vault.create(
                    VaultItemDraft(
                        ownerId = owner, category = VaultCategory.BANK, title = "Bank $i", institution = "Inst $i",
                        fields = listOf(FieldDraft(type = FieldType.USERNAME, label = "User", value = "user$i"), FieldDraft(type = FieldType.PASSWORD, label = "Password", value = "Secret-$i-xyz")),
                        tags = listOf("t$i"), reviewIntervalDays = 90, passwordLastChangedAt = now,
                    ),
                )
            }
        }
    }

    @After
    fun tearDown() {
        session.lock()
        work.deleteRecursively()
    }

    private fun uriFor(name: String): Uri = Uri.fromFile(File(work, name))

    @Test
    fun backupRoundTripRestoresIdenticalContent() = runBlocking<Unit> {
        val result = manager.createBackup(uriFor("a.rmvault"), pass, "a.rmvault")
        assertThat(result.verified).isTrue()
        assertThat(result.itemCount).isEqualTo(3)
        val before = vault.observeActive().first().map { it.title }.sorted()
        val beforeFields = vault.getItem(vault.observeActive().first().first().id)!!.fields.map { it.value }

        // Mutate the live vault, then restore -> content must match the backup snapshot exactly.
        vault.create(VaultItemDraft(ownerId = vault.observeActive().first().first().ownerId, category = VaultCategory.OTHER, title = "Extra"))
        assertThat(vault.observeActive().first()).hasSize(4)
        manager.restore(uriFor("a.rmvault"), pass, "a.rmvault")
        val after = vault.observeActive().first()
        assertThat(after.map { it.title }.sorted()).isEqualTo(before)
        assertThat(vault.getItem(after.first { it.title == before.first() }.id)!!.fields.map { it.value }).isEqualTo(beforeFields)
        assertThat(vault.getItem(after.first().id)!!.tags).isNotEmpty()
        // Digests were re-derived on import, so reuse detection still works.
        assertThat(vault.observeCurrentDigests().first()).hasSize(3)
    }

    @Test
    fun backupFileIsEncryptedAndCarriesNoPlaintext() = runBlocking<Unit> {
        manager.createBackup(uriFor("b.rmvault"), pass, null)
        val bytes = File(work, "b.rmvault").readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        assertThat(text).startsWith("RMVAULT")
        assertThat(text).doesNotContain("Secret-0-xyz")
        assertThat(text).doesNotContain("\"title\"")
        assertThat(text).doesNotContain("Bank 0")
    }

    @Test
    fun wrongPassphraseIsRejectedAndVaultUntouched() = runBlocking<Unit> {
        manager.createBackup(uriFor("c.rmvault"), pass, null)
        val before = dbFile.readBytes()
        val err = try {
            manager.restore(uriFor("c.rmvault"), "wrong".toByteArray(), null); null
        } catch (e: VaultException) {
            e.error
        }
        assertThat((err as VaultError.InvalidBackup).reason).isEqualTo(InvalidBackupReason.AUTH_FAILED)
        assertThat(vault.observeActive().first()).hasSize(3)
        assertThat(dbFile.readBytes()).isEqualTo(before)
    }

    @Test
    fun tamperedBackupIsRejected() = runBlocking<Unit> {
        manager.createBackup(uriFor("d.rmvault"), pass, null)
        val f = File(work, "d.rmvault")
        val bytes = f.readBytes()
        // Flip a byte in the ciphertext body.
        bytes[bytes.size - 40] = (bytes[bytes.size - 40].toInt() xor 0x55).toByte()
        File(work, "d1.rmvault").writeBytes(bytes)
        assertThat(reasonOf { manager.verify(uriFor("d1.rmvault"), pass) }).isEqualTo(InvalidBackupReason.AUTH_FAILED)
        // Flip a byte in the header (schema version) -> AAD mismatch.
        val hdr = f.readBytes(); hdr[11] = (hdr[11].toInt() xor 0x01).toByte()
        File(work, "d2.rmvault").writeBytes(hdr)
        assertThat(reasonOf { manager.verify(uriFor("d2.rmvault"), pass) }).isAnyOf(InvalidBackupReason.AUTH_FAILED, InvalidBackupReason.NEWER_SCHEMA)
        // Truncated file.
        File(work, "d3.rmvault").writeBytes(f.readBytes().copyOf(f.length().toInt() - 10))
        assertThat(reasonOf { manager.verify(uriFor("d3.rmvault"), pass) }).isEqualTo(InvalidBackupReason.TRUNCATED)
        // Not a backup at all.
        File(work, "d4.rmvault").writeText("hello world this is not a vault")
        assertThat(reasonOf { manager.verify(uriFor("d4.rmvault"), pass) }).isEqualTo(InvalidBackupReason.BAD_MAGIC)
        // Live vault still intact after all the failed attempts.
        assertThat(vault.observeActive().first()).hasSize(3)
    }

    @Test
    fun newerSchemaIsRefused() = runBlocking<Unit> {
        manager.createBackup(uriFor("e.rmvault"), pass, null)
        val bytes = File(work, "e.rmvault").readBytes()
        bytes[11] = 99 // schemaVersion low byte -> 99
        File(work, "e1.rmvault").writeBytes(bytes)
        assertThat(reasonOf { manager.verify(uriFor("e1.rmvault"), pass) }).isEqualTo(InvalidBackupReason.NEWER_SCHEMA)
    }

    @Test
    fun weakenedKdfInHeaderIsRefused() = runBlocking<Unit> {
        manager.createBackup(uriFor("f.rmvault"), pass, null)
        val bytes = File(work, "f.rmvault").readBytes()
        // memoryKib is at offset 8+2+2+1+1+16 = 30 (4 bytes big-endian) -> set to 1024
        bytes[30] = 0; bytes[31] = 0; bytes[32] = 0x04; bytes[33] = 0
        File(work, "f1.rmvault").writeBytes(bytes)
        assertThat(reasonOf { manager.verify(uriFor("f1.rmvault"), pass) }).isEqualTo(InvalidBackupReason.CORRUPT_PAYLOAD)
    }

    @Test
    fun snapshotIsCreatedBeforeRestore() = runBlocking<Unit> {
        manager.createBackup(uriFor("g.rmvault"), pass, null)
        manager.listSnapshots().forEach { manager.deleteSnapshot(it) }
        assertThat(manager.listSnapshots()).isEmpty()
        manager.restore(uriFor("g.rmvault"), pass, null)
        val snaps = manager.listSnapshots()
        assertThat(snaps).hasSize(1)
        assertThat(File(snaps.first(), "vault.db").exists()).isTrue()
        manager.deleteSnapshot(snaps.first())
        assertThat(manager.listSnapshots()).isEmpty()
    }

    private suspend fun reasonOf(block: suspend () -> Unit): InvalidBackupReason? = try {
        block(); null
    } catch (e: VaultException) {
        (e.error as? VaultError.InvalidBackup)?.reason
    }
}
