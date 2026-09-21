package com.rombamukiyam.vault.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rombamukiyam.vault.data.entity.BackupMetadataEntity
import com.rombamukiyam.vault.data.entity.DiagnosticEventEntity
import com.rombamukiyam.vault.data.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE enabled = 1 ORDER BY nextDueDate ASC")
    fun observeEnabled(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE vaultItemId = :itemId")
    suspend fun getForItem(itemId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE vaultItemId = :itemId AND type = :type")
    suspend fun get(itemId: String, type: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE vaultItemId = :itemId AND type = :type")
    suspend fun delete(itemId: String, type: String)

    @Query("DELETE FROM reminders WHERE vaultItemId = :itemId")
    suspend fun deleteForItem(itemId: String)

    @Query("UPDATE reminders SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    @Query("UPDATE reminders SET nextDueDate = :nextDue, lastActionDate = :now, updatedAt = :now WHERE id = :id")
    suspend fun reschedule(id: String, nextDue: Long, now: Long)

    // ---- backup metadata
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBackup(meta: BackupMetadataEntity)

    @Query("SELECT * FROM backup_metadata ORDER BY createdAt DESC LIMIT 50")
    fun observeBackups(): Flow<List<BackupMetadataEntity>>

    @Query("SELECT * FROM backup_metadata ORDER BY createdAt DESC")
    suspend fun getAllBackups(): List<BackupMetadataEntity>

    // ---- diagnostics
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDiagnostic(event: DiagnosticEventEntity)

    @Query("SELECT * FROM diagnostic_events ORDER BY occurredAt DESC LIMIT 200")
    fun observeDiagnostics(): Flow<List<DiagnosticEventEntity>>

    @Query("DELETE FROM diagnostic_events")
    suspend fun clearDiagnostics()
}
