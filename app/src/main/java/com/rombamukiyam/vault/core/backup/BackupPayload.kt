package com.rombamukiyam.vault.core.backup

import kotlinx.serialization.Serializable

/**
 * Plaintext (pre-encryption) content of a .rmvault backup. Field values are included in clear INSIDE the
 * payload because the whole payload is encrypted with AES-256-GCM under a key derived from the master
 * passphrase; this is what allows a restore onto a device with a different Vault Key.
 *
 * Every list is a straight copy of the corresponding table. Encrypted-at-rest columns are exported decrypted;
 * password-history digests are re-derived on import (they are keyed to the destination Vault Key).
 */
@Serializable
data class BackupPayload(
    val payloadVersion: Int = 1,
    val schemaVersion: Int,
    val createdAt: Long,
    val vaultId: String,
    val appVersion: String,
    val profiles: List<ProfileDto>,
    val items: List<ItemDto>,
    val fields: List<FieldDto>,
    val history: List<HistoryDto>,
    val tags: List<TagDto>,
    val itemTags: List<ItemTagDto>,
    val policies: List<PolicyDto>,
    val reminders: List<ReminderDto>,
    val manifest: Manifest,
) {
    @Serializable
    data class Manifest(val profiles: Int, val items: Int, val fields: Int, val history: Int, val tags: Int, val itemTags: Int, val policies: Int, val reminders: Int) {
        fun matches(p: BackupPayload): Boolean =
            profiles == p.profiles.size && items == p.items.size && fields == p.fields.size && history == p.history.size &&
                tags == p.tags.size && itemTags == p.itemTags.size && policies == p.policies.size && reminders == p.reminders.size
    }

    @Serializable
    data class ProfileDto(val id: String, val displayName: String, val relationship: String, val avatarType: String, val avatarColor: Long, val createdAt: Long, val updatedAt: Long, val archived: Boolean, val sortOrder: Int)

    @Serializable
    data class ItemDto(
        val id: String, val ownerId: String, val category: String, val title: String, val subtitle: String?, val institution: String?,
        val officialDomain: String?, val officialWebsite: String?, val favorite: Boolean, val highSecurity: Boolean, val notes: String?,
        val passwordLastChangedAt: Long?, val reviewIntervalDays: Int?, val twoFactorEnabled: Boolean?, val createdAt: Long, val updatedAt: Long, val deletedAt: Long?,
    )

    @Serializable
    data class FieldDto(val id: String, val vaultItemId: String, val fieldType: String, val label: String, val value: String, val isSecret: Boolean, val isSensitive: Boolean, val displayOrder: Int, val createdAt: Long, val updatedAt: Long)

    @Serializable
    data class HistoryDto(val id: String, val vaultItemId: String?, val eventType: String, val detail: String?, val occurredAt: Long)

    @Serializable
    data class TagDto(val id: String, val name: String, val createdAt: Long)

    @Serializable
    data class ItemTagDto(val vaultItemId: String, val tagId: String)

    @Serializable
    data class PolicyDto(
        val vaultItemId: String, val minLength: Int?, val maxLength: Int?, val requireUppercase: Boolean, val requireLowercase: Boolean,
        val requireDigit: Boolean, val requireSymbol: Boolean, val allowedSymbols: String?, val forbiddenCharacters: String?, val notes: String?,
        val policySource: String?, val lastVerifiedAt: Long?, val updatedAt: Long,
    )

    @Serializable
    data class ReminderDto(val id: String, val vaultItemId: String?, val type: String, val enabled: Boolean, val lastActionDate: Long?, val nextDueDate: Long, val intervalDays: Int?, val notifyBeforeDays: Int, val customLabel: String?, val createdAt: Long, val updatedAt: Long)
}
