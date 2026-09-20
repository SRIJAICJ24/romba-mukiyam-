package com.rombamukiyam.vault.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "vault_item_tags",
    primaryKeys = ["vaultItemId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = VaultItemEntity::class, parentColumns = ["id"], childColumns = ["vaultItemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class VaultItemTagCrossRef(
    val vaultItemId: String,
    val tagId: String,
)
