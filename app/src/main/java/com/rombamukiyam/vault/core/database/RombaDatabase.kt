package com.rombamukiyam.vault.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rombamukiyam.vault.data.dao.FamilyProfileDao
import com.rombamukiyam.vault.data.dao.PasswordDao
import com.rombamukiyam.vault.data.dao.ReminderDao
import com.rombamukiyam.vault.data.dao.TagDao
import com.rombamukiyam.vault.data.dao.VaultItemDao
import com.rombamukiyam.vault.data.entity.BackupMetadataEntity
import com.rombamukiyam.vault.data.entity.CredentialFieldEntity
import com.rombamukiyam.vault.data.entity.DiagnosticEventEntity
import com.rombamukiyam.vault.data.entity.CredentialHistoryEntity
import com.rombamukiyam.vault.data.entity.FamilyProfileEntity
import com.rombamukiyam.vault.data.entity.PasswordHistoryEntity
import com.rombamukiyam.vault.data.entity.PasswordPolicyEntity
import com.rombamukiyam.vault.data.entity.ReminderEntity
import com.rombamukiyam.vault.data.entity.TagEntity
import com.rombamukiyam.vault.data.entity.VaultItemEntity
import com.rombamukiyam.vault.data.entity.VaultItemTagCrossRef

/**
 * The encrypted vault database (Room over SQLCipher).
 *
 * Schema history — every version is kept and migrated explicitly; see DATABASE.md.
 *   1: family_profiles, vault_items, credential_fields, credential_history, tags, vault_item_tags
 *   2: + password_history (keyed digests), password_policies
 *   3: + reminders, backup_metadata, diagnostic_events
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
        PasswordHistoryEntity::class,
        PasswordPolicyEntity::class,
        ReminderEntity::class,
        BackupMetadataEntity::class,
        DiagnosticEventEntity::class,
    ],
)
abstract class RombaDatabase : RoomDatabase() {
    abstract fun familyProfileDao(): FamilyProfileDao
    abstract fun vaultItemDao(): VaultItemDao
    abstract fun tagDao(): TagDao
    abstract fun passwordDao(): PasswordDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val SCHEMA_VERSION = 3
        const val FILE_NAME = "romba_vault.db"
    }
}
