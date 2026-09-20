package com.rombamukiyam.vault.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rombamukiyam.vault.data.entity.TagEntity
import com.rombamukiyam.vault.data.entity.VaultItemTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT t.* FROM tags t INNER JOIN vault_item_tags vt ON vt.tagId = t.id WHERE vt.vaultItemId = :itemId ORDER BY t.name")
    suspend fun getTagsForItem(itemId: String): List<TagEntity>

    @Query("SELECT * FROM vault_item_tags")
    suspend fun getAllCrossRefs(): List<VaultItemTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<VaultItemTagCrossRef>)

    @Query("DELETE FROM vault_item_tags WHERE vaultItemId = :itemId")
    suspend fun clearTagsForItem(itemId: String)

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM vault_item_tags)")
    suspend fun deleteUnusedTags()
}
