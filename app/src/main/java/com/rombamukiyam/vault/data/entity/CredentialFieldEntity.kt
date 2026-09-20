package com.rombamukiyam.vault.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One dynamic field of a vault item (username, customer id, password, ...).
 *
 * Values are stored in [encryptedValue] as AES-256-GCM (nonce || ciphertext || tag) under the field key
 * derived from the Vault Key — defence in depth on top of SQLCipher. Non-secret fields use the same
 * container so the schema never reveals which fields are secrets by storage shape alone.
 * Nothing in this table is ever indexed by value.
 */
@Entity(
    tableName = "credential_fields",
    foreignKeys = [
        ForeignKey(
            entity = VaultItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vaultItemId"), Index(value = ["vaultItemId", "displayOrder"])],
)
data class CredentialFieldEntity(
    @PrimaryKey val id: String,
    val vaultItemId: String,
    /** FieldType enum name. */
    val fieldType: String,
    val label: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val encryptedValue: ByteArray,
    /** Secret: masked by default, reveal requires an explicit action (and auth for high-security items). */
    @ColumnInfo(defaultValue = "0") val isSecret: Boolean,
    /** Sensitive but not secret (e.g. customer id): shown, but excluded from notifications and logs. */
    @ColumnInfo(defaultValue = "0") val isSensitive: Boolean,
    @ColumnInfo(defaultValue = "0") val displayOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    override fun toString(): String = "CredentialField(id=$id, type=$fieldType)"

    override fun equals(other: Any?): Boolean =
        other is CredentialFieldEntity && other.id == id && other.vaultItemId == vaultItemId &&
            other.fieldType == fieldType && other.label == label && other.encryptedValue.contentEquals(encryptedValue) &&
            other.isSecret == isSecret && other.isSensitive == isSensitive && other.displayOrder == displayOrder &&
            other.createdAt == createdAt && other.updatedAt == updatedAt

    override fun hashCode(): Int = id.hashCode()
}
