package com.rombamukiyam.vault.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rombamukiyam.vault.data.entity.PasswordHistoryEntity
import com.rombamukiyam.vault.data.entity.PasswordPolicyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {
    // ---- history digests
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHistory(entry: PasswordHistoryEntity)

    @Query("UPDATE password_history SET current = 0 WHERE fieldId = :fieldId AND current = 1")
    suspend fun retireCurrent(fieldId: String)

    @Query("SELECT * FROM password_history WHERE current = 1")
    suspend fun getAllCurrent(): List<PasswordHistoryEntity>

    @Query("SELECT * FROM password_history WHERE current = 1")
    fun observeAllCurrent(): Flow<List<PasswordHistoryEntity>>

    @Query("SELECT * FROM password_history WHERE vaultItemId = :itemId ORDER BY changedAt DESC")
    suspend fun getHistoryFor(itemId: String): List<PasswordHistoryEntity>

    @Query("SELECT * FROM password_history")
    suspend fun getAllHistory(): List<PasswordHistoryEntity>

    @Query("SELECT COUNT(*) FROM password_history WHERE vaultItemId = :itemId AND fieldId = :fieldId AND digestHex = :digestHex")
    suspend fun countPreviousUses(itemId: String, fieldId: String, digestHex: String): Int

    // ---- policies
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPolicy(policy: PasswordPolicyEntity)

    @Query("SELECT * FROM password_policies WHERE vaultItemId = :itemId")
    suspend fun getPolicy(itemId: String): PasswordPolicyEntity?

    @Query("SELECT * FROM password_policies WHERE vaultItemId = :itemId")
    fun observePolicy(itemId: String): Flow<PasswordPolicyEntity?>

    @Query("SELECT * FROM password_policies")
    suspend fun getAllPolicies(): List<PasswordPolicyEntity>

    @Query("DELETE FROM password_policies WHERE vaultItemId = :itemId")
    suspend fun deletePolicy(itemId: String)
}
