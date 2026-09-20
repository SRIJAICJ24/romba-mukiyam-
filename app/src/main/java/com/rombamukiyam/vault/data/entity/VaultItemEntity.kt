package com.rombamukiyam.vault.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Aggregate root for one credential / document / note. Non-secret metadata only; secret values live in
 * [CredentialFieldEntity] and are additionally AES-GCM encrypted inside the SQLCipher database.
 *
 * ownerId -> family_profiles.id uses RESTRICT: a profile can never be deleted while it owns items.
 */
@Entity(
    tableName = "vault_items",
    foreignKeys = [
        ForeignKey(
            entity = FamilyProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("ownerId"), Index("category"), Index("title"), Index("favorite"),
        Index("updatedAt"), Index("deletedAt"), Index("institution"),
    ],
)
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    /** VaultCategory enum name. New categories need no schema change. */
    val category: String,
    val title: String,
    val subtitle: String? = null,
    val institution: String? = null,
    val officialDomain: String? = null,
    val officialWebsite: String? = null,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val highSecurity: Boolean = false,
    /** Free-text notes are non-secret by design; secret notes go into a SECURE_NOTE field. */
    val notes: String? = null,
    /** Epoch millis when the primary password was last changed (null = unknown). */
    val passwordLastChangedAt: Long? = null,
    /** Review interval in days; null = no scheduled review. */
    val reviewIntervalDays: Int? = null,
    /** Whether the user reports 2FA enabled for this account (null = unknown). */
    val twoFactorEnabled: Boolean? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** Non-null = in Trash. */
    val deletedAt: Long? = null,
) {
    override fun toString(): String = "VaultItem(id=$id, category=$category)"
}
