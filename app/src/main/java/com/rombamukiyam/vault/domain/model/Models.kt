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

/** Per-account password rules (entered by the user from the service's published policy). */
data class PasswordPolicy(
    val itemId: String,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val requireUppercase: Boolean = false,
    val requireLowercase: Boolean = false,
    val requireDigit: Boolean = false,
    val requireSymbol: Boolean = false,
    val allowedSymbols: String? = null,
    val forbiddenCharacters: String? = null,
    val notes: String? = null,
    val policySource: String? = null,
    val lastVerifiedAt: Long? = null,
) {
    /** Returns violation keys; empty means compliant. */
    fun violations(password: String): List<String> = buildList {
        minLength?.let { if (password.length < it) add("min_length") }
        maxLength?.let { if (password.length > it) add("max_length") }
        if (requireUppercase && password.none { it.isUpperCase() }) add("uppercase")
        if (requireLowercase && password.none { it.isLowerCase() }) add("lowercase")
        if (requireDigit && password.none { it.isDigit() }) add("digit")
        if (requireSymbol && password.none { !it.isLetterOrDigit() }) add("symbol")
        allowedSymbols?.takeIf { it.isNotBlank() }?.let { allowed ->
            if (password.any { !it.isLetterOrDigit() && it !in allowed }) add("disallowed_symbol")
        }
        forbiddenCharacters?.takeIf { it.isNotEmpty() }?.let { forbidden ->
            if (password.any { it in forbidden }) add("forbidden_char")
        }
    }
}

/** Current password digest for reuse detection (never the password). */
data class PasswordDigest(val itemId: String, val fieldId: String, val digestHex: String, val changedAt: Long)
