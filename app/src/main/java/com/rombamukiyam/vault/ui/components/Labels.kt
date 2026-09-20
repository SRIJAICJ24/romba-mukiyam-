package com.rombamukiyam.vault.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.domain.model.FieldType
import com.rombamukiyam.vault.domain.model.HistoryEventType
import com.rombamukiyam.vault.domain.model.VaultCategory

@Composable
fun categoryLabel(c: VaultCategory): String = stringResource(
    when (c) {
        VaultCategory.BANK -> R.string.cat_bank
        VaultCategory.WEBSITE -> R.string.cat_website
        VaultCategory.APP -> R.string.cat_app
        VaultCategory.EMAIL -> R.string.cat_email
        VaultCategory.CARD -> R.string.cat_card
        VaultCategory.IDENTITY -> R.string.cat_identity
        VaultCategory.DOCUMENT -> R.string.cat_document
        VaultCategory.INSURANCE -> R.string.cat_insurance
        VaultCategory.INVESTMENT -> R.string.cat_investment
        VaultCategory.UTILITY -> R.string.cat_utility
        VaultCategory.SECURE_NOTE -> R.string.cat_secure_note
        VaultCategory.WIFI -> R.string.cat_wifi
        VaultCategory.OTHER -> R.string.cat_other
    },
)

fun categoryIcon(c: VaultCategory): ImageVector = when (c) {
    VaultCategory.BANK -> Icons.Filled.AccountBalance
    VaultCategory.WEBSITE -> Icons.Filled.Language
    VaultCategory.APP -> Icons.Filled.Apps
    VaultCategory.EMAIL -> Icons.Filled.Email
    VaultCategory.CARD -> Icons.Filled.CreditCard
    VaultCategory.IDENTITY -> Icons.Filled.Badge
    VaultCategory.DOCUMENT -> Icons.Filled.Description
    VaultCategory.INSURANCE -> Icons.Filled.HealthAndSafety
    VaultCategory.INVESTMENT -> Icons.Filled.TrendingUp
    VaultCategory.UTILITY -> Icons.Filled.Bolt
    VaultCategory.SECURE_NOTE -> Icons.Filled.Notes
    VaultCategory.WIFI -> Icons.Filled.Wifi
    VaultCategory.OTHER -> Icons.Filled.MoreHoriz
}

@Composable
fun fieldTypeLabel(t: FieldType): String = stringResource(
    when (t) {
        FieldType.USERNAME -> R.string.ft_username
        FieldType.LOGIN_ID -> R.string.ft_login_id
        FieldType.PASSWORD -> R.string.ft_password
        FieldType.PROFILE_PASSWORD -> R.string.ft_profile_password
        FieldType.TRANSACTION_PASSWORD -> R.string.ft_transaction_password
        FieldType.EMAIL -> R.string.ft_email
        FieldType.PHONE -> R.string.ft_phone
        FieldType.CUSTOMER_ID -> R.string.ft_customer_id
        FieldType.CIF -> R.string.ft_cif
        FieldType.CRN -> R.string.ft_crn
        FieldType.ACCOUNT_REFERENCE -> R.string.ft_account_reference
        FieldType.MEMBERSHIP_ID -> R.string.ft_membership_id
        FieldType.SECURITY_QUESTION -> R.string.ft_security_question
        FieldType.SECURITY_ANSWER -> R.string.ft_security_answer
        FieldType.RECOVERY_EMAIL -> R.string.ft_recovery_email
        FieldType.RECOVERY_PHONE -> R.string.ft_recovery_phone
        FieldType.RECOVERY_CODE -> R.string.ft_recovery_code
        FieldType.RECOVERY_NOTES -> R.string.ft_recovery_notes
        FieldType.URL -> R.string.ft_url
        FieldType.DATE -> R.string.ft_date
        FieldType.EXPIRY_DATE -> R.string.ft_expiry_date
        FieldType.ISSUE_DATE -> R.string.ft_issue_date
        FieldType.CARD_LAST4 -> R.string.ft_card_last4
        FieldType.CARD_TYPE -> R.string.ft_card_type
        FieldType.REFERENCE_NUMBER -> R.string.ft_reference_number
        FieldType.ISSUER -> R.string.ft_issuer
        FieldType.WIFI_PASSWORD -> R.string.ft_wifi_password
        FieldType.SECURE_NOTE -> R.string.ft_secure_note
        FieldType.TEXT -> R.string.ft_text
        FieldType.CUSTOM -> R.string.ft_custom
        FieldType.CUSTOM_SECRET -> R.string.ft_custom_secret
    },
)

