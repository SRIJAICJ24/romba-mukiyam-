package com.rombamukiyam.vault.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "family_profiles",
    indices = [Index("archived"), Index("displayName")],
)
data class FamilyProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    /** One of Relationship enum names: DAD, MOM, ME, SIBLING, JOINT, OTHER. */
    val relationship: String,
    val avatarType: String,
    /** ARGB colour as Long so no float rounding. */
    val avatarColor: Long,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
) {
    override fun toString(): String = "FamilyProfile(id=$id)"
}
