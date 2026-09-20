package com.rombamukiyam.vault.domain.model

enum class Relationship { DAD, MOM, ME, SIBLING, JOINT, OTHER }

enum class AvatarType { INITIALS, ICON_MAN, ICON_WOMAN, ICON_FAMILY, ICON_STAR }

/** Categories are stored by name; adding one here needs no schema change. */
enum class VaultCategory(val defaultHighSecurity: Boolean = false) {
    BANK(defaultHighSecurity = true),
    WEBSITE,
    APP,
    EMAIL,
    CARD(defaultHighSecurity = true),
    IDENTITY,
    DOCUMENT,
    INSURANCE,
    INVESTMENT(defaultHighSecurity = true),
    UTILITY,
    SECURE_NOTE(defaultHighSecurity = true),
    WIFI,
    OTHER,
    ;

    companion object {
        fun fromName(name: String): VaultCategory = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * Dynamic field types. [secretByDefault] controls masking; [sensitiveByDefault] excludes the value from
 * notifications/logs while still showing it in the UI.
 */
enum class FieldType(val secretByDefault: Boolean, val sensitiveByDefault: Boolean) {
    USERNAME(false, true),
    LOGIN_ID(false, true),
    PASSWORD(true, true),
    PROFILE_PASSWORD(true, true),
    TRANSACTION_PASSWORD(true, true),
    EMAIL(false, true),
    PHONE(false, true),
    CUSTOMER_ID(false, true),
    CIF(false, true),
    CRN(false, true),
    ACCOUNT_REFERENCE(false, true),
    MEMBERSHIP_ID(false, true),
    SECURITY_QUESTION(false, false),
    SECURITY_ANSWER(true, true),
    RECOVERY_EMAIL(false, true),
    RECOVERY_PHONE(false, true),
    RECOVERY_CODE(true, true),
    RECOVERY_NOTES(true, true),
    URL(false, false),
    DATE(false, false),
    EXPIRY_DATE(false, false),
    ISSUE_DATE(false, false),
    CARD_LAST4(false, true),
    CARD_TYPE(false, false),
    REFERENCE_NUMBER(false, true),
    ISSUER(false, false),
    WIFI_PASSWORD(true, true),
    SECURE_NOTE(true, true),
    TEXT(false, false),
    CUSTOM(false, false),
    CUSTOM_SECRET(true, true),
    ;

    companion object {
        fun fromName(name: String): FieldType = entries.firstOrNull { it.name == name } ?: CUSTOM
    }
}

enum class HistoryEventType {
    CREDENTIAL_CREATED,
    CREDENTIAL_UPDATED,
    PASSWORD_CHANGED,
    CREDENTIAL_REVIEWED,
    REMINDER_UPDATED,
    MOVED_TO_TRASH,
    RESTORED,
    PERMANENTLY_DELETED,
    BACKUP_CREATED,
    BACKUP_VERIFIED,
    BACKUP_RESTORED,
    BIOMETRIC_ENABLED,
    BIOMETRIC_DISABLED,
    MASTER_PASSPHRASE_CHANGED,
    RECOVERY_KEY_REGENERATED,
    VAULT_CREATED,
    INTEGRITY_CHECK,
}

enum class ReviewPolicy(val days: Int?) {
    NONE(null), DAYS_30(30), DAYS_60(60), DAYS_90(90), DAYS_180(180), DAYS_365(365), CUSTOM(null);

    companion object {
        fun fromDays(days: Int?): ReviewPolicy = when (days) {
            null -> NONE
            30 -> DAYS_30
            60 -> DAYS_60
            90 -> DAYS_90
            180 -> DAYS_180
            365 -> DAYS_365
            else -> CUSTOM
        }
    }
}
