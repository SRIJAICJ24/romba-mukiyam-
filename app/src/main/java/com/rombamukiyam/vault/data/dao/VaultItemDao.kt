package com.rombamukiyam.vault.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.rombamukiyam.vault.data.entity.CredentialFieldEntity
import com.rombamukiyam.vault.data.entity.CredentialHistoryEntity
import com.rombamukiyam.vault.data.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow

/** Item with its fields (fields still encrypted at this layer). */
data class VaultItemWithFields(
    @Embedded val item: VaultItemEntity,
    @Relation(parentColumn = "id", entityColumn = "vaultItemId")
    val fields: List<CredentialFieldEntity>,
)

@Dao
interface VaultItemDao {

    // ------------------------------------------------------------ reads

    @Query("SELECT * FROM vault_items WHERE deletedAt IS NULL ORDER BY favorite DESC, title COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE deletedAt IS NULL AND favorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE deletedAt IS NULL AND category = :category ORDER BY title COLLATE NOCASE ASC")
    fun observeByCategory(category: String): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE deletedAt IS NULL AND ownerId = :ownerId ORDER BY title COLLATE NOCASE ASC")
    fun observeByOwner(ownerId: String): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    fun observeById(id: String): Flow<VaultItemEntity?>

    @Transaction
    @Query("SELECT * FROM vault_items WHERE id = :id")
    fun observeWithFields(id: String): Flow<VaultItemWithFields?>

    @Transaction
    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getWithFields(id: String): VaultItemWithFields?

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getById(id: String): VaultItemEntity?

    @Query("SELECT * FROM vault_items ORDER BY createdAt ASC")
    suspend fun getAllIncludingTrash(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE deletedAt IS NULL")
    suspend fun getAllActive(): List<VaultItemEntity>

    @Query("SELECT COUNT(*) FROM vault_items WHERE deletedAt IS NULL")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM vault_items WHERE deletedAt IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT category, COUNT(*) AS count FROM vault_items WHERE deletedAt IS NULL GROUP BY category")
    fun observeCategoryCounts(): Flow<List<CategoryCount>>

    @Query("SELECT * FROM vault_items WHERE deletedAt IS NOT NULL AND deletedAt < :olderThan")
    suspend fun getTrashOlderThan(olderThan: Long): List<VaultItemEntity>

    /**
     * Search over non-secret metadata only. The caller lower-cases and wraps the query in %...%.
     * Secret field values are NEVER searched.
     */
    @Query(
        """
        SELECT DISTINCT vi.* FROM vault_items vi
        LEFT JOIN family_profiles fp ON fp.id = vi.ownerId
        LEFT JOIN vault_item_tags vt ON vt.vaultItemId = vi.id
        LEFT JOIN tags t ON t.id = vt.tagId
        WHERE vi.deletedAt IS NULL AND (
            vi.title LIKE :pattern OR vi.subtitle LIKE :pattern OR vi.institution LIKE :pattern
            OR vi.officialDomain LIKE :pattern OR vi.category LIKE :pattern
            OR fp.displayName LIKE :pattern OR t.name LIKE :pattern
        )
        ORDER BY vi.favorite DESC, vi.title COLLATE NOCASE ASC
        LIMIT 200
        """,
    )
    suspend fun searchMetadata(pattern: String): List<VaultItemEntity>

    // ------------------------------------------------------------ writes

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: VaultItemEntity)

    @Update
    suspend fun update(item: VaultItemEntity)

    @Query("UPDATE vault_items SET favorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: Long)

    @Query("UPDATE vault_items SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    @Query("UPDATE vault_items SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun unmarkDeleted(id: String, now: Long)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deletePermanently(id: String)

    @Query("UPDATE vault_items SET passwordLastChangedAt = :changedAt, updatedAt = :now WHERE id = :id")
    suspend fun setPasswordLastChanged(id: String, changedAt: Long, now: Long)

    // ------------------------------------------------------------ fields

    @Query("SELECT * FROM credential_fields WHERE vaultItemId = :itemId ORDER BY displayOrder ASC")
    suspend fun getFields(itemId: String): List<CredentialFieldEntity>

    @Query("SELECT * FROM credential_fields")
    suspend fun getAllFields(): List<CredentialFieldEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFields(fields: List<CredentialFieldEntity>)

    @Update
    suspend fun updateField(field: CredentialFieldEntity)

    @Query("DELETE FROM credential_fields WHERE vaultItemId = :itemId")
    suspend fun deleteFieldsFor(itemId: String)

    @Query("DELETE FROM credential_fields WHERE id IN (:ids)")
    suspend fun deleteFields(ids: List<String>)

    // ------------------------------------------------------------ history

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHistory(event: CredentialHistoryEntity)

    @Query("SELECT * FROM credential_history WHERE vaultItemId = :itemId ORDER BY occurredAt DESC LIMIT 200")
    fun observeHistory(itemId: String): Flow<List<CredentialHistoryEntity>>

    @Query("SELECT * FROM credential_history WHERE vaultItemId IS NULL ORDER BY occurredAt DESC LIMIT :limit")
    fun observeVaultEvents(limit: Int): Flow<List<CredentialHistoryEntity>>

    @Query("SELECT * FROM credential_history ORDER BY occurredAt ASC")
    suspend fun getAllHistory(): List<CredentialHistoryEntity>

    // ------------------------------------------------------------ composite, atomic operations

    /** Create item + fields + history in one transaction. */
    @Transaction
    suspend fun insertItemWithFields(item: VaultItemEntity, fields: List<CredentialFieldEntity>, history: CredentialHistoryEntity) {
        insert(item)
        if (fields.isNotEmpty()) insertFields(fields)
        insertHistory(history)
    }

    /** Replace the item and its full field set atomically. */
    @Transaction
    suspend fun updateItemWithFields(item: VaultItemEntity, fields: List<CredentialFieldEntity>, history: CredentialHistoryEntity) {
        update(item)
        deleteFieldsFor(item.id)
        if (fields.isNotEmpty()) insertFields(fields)
        insertHistory(history)
    }

    @Transaction
    suspend fun moveToTrash(id: String, now: Long, history: CredentialHistoryEntity) {
        markDeleted(id, now)
        insertHistory(history)
    }

    @Transaction
    suspend fun restoreFromTrash(id: String, now: Long, history: CredentialHistoryEntity) {
        unmarkDeleted(id, now)
        insertHistory(history)
    }
}

data class CategoryCount(val category: String, val count: Int)
