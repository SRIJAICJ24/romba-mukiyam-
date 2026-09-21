package com.rombamukiyam.vault.domain.repository

import com.rombamukiyam.vault.domain.model.AvatarType
import com.rombamukiyam.vault.domain.model.FamilyProfile
import com.rombamukiyam.vault.domain.model.FieldDraft
import com.rombamukiyam.vault.domain.model.Reminder
import com.rombamukiyam.vault.domain.model.ReminderType
import com.rombamukiyam.vault.domain.model.HistoryEvent
import com.rombamukiyam.vault.domain.model.HistoryEventType
import com.rombamukiyam.vault.domain.model.PasswordDigest
import com.rombamukiyam.vault.domain.model.PasswordPolicy
import com.rombamukiyam.vault.domain.model.Relationship
import com.rombamukiyam.vault.domain.model.VaultCategory
import com.rombamukiyam.vault.domain.model.VaultItem
import com.rombamukiyam.vault.domain.model.VaultItemDraft
import com.rombamukiyam.vault.domain.model.VaultItemSummary
import kotlinx.coroutines.flow.Flow

/** Vault-wide event log (no item). Implementations must be a no-op when the vault is locked. */
interface VaultEventLogger {
    suspend fun log(type: HistoryEventType, detail: String?)
}

interface FamilyRepository {
    fun observeAll(): Flow<List<FamilyProfile>>
    fun observeActive(): Flow<List<FamilyProfile>>
    suspend fun get(id: String): FamilyProfile?
    suspend fun count(): Int
    suspend fun create(displayName: String, relationship: Relationship, avatarType: AvatarType, avatarColor: Long): FamilyProfile
    suspend fun update(profile: FamilyProfile)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun ownedItemCount(id: String): Int
}

data class CategoryCountSummary(val category: VaultCategory, val count: Int)

interface VaultRepository {
    fun observeActive(): Flow<List<VaultItemSummary>>
    fun observeFavorites(): Flow<List<VaultItemSummary>>
    fun observeByCategory(category: VaultCategory): Flow<List<VaultItemSummary>>
    fun observeByOwner(ownerId: String): Flow<List<VaultItemSummary>>
    fun observeTrash(): Flow<List<VaultItemSummary>>
    fun observeCategoryCounts(): Flow<List<CategoryCountSummary>>
    fun observeActiveCount(): Flow<Int>
    fun observeItem(id: String): Flow<VaultItem?>
    fun observeHistory(itemId: String): Flow<List<HistoryEvent>>
    suspend fun getItem(id: String): VaultItem?
    suspend fun search(query: String): List<VaultItemSummary>
    suspend fun create(draft: VaultItemDraft): String
    suspend fun update(draft: VaultItemDraft)
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun moveToTrash(id: String)
    suspend fun restore(id: String)
    suspend fun deletePermanently(id: String)
    suspend fun purgeTrashOlderThan(days: Int): Int
    /** Atomic password change: field value + lastChanged + history event + history digest, all-or-nothing. */
    suspend fun changePassword(itemId: String, fieldId: String, newPassword: String, changedAt: Long)
    suspend fun markReviewed(itemId: String, reviewedAt: Long)

    // ---- password tools (schema v2)
    fun observeCurrentDigests(): Flow<List<PasswordDigest>>
    suspend fun wasUsedBefore(itemId: String, fieldId: String, password: String): Boolean
    fun observePolicy(itemId: String): Flow<PasswordPolicy?>
    suspend fun getPolicy(itemId: String): PasswordPolicy?
    suspend fun savePolicy(policy: PasswordPolicy)
    suspend fun deletePolicy(itemId: String)
    /** Decrypted primary-password values for local health analysis only; never leaves the process. */
    suspend fun primaryPasswordsForHealth(): List<Triple<String, String, String>>
}

interface ReminderRepository {
    fun observeAll(): Flow<List<Reminder>>
    suspend fun forItem(itemId: String): List<Reminder>
    suspend fun upsert(reminder: Reminder)
    suspend fun delete(itemId: String, type: ReminderType)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun markDone(id: String)
    suspend fun syncForItem(itemId: String, category: VaultCategory, passwordLastChangedAt: Long?, reviewIntervalDays: Int?, fields: List<FieldDraft>)
    suspend fun republish()
}

data class BackupRecord(val id: String, val createdAt: Long, val displayName: String?, val sizeBytes: Long, val itemCount: Int, val verified: Boolean, val event: String)

data class DiagnosticEvent(val id: String, val occurredAt: Long, val appVersion: String, val schemaVersion: Int, val module: String, val code: String, val exceptionClass: String?)

interface DiagnosticsRepository {
    fun observe(): Flow<List<DiagnosticEvent>>
    suspend fun record(module: String, code: String, exception: Throwable?)
    suspend fun clear()
}
