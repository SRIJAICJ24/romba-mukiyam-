package com.rombamukiyam.vault.domain.model

data class FamilyProfile(
    val id: String,
    val displayName: String,
    val relationship: Relationship,
    val avatarType: AvatarType,
    val avatarColor: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean,
    val sortOrder: Int,
) {
    val initials: String get() = displayName.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
    override fun toString(): String = "FamilyProfile(id=$id)"
}

/** A decrypted field value. Instances must not outlive the screen that needs them. */
data class CredentialField(
    val id: String,
    val type: FieldType,
    val label: String,
    val value: String,
    val isSecret: Boolean,
    val isSensitive: Boolean,
    val displayOrder: Int,
) {
    override fun toString(): String = "CredentialField(id=$id, type=$type)"
}

/** Item summary for lists — never contains field values. */
data class VaultItemSummary(
    val id: String,
    val ownerId: String,
    val ownerName: String?,
    val category: VaultCategory,
    val title: String,
    val subtitle: String?,
    val institution: String?,
    val favorite: Boolean,
    val highSecurity: Boolean,
    val updatedAt: Long,
    val deletedAt: Long?,
    val passwordLastChangedAt: Long?,
    val reviewIntervalDays: Int?,
) {
    override fun toString(): String = "VaultItemSummary(id=$id)"
}

/** Full item incl. decrypted fields. */
data class VaultItem(
    val id: String,
    val ownerId: String,
    val category: VaultCategory,
    val title: String,
    val subtitle: String?,
    val institution: String?,
    val officialDomain: String?,
    val officialWebsite: String?,
    val favorite: Boolean,
    val highSecurity: Boolean,
    val notes: String?,
    val passwordLastChangedAt: Long?,
    val reviewIntervalDays: Int?,
    val twoFactorEnabled: Boolean?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val fields: List<CredentialField>,
    val tags: List<String>,
) {
    override fun toString(): String = "VaultItem(id=$id)"
}

/** Input for creating/updating an item. Field ids null => new field. */
data class VaultItemDraft(
    val id: String? = null,
    val ownerId: String,
    val category: VaultCategory,
    val title: String,
    val subtitle: String? = null,
    val institution: String? = null,
    val officialWebsite: String? = null,
    val favorite: Boolean = false,
    val highSecurity: Boolean = category.defaultHighSecurity,
    val notes: String? = null,
    val passwordLastChangedAt: Long? = null,
    val reviewIntervalDays: Int? = null,
    val twoFactorEnabled: Boolean? = null,
    val fields: List<FieldDraft> = emptyList(),
    val tags: List<String> = emptyList(),
) {
    override fun toString(): String = "VaultItemDraft(id=$id)"
}

data class FieldDraft(
    val id: String? = null,
    val type: FieldType,
    val label: String,
    val value: String,
    val isSecret: Boolean = type.secretByDefault,
    val isSensitive: Boolean = type.sensitiveByDefault,
) {
    override fun toString(): String = "FieldDraft(type=$type)"
}

data class HistoryEvent(
    val id: String,
    val vaultItemId: String?,
    val type: HistoryEventType,
    val detail: String?,
    val occurredAt: Long,
)
