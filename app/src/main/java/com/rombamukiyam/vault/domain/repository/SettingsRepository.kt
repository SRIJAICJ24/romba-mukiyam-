package com.rombamukiyam.vault.domain.repository

import com.rombamukiyam.vault.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Non-secret user preferences only. Secrets NEVER go through this interface. */
data class AppSettings(
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textScale: Float = 1.0f,
    val autoLockSeconds: Int = 30,
    val clipboardClearSeconds: Int = 30,
    val revealTimeoutSeconds: Int = 15,
    val requireReauthForHighSecurity: Boolean = true,
    val maximumPrivacyWhenLocked: Boolean = true,
    val screenshotProtection: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val notificationShowItemNames: Boolean = false,
    val passwordReviewAlerts: Boolean = true,
    val documentExpiryAlerts: Boolean = true,
    val cardExpiryAlerts: Boolean = true,
    val backupReminderDays: Int = 30,
    val defaultReviewDays: Int? = 180,
    val trashRetentionDays: Int = 30,
    val rootWarningDismissed: Boolean = false,
    val lastIntegrityCheckAt: Long? = null,
    val lastBackupAt: Long? = null,
    val lastBackupVerified: Boolean = false,
) {
    companion object {
        val AUTO_LOCK_OPTIONS = listOf(0, 15, 30, 60, 300)
        val CLIPBOARD_OPTIONS = listOf(15, 30, 60)
        val REVEAL_OPTIONS = listOf(10, 15, 30)
        val TRASH_RETENTION_OPTIONS = listOf(7, 30, 90)
        val BACKUP_REMINDER_OPTIONS = listOf(7, 14, 30, 60, 90)
    }
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
