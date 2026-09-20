package com.rombamukiyam.vault.domain.repository

import com.rombamukiyam.vault.domain.model.AvatarType
import com.rombamukiyam.vault.domain.model.FamilyProfile
import com.rombamukiyam.vault.domain.model.HistoryEvent
import com.rombamukiyam.vault.domain.model.HistoryEventType
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
    /** Atomic password change: field value + lastChanged + history (+ history digest handled in Phase 3). */
    suspend fun changePassword(itemId: String, fieldId: String, newPassword: String, changedAt: Long)
    suspend fun markReviewed(itemId: String, reviewedAt: Long)
}
