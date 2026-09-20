package com.rombamukiyam.vault.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rombamukiyam.vault.domain.repository.AppSettings
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Jetpack DataStore backed settings. Only the whitelisted, non-secret keys below exist. */
class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val THEME = stringPreferencesKey("theme_mode")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val AUTO_LOCK = intPreferencesKey("auto_lock_seconds")
        val CLIPBOARD_CLEAR = intPreferencesKey("clipboard_clear_seconds")
        val REVEAL_TIMEOUT = intPreferencesKey("reveal_timeout_seconds")
        val REAUTH_HIGH_SECURITY = booleanPreferencesKey("reauth_high_security")
        val MAX_PRIVACY = booleanPreferencesKey("max_privacy_locked")
        val SCREENSHOT_PROTECTION = booleanPreferencesKey("screenshot_protection")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATION_NAMES = booleanPreferencesKey("notification_show_names")
        val REVIEW_ALERTS = booleanPreferencesKey("password_review_alerts")
        val DOC_ALERTS = booleanPreferencesKey("document_expiry_alerts")
        val CARD_ALERTS = booleanPreferencesKey("card_expiry_alerts")
        val BACKUP_REMINDER_DAYS = intPreferencesKey("backup_reminder_days")
        val DEFAULT_REVIEW_DAYS = intPreferencesKey("default_review_days")
        val TRASH_RETENTION = intPreferencesKey("trash_retention_days")
        val ROOT_DISMISSED = booleanPreferencesKey("root_warning_dismissed")
        val LAST_INTEGRITY = longPreferencesKey("last_integrity_check_at")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_VERIFIED = booleanPreferencesKey("last_backup_verified")
    }

    override val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            onboardingComplete = this[Keys.ONBOARDING_COMPLETE] ?: d.onboardingComplete,
            themeMode = this[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            textScale = this[Keys.TEXT_SCALE] ?: d.textScale,
            autoLockSeconds = this[Keys.AUTO_LOCK] ?: d.autoLockSeconds,
            clipboardClearSeconds = this[Keys.CLIPBOARD_CLEAR] ?: d.clipboardClearSeconds,
            revealTimeoutSeconds = this[Keys.REVEAL_TIMEOUT] ?: d.revealTimeoutSeconds,
            requireReauthForHighSecurity = this[Keys.REAUTH_HIGH_SECURITY] ?: d.requireReauthForHighSecurity,
            maximumPrivacyWhenLocked = this[Keys.MAX_PRIVACY] ?: d.maximumPrivacyWhenLocked,
            screenshotProtection = this[Keys.SCREENSHOT_PROTECTION] ?: d.screenshotProtection,
            notificationsEnabled = this[Keys.NOTIFICATIONS] ?: d.notificationsEnabled,
            notificationShowItemNames = this[Keys.NOTIFICATION_NAMES] ?: d.notificationShowItemNames,
            passwordReviewAlerts = this[Keys.REVIEW_ALERTS] ?: d.passwordReviewAlerts,
            documentExpiryAlerts = this[Keys.DOC_ALERTS] ?: d.documentExpiryAlerts,
            cardExpiryAlerts = this[Keys.CARD_ALERTS] ?: d.cardExpiryAlerts,
            backupReminderDays = this[Keys.BACKUP_REMINDER_DAYS] ?: d.backupReminderDays,
            defaultReviewDays = if (contains(Keys.DEFAULT_REVIEW_DAYS)) this[Keys.DEFAULT_REVIEW_DAYS]?.takeIf { it > 0 } else d.defaultReviewDays,
            trashRetentionDays = this[Keys.TRASH_RETENTION] ?: d.trashRetentionDays,
            rootWarningDismissed = this[Keys.ROOT_DISMISSED] ?: d.rootWarningDismissed,
            lastIntegrityCheckAt = this[Keys.LAST_INTEGRITY],
            lastBackupAt = this[Keys.LAST_BACKUP],
            lastBackupVerified = this[Keys.LAST_BACKUP_VERIFIED] ?: d.lastBackupVerified,
        )
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[Keys.ONBOARDING_COMPLETE] = s.onboardingComplete
        this[Keys.THEME] = s.themeMode.name
        this[Keys.TEXT_SCALE] = s.textScale
        this[Keys.AUTO_LOCK] = s.autoLockSeconds
        this[Keys.CLIPBOARD_CLEAR] = s.clipboardClearSeconds
        this[Keys.REVEAL_TIMEOUT] = s.revealTimeoutSeconds
        this[Keys.REAUTH_HIGH_SECURITY] = s.requireReauthForHighSecurity
        this[Keys.MAX_PRIVACY] = s.maximumPrivacyWhenLocked
        this[Keys.SCREENSHOT_PROTECTION] = s.screenshotProtection
        this[Keys.NOTIFICATIONS] = s.notificationsEnabled
        this[Keys.NOTIFICATION_NAMES] = s.notificationShowItemNames
        this[Keys.REVIEW_ALERTS] = s.passwordReviewAlerts
        this[Keys.DOC_ALERTS] = s.documentExpiryAlerts
        this[Keys.CARD_ALERTS] = s.cardExpiryAlerts
        this[Keys.BACKUP_REMINDER_DAYS] = s.backupReminderDays
        this[Keys.DEFAULT_REVIEW_DAYS] = s.defaultReviewDays ?: 0
        this[Keys.TRASH_RETENTION] = s.trashRetentionDays
        this[Keys.ROOT_DISMISSED] = s.rootWarningDismissed
        s.lastIntegrityCheckAt?.let { this[Keys.LAST_INTEGRITY] = it } ?: remove(Keys.LAST_INTEGRITY)
        s.lastBackupAt?.let { this[Keys.LAST_BACKUP] = it } ?: remove(Keys.LAST_BACKUP)
        this[Keys.LAST_BACKUP_VERIFIED] = s.lastBackupVerified
    }
}
