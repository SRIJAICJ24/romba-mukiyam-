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
