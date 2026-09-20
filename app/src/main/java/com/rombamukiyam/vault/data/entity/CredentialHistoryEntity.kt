package com.rombamukiyam.vault.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Event log for an item. Events only — never old/new secret values.
 * vaultItemId is nullable so vault-wide events (backup created, biometric enabled) can be recorded too.
 */
@Entity(
    tableName = "credential_history",
    foreignKeys = [
        ForeignKey(
            entity = VaultItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaultItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("vaultItemId"), Index("occurredAt"), Index("eventType")],
)
data class CredentialHistoryEntity(
    @PrimaryKey val id: String,
    val vaultItemId: String?,
    /** HistoryEventType enum name. */
    val eventType: String,
    /** Short, non-secret detail (e.g. "review interval 90 -> 180"). */
    val detail: String? = null,
    val occurredAt: Long,
) {
    override fun toString(): String = "HistoryEvent(id=$id, type=$eventType)"
}