/** Resolves a template label key (CategoryTemplates) to a localised default label. */
@Composable
fun templateLabel(key: String): String = stringResource(
    when (key) {
        "customer_id" -> R.string.ft_customer_id
        "username_login_id" -> R.string.tl_username_login_id
        "cif_crn" -> R.string.tl_cif_crn
        "login_password" -> R.string.tl_login_password
        "profile_password" -> R.string.ft_profile_password
        "transaction_password" -> R.string.ft_transaction_password
        "registered_mobile" -> R.string.tl_registered_mobile
        "registered_email" -> R.string.tl_registered_email
        "recovery_instructions" -> R.string.tl_recovery_instructions
        "username" -> R.string.ft_username
        "email" -> R.string.ft_email
        "password" -> R.string.ft_password
        "recovery_email" -> R.string.ft_recovery_email
        "recovery_phone" -> R.string.ft_recovery_phone
        "recovery_notes" -> R.string.ft_recovery_notes
        "recovery_method" -> R.string.tl_recovery_method
        "recovery_codes" -> R.string.tl_recovery_codes
        "card_type" -> R.string.ft_card_type
        "last4" -> R.string.ft_card_last4
        "expiry" -> R.string.tl_expiry
        "reference_number" -> R.string.ft_reference_number
        "issuer" -> R.string.ft_issuer
        "issue_date" -> R.string.ft_issue_date
        "expiry_date" -> R.string.ft_expiry_date
        "account_reference" -> R.string.ft_account_reference
        "consumer_number" -> R.string.tl_consumer_number
        "secure_note" -> R.string.ft_secure_note
        "network_name" -> R.string.tl_network_name
        "wifi_password" -> R.string.ft_wifi_password
        else -> R.string.ft_custom
    },
)

@Composable
fun historyEventLabel(t: HistoryEventType): String = stringResource(
    when (t) {
        HistoryEventType.CREDENTIAL_CREATED -> R.string.ev_created
        HistoryEventType.CREDENTIAL_UPDATED -> R.string.ev_updated
        HistoryEventType.PASSWORD_CHANGED -> R.string.ev_password_changed
        HistoryEventType.CREDENTIAL_REVIEWED -> R.string.ev_reviewed
        HistoryEventType.REMINDER_UPDATED -> R.string.ev_reminder_updated
        HistoryEventType.MOVED_TO_TRASH -> R.string.ev_trashed
        HistoryEventType.RESTORED -> R.string.ev_restored
        HistoryEventType.PERMANENTLY_DELETED -> R.string.ev_deleted
        HistoryEventType.BACKUP_CREATED -> R.string.ev_backup_created
        HistoryEventType.BACKUP_VERIFIED -> R.string.ev_backup_verified
        HistoryEventType.BACKUP_RESTORED -> R.string.ev_backup_restored
        HistoryEventType.BIOMETRIC_ENABLED -> R.string.ev_bio_enabled
        HistoryEventType.BIOMETRIC_DISABLED -> R.string.ev_bio_disabled
        HistoryEventType.MASTER_PASSPHRASE_CHANGED -> R.string.ev_passphrase_changed
        HistoryEventType.RECOVERY_KEY_REGENERATED -> R.string.ev_recovery_regenerated
        HistoryEventType.VAULT_CREATED -> R.string.ev_vault_created
        HistoryEventType.INTEGRITY_CHECK -> R.string.ev_integrity_check
    },
)
