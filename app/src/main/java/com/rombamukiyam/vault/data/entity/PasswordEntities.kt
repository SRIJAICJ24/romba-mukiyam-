package com.rombamukiyam.vault.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Password-history representation for reuse detection. Stores ONLY a keyed digest:
 *   HMAC-SHA256(key = HKDF(VaultKey, "history-hmac"), password)
 * Without the Vault Key the digest reveals nothing; with it, equality means "same password".
 * Never a plain hash, never the password itself. Schema version 2.
 */
@Entity(
    tableName = "password_history",
    foreignKeys = [
        ForeignKey(entity = VaultItemEntity::class, parentColumns = ["id"], childColumns = ["vaultItemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("vaultItemId"), Index("digestHex"), Index("changedAt")],
)
data class PasswordHistoryEntity(
    @PrimaryKey val id: String,
    val vaultItemId: String,
    /** Field the digest belongs to (a bank item can have login / profile / transaction passwords). */
    val fieldId: String,
    val digestHex: String,
    /** True for the currently active password of that field. */
    @ColumnInfo(defaultValue = "1") val current: Boolean,
    val changedAt: Long,
) {
    override fun toString(): String = "PasswordHistory(id=$id)"
}

/** Per-account password rules, entered by the user from the bank/site's published policy. Schema version 2. */
@Entity(
    tableName = "password_policies",
    foreignKeys = [
        ForeignKey(entity = VaultItemEntity::class, parentColumns = ["id"], childColumns = ["vaultItemId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PasswordPolicyEntity(
    @PrimaryKey val vaultItemId: String,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    @ColumnInfo(defaultValue = "0") val requireUppercase: Boolean = false,
    @ColumnInfo(defaultValue = "0") val requireLowercase: Boolean = false,
    @ColumnInfo(defaultValue = "0") val requireDigit: Boolean = false,
    @ColumnInfo(defaultValue = "0") val requireSymbol: Boolean = false,
    val allowedSymbols: String? = null,
    val forbiddenCharacters: String? = null,
    val notes: String? = null,
    /** Where the rule came from (e.g. "Bank website, Change Password page"). */
    val policySource: String? = null,
    val lastVerifiedAt: Long? = null,
    val updatedAt: Long,
)
