package com.rombamukiyam.vault.domain.model

/** A suggested field in a template. Every field is optional for the user. */
data class TemplateField(val type: FieldType, val labelKey: String)

/**
 * Category templates are pure data: adding a category or field here never touches the database schema.
 * Label keys are resolved to localised strings in the UI layer (see FieldLabels).
 */
object CategoryTemplates {

    fun fieldsFor(category: VaultCategory): List<TemplateField> = when (category) {
        VaultCategory.BANK -> listOf(
            TemplateField(FieldType.CUSTOMER_ID, "customer_id"),
            TemplateField(FieldType.USERNAME, "username_login_id"),
            TemplateField(FieldType.CIF, "cif_crn"),
            TemplateField(FieldType.PASSWORD, "login_password"),
            TemplateField(FieldType.PROFILE_PASSWORD, "profile_password"),
            TemplateField(FieldType.TRANSACTION_PASSWORD, "transaction_password"),
            TemplateField(FieldType.PHONE, "registered_mobile"),
            TemplateField(FieldType.EMAIL, "registered_email"),
            TemplateField(FieldType.RECOVERY_NOTES, "recovery_instructions"),
        )
        VaultCategory.WEBSITE, VaultCategory.APP -> listOf(
            TemplateField(FieldType.USERNAME, "username"),
            TemplateField(FieldType.EMAIL, "email"),
            TemplateField(FieldType.PASSWORD, "password"),
            TemplateField(FieldType.RECOVERY_EMAIL, "recovery_email"),
            TemplateField(FieldType.RECOVERY_PHONE, "recovery_phone"),
            TemplateField(FieldType.RECOVERY_NOTES, "recovery_notes"),
        )
        VaultCategory.EMAIL -> listOf(
            TemplateField(FieldType.EMAIL, "email"),
            TemplateField(FieldType.PASSWORD, "password"),
            TemplateField(FieldType.RECOVERY_EMAIL, "recovery_email"),
            TemplateField(FieldType.RECOVERY_PHONE, "recovery_phone"),
            TemplateField(FieldType.TEXT, "recovery_method"),
            TemplateField(FieldType.RECOVERY_CODE, "recovery_codes"),
        )
        VaultCategory.CARD -> listOf(
            TemplateField(FieldType.CARD_TYPE, "card_type"),
            TemplateField(FieldType.CARD_LAST4, "last4"),
            TemplateField(FieldType.EXPIRY_DATE, "expiry"),
            TemplateField(FieldType.PHONE, "registered_mobile"),
        )
        VaultCategory.IDENTITY, VaultCategory.DOCUMENT, VaultCategory.INSURANCE -> listOf(
            TemplateField(FieldType.REFERENCE_NUMBER, "reference_number"),
            TemplateField(FieldType.ISSUER, "issuer"),
            TemplateField(FieldType.ISSUE_DATE, "issue_date"),
            TemplateField(FieldType.EXPIRY_DATE, "expiry_date"),
        )
        VaultCategory.INVESTMENT -> listOf(
            TemplateField(FieldType.ACCOUNT_REFERENCE, "account_reference"),
            TemplateField(FieldType.USERNAME, "username_login_id"),
            TemplateField(FieldType.PASSWORD, "login_password"),
            TemplateField(FieldType.PHONE, "registered_mobile"),
            TemplateField(FieldType.EMAIL, "registered_email"),
        )
        VaultCategory.UTILITY -> listOf(
            TemplateField(FieldType.ACCOUNT_REFERENCE, "consumer_number"),
            TemplateField(FieldType.USERNAME, "username"),
            TemplateField(FieldType.PASSWORD, "password"),
            TemplateField(FieldType.PHONE, "registered_mobile"),
        )
        VaultCategory.SECURE_NOTE -> listOf(TemplateField(FieldType.SECURE_NOTE, "secure_note"))
        VaultCategory.WIFI -> listOf(
            TemplateField(FieldType.TEXT, "network_name"),
            TemplateField(FieldType.WIFI_PASSWORD, "wifi_password"),
        )
        VaultCategory.OTHER -> listOf(
            TemplateField(FieldType.USERNAME, "username"),
            TemplateField(FieldType.PASSWORD, "password"),
        )
    }

    /** Whether the category tracks a "primary" password whose review/rotation matters. */
    fun hasPrimaryPassword(category: VaultCategory): Boolean = when (category) {
        VaultCategory.CARD, VaultCategory.IDENTITY, VaultCategory.DOCUMENT, VaultCategory.INSURANCE, VaultCategory.SECURE_NOTE -> false
        else -> true
    }

    /** Field types that behave as "the" password for review/health purposes. */
    val primaryPasswordTypes = setOf(FieldType.PASSWORD, FieldType.WIFI_PASSWORD)

    /**
     * Values that must never be stored: OTP, PINs, CVV. We match on the label the user types.
     * The check is on words, so "Profile Password" is fine but "ATM PIN" / "CVV" / "OTP" are refused.
     */
    private val disallowedTokens = listOf("otp", "cvv", "cvc", "pin", "upi pin", "atm pin", "mpin", "one time password")

    fun isDisallowedLabel(label: String): Boolean {
        val words = label.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val joined = words.joinToString(" ")
        return disallowedTokens.any { token -> token.split(' ').all { it in words } && (token.contains(' ') || words.contains(token)) } ||
            joined.contains("one time password")
    }
}
