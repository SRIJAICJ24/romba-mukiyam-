package com.rombamukiyam.vault.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.rombamukiyam.vault.core.crypto.Aead
import com.rombamukiyam.vault.core.crypto.toHex
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Rule
import org.junit.Test

/**
 * Room migration tests over SQLCipher. Every shipped schema version gets seeded with realistic rows and is
 * migrated to the current version; row counts and key values must survive. Schemas come from app/schemas.
 */
class MigrationTest {

    private val dbName = "migration-test.db"
    private val rawKey = "x'${Aead.newKey().toHex()}'".toByteArray(Charsets.US_ASCII)

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RombaDatabase::class.java,
        emptyList(),
        SupportOpenHelperFactory(rawKey.copyOf()),
    )

    private fun seedV1(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO family_profiles (id, displayName, relationship, avatarType, avatarColor, createdAt, updatedAt, archived, sortOrder) " +
                "VALUES ('p1','Dad','DAD','INITIALS',1,1,1,0,0)",
        )
        db.execSQL(
            "INSERT INTO vault_items (id, ownerId, category, title, favorite, highSecurity, createdAt, updatedAt) " +
                "VALUES ('i1','p1','BANK','SBI',1,1,1,1)",
        )
        db.execSQL(
            "INSERT INTO credential_fields (id, vaultItemId, fieldType, label, encryptedValue, isSecret, isSensitive, displayOrder, createdAt, updatedAt) " +
                "VALUES ('f1','i1','PASSWORD','Login password', x'00112233', 1, 1, 0, 1, 1)",
        )
        db.execSQL("INSERT INTO credential_history (id, vaultItemId, eventType, occurredAt) VALUES ('h1','i1','CREDENTIAL_CREATED',1)")
        db.execSQL("INSERT INTO tags (id, name, createdAt) VALUES ('t1','family',1)")
        db.execSQL("INSERT INTO vault_item_tags (vaultItemId, tagId) VALUES ('i1','t1')")
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(table: String): Int = query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun migrate1To2_preservesAllRowsAndAddsNewTables() {
        helper.createDatabase(dbName, 1).apply { seedV1(this); close() }
        val db = helper.runMigrationsAndValidate(dbName, 2, true, MigrationRegistry.MIGRATION_1_2)
        assertThat(db.count("family_profiles")).isEqualTo(1)
        assertThat(db.count("vault_items")).isEqualTo(1)
        assertThat(db.count("credential_fields")).isEqualTo(1)
        assertThat(db.count("credential_history")).isEqualTo(1)
        assertThat(db.count("vault_item_tags")).isEqualTo(1)
        assertThat(db.count("password_history")).isEqualTo(0)
        assertThat(db.count("password_policies")).isEqualTo(0)
        // Encrypted blob is byte-identical after migration.
        db.query("SELECT encryptedValue FROM credential_fields WHERE id='f1'").use { c -> c.moveToFirst(); assertThat(c.getBlob(0)).isEqualTo(byteArrayOf(0x00, 0x11, 0x22, 0x33)) }
        // New tables are usable and cascade from vault_items.
        db.execSQL("INSERT INTO password_history (id, vaultItemId, fieldId, digestHex, current, changedAt) VALUES ('ph1','i1','f1','abcd',1,5)")
        db.execSQL("INSERT INTO password_policies (vaultItemId, requireDigit, updatedAt) VALUES ('i1',1,5)")
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM vault_items WHERE id='i1'")
        assertThat(db.count("password_history")).isEqualTo(0)
        assertThat(db.count("password_policies")).isEqualTo(0)
        db.close()
    }

    @Test
    fun migrate2To3_addsReminderTablesAndKeepsRows() {
        helper.createDatabase(dbName, 2).apply {
            seedV1(this)
            execSQL("INSERT INTO password_history (id, vaultItemId, fieldId, digestHex, current, changedAt) VALUES ('ph1','i1','f1','abcd',1,5)")
            execSQL("INSERT INTO password_policies (vaultItemId, requireDigit, updatedAt) VALUES ('i1',1,5)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 3, true, MigrationRegistry.MIGRATION_2_3)
        assertThat(db.count("password_history")).isEqualTo(1)
        assertThat(db.count("password_policies")).isEqualTo(1)
        assertThat(db.count("reminders")).isEqualTo(0)
        db.execSQL("INSERT INTO reminders (id, vaultItemId, type, enabled, nextDueDate, notifyBeforeDays, createdAt, updatedAt) VALUES ('r1','i1','PASSWORD_REVIEW',1,99,7,1,1)")
        db.execSQL("INSERT INTO backup_metadata (id, createdAt, sizeBytes, itemCount, formatVersion, schemaVersion, verified, event) VALUES ('b1',1,10,1,1,3,1,'CREATED')")
        db.execSQL("INSERT INTO diagnostic_events (id, occurredAt, appVersion, schemaVersion, module, code) VALUES ('d1',1,'1.0',3,'test','E_TEST')")
        assertThat(db.count("reminders")).isEqualTo(1)
        db.close()
    }

    @Test
    fun migrate1To3_fullChainPreservesData() {
        helper.createDatabase(dbName, 1).apply { seedV1(this); close() }
        val db = helper.runMigrationsAndValidate(dbName, 3, true, *MigrationRegistry.ALL)
        assertThat(db.count("vault_items")).isEqualTo(1)
        assertThat(db.count("credential_fields")).isEqualTo(1)
        assertThat(db.count("credential_history")).isEqualTo(1)
        assertThat(db.count("tags")).isEqualTo(1)
        assertThat(db.count("reminders")).isEqualTo(0)
        db.query("SELECT title FROM vault_items WHERE id='i1'").use { c -> c.moveToFirst(); assertThat(c.getString(0)).isEqualTo("SBI") }
        db.close()
    }

    @Test
    fun schema1_createsAndReopensAtCurrentVersion() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO family_profiles (id, displayName, relationship, avatarType, avatarColor, createdAt, updatedAt, archived, sortOrder) " +
                    "VALUES ('p1','Dad','DAD','INITIALS',1,1,1,0,0)",
            )
            execSQL(
                "INSERT INTO vault_items (id, ownerId, category, title, favorite, highSecurity, createdAt, updatedAt) " +
                    "VALUES ('i1','p1','BANK','SBI',1,1,1,1)",
            )
            execSQL(
                "INSERT INTO credential_fields (id, vaultItemId, fieldType, label, encryptedValue, isSecret, isSensitive, displayOrder, createdAt, updatedAt) " +
                    "VALUES ('f1','i1','PASSWORD','Login password', x'00112233', 1, 1, 0, 1, 1)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, RombaDatabase.SCHEMA_VERSION, true, *MigrationRegistry.ALL)
        db.query("SELECT COUNT(*) FROM vault_items").use { c -> c.moveToFirst(); assertThat(c.getInt(0)).isEqualTo(1) }
        db.query("SELECT COUNT(*) FROM credential_fields").use { c -> c.moveToFirst(); assertThat(c.getInt(0)).isEqualTo(1) }
        db.query("SELECT title FROM vault_items WHERE id='i1'").use { c -> c.moveToFirst(); assertThat(c.getString(0)).isEqualTo("SBI") }
        db.close()
    }
}
