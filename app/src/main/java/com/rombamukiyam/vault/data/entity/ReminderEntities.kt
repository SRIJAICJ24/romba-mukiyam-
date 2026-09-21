package com.rombamukiyam.vault.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Schema v3. One reminder per (item, type). Vault-wide reminders (backup) have vaultItemId = null. */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(entity = VaultItemEntity::class, parentColumns = ["id"], childColumns = ["vaultItemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("vaultItemId"), Index("nextDueDate"), Index("type"), Index(value = ["vaultItemId", "type"], unique = true)],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val vaultItemId: String?,
    /** ReminderType enum name. */
    val type: String,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    val lastActionDate: Long? = null,
    val nextDueDate: Long,
    val intervalDays: Int? = null,
    @ColumnInfo(defaultValue = "7") val notifyBeforeDays: Int = 7,
    val customLabel: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    override fun toString(): String = "Reminder(id=$id, type=$type)"
}

/** Schema v3. Record of every backup created/verified/restored. Contains no vault content. */
@Entity(tableName = "backup_metadata", indices = [Index("createdAt")])
data class BackupMetadataEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    /** Display name of the chosen file (user-visible; never includes owner or secret data). */
    val displayName: String?,
    val sizeBytes: Long,
    val itemCount: Int,
    val formatVersion: Int,
    val schemaVersion: Int,
    @ColumnInfo(defaultValue = "0") val verified: Boolean,
    /** BackupEvent enum name: CREATED, RESTORED. */
    val event: String,
)

/** Schema v3. Local diagnostics: safe codes only, never secrets, user-clearable. */
@Entity(tableName = "diagnostic_events", indices = [Index("occurredAt")])
data class DiagnosticEventEntity(
    @PrimaryKey val id: String,
    val occurredAt: Long,
    val appVersion: String,
    val schemaVersion: Int,
    val module: String,
    val code: String,
    val exceptionClass: String?,
)
