package com.rombamukiyam.vault.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rombamukiyam.vault.data.entity.FamilyProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyProfileDao {
    @Query("SELECT * FROM family_profiles ORDER BY archived ASC, sortOrder ASC, displayName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FamilyProfileEntity>>

    @Query("SELECT * FROM family_profiles WHERE archived = 0 ORDER BY sortOrder ASC, displayName COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<FamilyProfileEntity>>

    @Query("SELECT * FROM family_profiles ORDER BY sortOrder ASC")
    suspend fun getAll(): List<FamilyProfileEntity>

    @Query("SELECT * FROM family_profiles WHERE id = :id")
    suspend fun getById(id: String): FamilyProfileEntity?

    @Query("SELECT COUNT(*) FROM family_profiles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: FamilyProfileEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(profiles: List<FamilyProfileEntity>)

    @Update
    suspend fun update(profile: FamilyProfileEntity)

    @Query("UPDATE family_profiles SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("SELECT COUNT(*) FROM vault_items WHERE ownerId = :profileId")
    suspend fun countItemsOwnedBy(profileId: String): Int
}
