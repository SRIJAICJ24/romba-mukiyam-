package com.rombamukiyam.vault.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.crypto.Aead
import com.rombamukiyam.vault.core.database.DatabaseFactory
import com.rombamukiyam.vault.core.database.IntegrityChecker
import com.rombamukiyam.vault.core.database.IntegrityLevel
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.data.entity.CredentialHistoryEntity
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

/**
 * End-to-end data layer over a real SQLCipher database: encryption of fields, atomic multi-step writes,
 * rollback on failure, trash/restore, search and reuse digests.
 */
@RunWith(AndroidJUnit4::class)
class VaultRepositoryInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var file: File
    private lateinit var session: VaultSession
    private lateinit var vault: VaultRepositoryImpl
    private lateinit var family: FamilyRepositoryImpl
    private var now = 1_700_000_000_000L
    private val clock = object : Clock { override fun nowMillis() = now }

    private class TestFactory(context: android.content.Context, private val file: File) : DatabaseFactory(context) {
        override fun databaseFile(): File = file
    }

    @Before
    fun setUp() {
        file = File(context.cacheDir, "repo-${System.nanoTime()}.db")
        session = VaultSession(TestFactory(context, file), IntegrityChecker(), clock)
        session.unlock(Aead.newKey())
        vault = VaultRepositoryImpl(session, clock)
        family = FamilyRepositoryImpl(session, clock)
    }

    @After
    fun tearDown() {
        session.lock()
        file.delete(); File(file.path + "-wal").delete(); File(file.path + "-shm").delete()
    }

    private suspend fun owner() = family.create("Dad", Relationship.DAD, AvatarType.INITIALS, 1L).id

    private fun bankDraft(ownerId: String, password: String, title: String = "SBI") = VaultItemDraft(
        ownerId = ownerId, category = VaultCategory.BANK, title = title, institution = "State Bank",
        officialWebsite = "https://onlinesbi.sbi/login",
        fields = listOf(
            FieldDraft(type = FieldType.CUSTOMER_ID, label = "Customer ID", value = "12345"),
            FieldDraft(type = FieldType.PASSWORD, label = "Login password", value = password),
        ),
        tags = listOf("bank", "dad"),
        reviewIntervalDays = 90,
    )

    @Test
    fun createReadRoundTripWithEncryptedFields() = runBlocking<Unit> {
        val o = owner()
        val id = vault.create(bankDraft(o, "S3cret-Pass"))
        val item = vault.getItem(id)!!
        assertThat(item.fields.map { it.value }).containsExactly("12345", "S3cret-Pass")
        assertThat(item.officialDomain).isEqualTo("onlinesbi.sbi")
        assertThat(item.tags).containsExactly("bank", "dad")
        // Stored bytes are ciphertext, not the value.
        val raw = session.database().vaultItemDao().getFields(id)
        raw.forEach { assertThat(String(it.encryptedValue, Charsets.ISO_8859_1)).doesNotContain("S3cret") }
        // History and digest recorded in the same transaction.
        assertThat(vault.observeHistory(id).first()).hasSize(1)
        assertThat(vault.observeCurrentDigests().first()).hasSize(1)
    }

    @Test
    fun updateReplacesFieldsAtomically() = runBlocking<Unit> {
        val o = owner()
        val id = vault.create(bankDraft(o, "first"))
        val item = vault.getItem(id)!!
        val draft = bankDraft(o, "second", title = "SBI Savings").copy(
            id = id,
            fields = item.fields.map { FieldDraft(id = it.id, type = it.type, label = it.label, value = if (it.type == FieldType.PASSWORD) "second" else it.value) },
        )
        vault.update(draft)
        val updated = vault.getItem(id)!!
        assertThat(updated.title).isEqualTo("SBI Savings")
        assertThat(updated.fields.first { it.type == FieldType.PASSWORD }.value).isEqualTo("second")
        assertThat(updated.fields).hasSize(2)
    }

    @Test
    fun failedTransactionLeavesPreviousDataIntact() = runBlocking<Unit> {
        val o = owner()
        val id = vault.create(bankDraft(o, "keep-me"))
        val dao = session.database().vaultItemDao()
        val before = dao.getWithFields(id)!!
        // Force a constraint violation inside the composite transaction: duplicate history id.
        val existingHistoryId = dao.getAllHistory().first().id
        val threw = try {
            dao.updateItemWithFields(
                before.item.copy(title = "SHOULD NOT PERSIST"),
                emptyList(),
                CredentialHistoryEntity(existingHistoryId, id, "CREDENTIAL_UPDATED", null, now),
            )
            false
        } catch (e: Exception) {
            true
        }
        assertThat(threw).isTrue()
        val after = dao.getWithFields(id)!!
        assertThat(after.item.title).isEqualTo("SBI")
        assertThat(after.fields).hasSize(2)
    }

    @Test
    fun changePasswordIsAtomicAndRecordsDigest() = runBlocking<Unit> {
        val o = owner()
        val id = vault.create(bankDraft(o, "old-pass"))
        val field = vault.getItem(id)!!.fields.first { it.type == FieldType.PASSWORD }
        now += 1000
        vault.changePassword(id, field.id, "new-pass", now)
        val item = vault.getItem(id)!!
        assertThat(item.fields.first { it.type == FieldType.PASSWORD }.value).isEqualTo("new-pass")
        assertThat(item.passwordLastChangedAt).isEqualTo(now)
        assertThat(vault.wasUsedBefore(id, field.id, "old-pass")).isTrue()
        assertThat(vault.wasUsedBefore(id, field.id, "never")).isFalse()
        assertThat(vault.observeHistory(id).first().map { it.type.name }).contains("PASSWORD_CHANGED")
    }

    @Test
    fun reuseAcrossItemsIsDetectableByDigest() = runBlocking<Unit> {
        val o = owner()
        vault.create(bankDraft(o, "same-password", title = "A"))
        vault.create(bankDraft(o, "same-password", title = "B"))
        vault.create(bankDraft(o, "unique", title = "C"))
        val digests = vault.observeCurrentDigests().first()
        val groups = digests.groupBy { it.digestHex }
        assertThat(groups.values.count { it.size == 2 }).isEqualTo(1)
    }

    @Test
    fun trashRestoreAndPermanentDelete() = runBlocking<Unit> {
        val o = owner()
        val id = vault.create(bankDraft(o, "x"))
        vault.moveToTrash(id)
        assertThat(vault.observeActive().first()).isEmpty()
        assertThat(vault.observeTrash().first()).hasSize(1)
        vault.restore(id)
        assertThat(vault.observeActive().first()).hasSize(1)
        vault.moveToTrash(id)
        vault.deletePermanently(id)
        assertThat(vault.getItem(id)).isNull()
        assertThat(session.database().vaultItemDao().getAllFields()).isEmpty()
        assertThat(session.database().passwordDao().getAllHistory()).isEmpty()
    }

    @Test
    fun searchMatchesMetadataOnlyNeverSecrets() = runBlocking<Unit> {
        val o = owner()
        vault.create(bankDraft(o, "TopSecretValue"))
        assertThat(vault.search("sbi")).hasSize(1)
        assertThat(vault.search("dad")).hasSize(1) // owner name / tag
        assertThat(vault.search("onlinesbi")).hasSize(1)
        assertThat(vault.search("TopSecret")).isEmpty()
        assertThat(vault.search("12345")).isEmpty() // customer id is a field value, never searched
    }

    @Test
    fun fiveHundredItemsSearchQuickly() = runBlocking<Unit> {
        val o = owner()
        repeat(500) { i -> vault.create(bankDraft(o, "pw$i", title = "Account $i")) }
        val start = System.nanoTime()
        val hits = vault.search("Account 42")
        val ms = (System.nanoTime() - start) / 1_000_000
        assertThat(hits.map { it.title }).contains("Account 42")
        assertThat(ms).isLessThan(2000)
        val integrity = IntegrityChecker().check(session.database().openHelper.writableDatabase, IntegrityLevel.FULL, now)
        assertThat(integrity.ok).isTrue()
        assertThat(integrity.tableCounts["vault_items"]).isEqualTo(500)
    }

    @Test
    fun ownerWithItemsCannotBeDeletedButCanBeArchived() = runBlocking<Unit> {
        val o = owner()
        vault.create(bankDraft(o, "x"))
        family.setArchived(o, true)
        assertThat(family.observeActive().first()).isEmpty()
        assertThat(vault.observeActive().first()).hasSize(1)
        val threw = try {
            session.database().openHelper.writableDatabase.execSQL("DELETE FROM family_profiles WHERE id = ?", arrayOf(o))
            false
        } catch (e: Exception) {
            true
        }
        assertThat(threw).isTrue()
    }
}
