package com.rombamukiyam.vault.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.rombamukiyam.vault.core.crypto.Aead
import com.rombamukiyam.vault.data.entity.FamilyProfileEntity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqlCipherDatabaseTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val factory = DatabaseFactory(context)
    private lateinit var file: File
    private val key = Aead.newKey()

    @Before
    fun setUp() {
        file = File(context.cacheDir, "test-${System.nanoTime()}.db")
    }

    @After
    fun tearDown() {
        file.delete(); File(file.path + "-wal").delete(); File(file.path + "-shm").delete()
    }

    private fun profile(id: String) = FamilyProfileEntity(id, "Dad", "DAD", "INITIALS", 0xFF2B2D6BL, 1L, 1L)

    @Test
    fun dataWrittenWithKeyIsReadableWithSameKey() = runBlocking<Unit> {
        factory.open(key, file).use { db -> db.familyProfileDao().insert(profile("p1")) }
        factory.open(key, file).use { db -> assertThat(db.familyProfileDao().getById("p1")?.displayName).isEqualTo("Dad") }
    }

    @Test
    fun wrongKeyCannotOpenDatabase() = runBlocking<Unit> {
        factory.open(key, file).use { db -> db.familyProfileDao().insert(profile("p1")) }
        val wrong = Aead.newKey()
        val threw = try {
            factory.open(wrong, file).use { it.familyProfileDao().count() }
            false
        } catch (e: Exception) {
            true
        }
        assertThat(threw).isTrue()
        // The file is untouched by the failed attempt.
        factory.open(key, file).use { db -> assertThat(db.familyProfileDao().count()).isEqualTo(1) }
    }

    @Test
    fun databaseFileIsNotPlaintext() = runBlocking<Unit> {
        factory.open(key, file).use { db -> db.familyProfileDao().insert(profile("p1")) }
        val bytes = file.readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        // Long, structured strings only: a 3-letter value can appear in random ciphertext by chance.
        assertThat(text).doesNotContain("SQLite format 3")
        assertThat(text).doesNotContain("CREATE TABLE")
        assertThat(text).doesNotContain("family_profiles")
        assertThat(text).doesNotContain("credential_fields")
    }

    @Test
    fun quickIntegrityCheckPassesOnFreshDatabase() = runBlocking<Unit> {
        factory.open(key, file).use { db ->
            val result = IntegrityChecker().check(db.openHelper.writableDatabase, IntegrityLevel.FULL, 1L)
            assertThat(result.ok).isTrue()
            assertThat(result.tableCounts.keys).containsAtLeast("family_profiles", "vault_items", "credential_fields")
        }
    }

    @Test
    fun foreignKeyRestrictPreventsDeletingOwnerWithItems() = runBlocking<Unit> {
        factory.open(key, file).use { db ->
            db.familyProfileDao().insert(profile("p1"))
            db.vaultItemDao().insert(
                com.rombamukiyam.vault.data.entity.VaultItemEntity(
                    id = "i1", ownerId = "p1", category = "BANK", title = "SBI", createdAt = 1L, updatedAt = 1L,
                ),
            )
            val threw = try {
                db.openHelper.writableDatabase.execSQL("DELETE FROM family_profiles WHERE id = 'p1'")
                false
            } catch (e: Exception) {
                true
            }
            assertThat(threw).isTrue()
            assertThat(db.familyProfileDao().count()).isEqualTo(1)
        }
    }

    private inline fun <T> RombaDatabase.use(block: (RombaDatabase) -> T): T = try {
        block(this)
    } finally {
        factory.closeAndWipe(this)
    }

    @Test
    fun multipleConnectionsWorkAfterOpen() = runBlocking<Unit> {
        // Room uses separate reader/writer connections; each must be able to key the database.
        factory.open(key, file).use { db ->
            db.familyProfileDao().insert(profile("p1"))
            assertThat(db.familyProfileDao().count()).isEqualTo(1)
            assertThat(db.familyProfileDao().observeAll().first()).hasSize(1)
        }
    }
}
