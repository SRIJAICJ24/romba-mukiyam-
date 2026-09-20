package com.rombamukiyam.vault.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rombamukiyam.vault.data.dao.FamilyProfileDao
import com.rombamukiyam.vault.data.dao.TagDao
import com.rombamukiyam.vault.data.dao.VaultItemDao
import com.rombamukiyam.vault.data.entity.CredentialFieldEntity
import com.rombamukiyam.vault.data.entity.CredentialHistoryEntity
import com.rombamukiyam.vault.data.entity.FamilyProfileEntity
import com.rombamukiyam.vault.data.entity.TagEntity
import com.rombamukiyam.vault.data.entity.VaultItemEntity
import com.rombamukiyam.vault.data.entity.VaultItemTagCrossRef

/**
 * The encrypted vault database (Room over SQLCipher).
 *
 * Schema history — every version is kept and migrated explicitly; see DATABASE.md.
 *   1: family_profiles, vault_items, credential_fields, credential_history, tags, vault_item_tags
 */
@Database(
    version = RombaDatabase.SCHEMA_VERSION,
    exportSchema = true,
    entities = [
        FamilyProfileEntity::class,
        VaultItemEntity::class,
        CredentialFieldEntity::class,
        CredentialHistoryEntity::class,
        TagEntity::class,
        VaultItemTagCrossRef::class,
    ],
)
abstract class RombaDatabase : RoomDatabase() {
    abstract fun familyProfileDao(): FamilyProfileDao
    abstract fun vaultItemDao(): VaultItemDao
    abstract fun tagDao(): TagDao

    companion object {
        const val SCHEMA_VERSION = 1
        const val FILE_NAME = "romba_vault.db"
    }
}
