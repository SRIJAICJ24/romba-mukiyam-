package com.rombamukiyam.vault.domain.model

enum class ReminderType { PASSWORD_REVIEW, PASSWORD_CHANGE, CARD_EXPIRY, DOCUMENT_EXPIRY, BACKUP_REMINDER, SECURITY_REVIEW, CUSTOM }

data class Reminder(
    val id: String,
    val vaultItemId: String?,
    val type: ReminderType,
    val enabled: Boolean,
    val lastActionDate: Long?,
    val nextDueDate: Long,
    val intervalDays: Int?,
    val notifyBeforeDays: Int,
    val customLabel: String?,
)

/**
 * The minimal, NON-SECRET view of upcoming reminders that the background worker may read while the vault
 * is locked. Titles are included only when the user opted into "show item names" notifications.
 */
data class ScheduledReminder(val dueAt: Long, val type: ReminderType, val label: String?)
