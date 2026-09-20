package com.rombamukiyam.vault.data.mapper

import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.data.entity.CredentialFieldEntity
import com.rombamukiyam.vault.data.entity.CredentialHistoryEntity
import com.rombamukiyam.vault.data.entity.FamilyProfileEntity
import com.rombamukiyam.vault.data.entity.VaultItemEntity
import com.rombamukiyam.vault.domain.model.AvatarType
import com.rombamukiyam.vault.domain.model.CredentialField
import com.rombamukiyam.vault.domain.model.FamilyProfile
import com.rombamukiyam.vault.domain.model.FieldType
import com.rombamukiyam.vault.domain.model.HistoryEvent
import com.rombamukiyam.vault.domain.model.HistoryEventType
import com.rombamukiyam.vault.domain.model.Relationship
import com.rombamukiyam.vault.domain.model.VaultCategory
import com.rombamukiyam.vault.domain.model.VaultItem
import com.rombamukiyam.vault.domain.model.VaultItemSummary

fun FamilyProfileEntity.toDomain() = FamilyProfile(
    id = id,
    displayName = displayName,
    relationship = runCatching { Relationship.valueOf(relationship) }.getOrDefault(Relationship.OTHER),
    avatarType = runCatching { AvatarType.valueOf(avatarType) }.getOrDefault(AvatarType.INITIALS),
    avatarColor = avatarColor,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = archived,
    sortOrder = sortOrder,
)

fun FamilyProfile.toEntity() = FamilyProfileEntity(
    id = id,
    displayName = displayName,
    relationship = relationship.name,
    avatarType = avatarType.name,
    avatarColor = avatarColor,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = archived,
    sortOrder = sortOrder,
)

fun VaultItemEntity.toSummary(ownerName: String?) = VaultItemSummary(
    id = id,
    ownerId = ownerId,
    ownerName = ownerName,
    category = VaultCategory.fromName(category),
    title = title,
    subtitle = subtitle,
    institution = institution,
    favorite = favorite,
    highSecurity = highSecurity,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    passwordLastChangedAt = passwordLastChangedAt,
    reviewIntervalDays = reviewIntervalDays,
)

fun CredentialFieldEntity.toDomain(session: VaultSession) = CredentialField(
    id = id,
    type = FieldType.fromName(fieldType),
    label = label,
    value = session.decryptField(encryptedValue, id),
    isSecret = isSecret,
    isSensitive = isSensitive,
    displayOrder = displayOrder,
)

fun VaultItemEntity.toDomain(fields: List<CredentialField>, tags: List<String>) = VaultItem(
    id = id,
    ownerId = ownerId,
    category = VaultCategory.fromName(category),
    title = title,
    subtitle = subtitle,
    institution = institution,
    officialDomain = officialDomain,
    officialWebsite = officialWebsite,
    favorite = favorite,
    highSecurity = highSecurity,
    notes = notes,
    passwordLastChangedAt = passwordLastChangedAt,
    reviewIntervalDays = reviewIntervalDays,
    twoFactorEnabled = twoFactorEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    fields = fields.sortedBy { it.displayOrder },
    tags = tags,
)

fun CredentialHistoryEntity.toDomain() = HistoryEvent(
    id = id,
    vaultItemId = vaultItemId,
    type = runCatching { HistoryEventType.valueOf(eventType) }.getOrDefault(HistoryEventType.CREDENTIAL_UPDATED),
    detail = detail,
    occurredAt = occurredAt,
)

/** Extracts a bare host from a URL for display ("onlinesbi.sbi"). */
fun domainOf(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val trimmed = url.trim()
    val withoutScheme = trimmed.substringAfter("://", trimmed)
    val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    return host.removePrefix("www.").lowercase().ifBlank { null }
}
