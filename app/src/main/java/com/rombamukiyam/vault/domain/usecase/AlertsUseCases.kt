package com.rombamukiyam.vault.domain.usecase

import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.common.DispatcherProvider
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.safeOutcome
import com.rombamukiyam.vault.domain.model.ReminderType
import com.rombamukiyam.vault.domain.repository.ReminderRepository
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.repository.VaultRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

enum class AlertType { PASSWORD_REVIEW, CARD_EXPIRY, DOCUMENT_EXPIRY, BACKUP, CUSTOM }

enum class AlertBucket { OVERDUE, NEXT_7_DAYS, NEXT_30_DAYS, LATER }

data class AlertItem(
    val reminderId: String?,
    val itemId: String?,
    val title: String,
    val ownerName: String?,
    val type: AlertType,
    val dueAt: Long,
    val canMarkDone: Boolean,
) {
    fun bucket(now: Long): AlertBucket {
        val days = (dueAt - now) / 86_400_000L
        return when {
            dueAt <= now -> AlertBucket.OVERDUE
            days < 7 -> AlertBucket.NEXT_7_DAYS
            days < 30 -> AlertBucket.NEXT_30_DAYS
            else -> AlertBucket.LATER
        }
    }
}

data class AlertSummary(val reviewsDue: Int, val expiringSoon: Int, val backupOverdue: Boolean)

/**
 * Alerts come from the reminders table (item reviews and expiries) plus the settings-based backup reminder.
 * Everything here is non-secret: titles, owners and dates only.
 */
class GetUpcomingAlertsUseCase @Inject constructor(
    private val vault: VaultRepository,
    private val reminders: ReminderRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    fun observeAll(): Flow<List<AlertItem>> = combine(vault.observeActive(), reminders.observeAll(), settings.settings) { items, rems, s ->
        val now = clock.nowMillis()
        val byId = items.associateBy { it.id }
        val alerts = rems.mapNotNull { r ->
            val item = r.vaultItemId?.let { byId[it] } ?: return@mapNotNull null // trashed/unknown items are hidden
            val type = when (r.type) {
                ReminderType.PASSWORD_REVIEW, ReminderType.PASSWORD_CHANGE, ReminderType.SECURITY_REVIEW -> AlertType.PASSWORD_REVIEW
                ReminderType.CARD_EXPIRY -> AlertType.CARD_EXPIRY
                ReminderType.DOCUMENT_EXPIRY -> AlertType.DOCUMENT_EXPIRY
                ReminderType.BACKUP_REMINDER -> AlertType.BACKUP
                ReminderType.CUSTOM -> AlertType.CUSTOM
            }
            val enabled = when (type) {
                AlertType.PASSWORD_REVIEW -> s.passwordReviewAlerts
                AlertType.CARD_EXPIRY -> s.cardExpiryAlerts
                AlertType.DOCUMENT_EXPIRY -> s.documentExpiryAlerts
                else -> true
            }
            if (!enabled) return@mapNotNull null
            // Show only within the notify window or overdue; far-future expiries stay quiet until they matter.
            if (r.nextDueDate - r.notifyBeforeDays * 86_400_000L > now + 60L * 86_400_000L) return@mapNotNull null
            AlertItem(r.id, item.id, r.customLabel ?: item.title, item.ownerName, type, r.nextDueDate, canMarkDone = type == AlertType.PASSWORD_REVIEW || type == AlertType.CUSTOM)
        }.toMutableList()
        val last = s.lastBackupAt
        val backupDue = (last ?: now) + s.backupReminderDays * 86_400_000L
        if (last == null || backupDue <= now + 30L * 86_400_000L) {
            alerts += AlertItem(null, null, "backup", null, AlertType.BACKUP, if (last == null) now else backupDue, canMarkDone = false)
        }
        alerts.sortedBy { it.dueAt }
    }

    fun observeSummary(): Flow<AlertSummary> = combine(observeAll(), settings.settings) { alerts, s ->
        val now = clock.nowMillis()
        AlertSummary(
            reviewsDue = alerts.count { it.type == AlertType.PASSWORD_REVIEW && it.dueAt <= now },
            expiringSoon = alerts.count { (it.type == AlertType.CARD_EXPIRY || it.type == AlertType.DOCUMENT_EXPIRY) && it.dueAt <= now + 30L * 86_400_000L },
            backupOverdue = s.lastBackupAt == null || s.lastBackupAt + s.backupReminderDays * 86_400_000L <= now,
        )
    }
}

class MarkReminderDoneUseCase @Inject constructor(private val reminders: ReminderRepository, private val vault: VaultRepository, private val clock: Clock, private val dispatchers: DispatcherProvider) {
    /** For review reminders "done" means the credential was reviewed today. */
    suspend operator fun invoke(alert: AlertItem): Outcome<Unit> = withContext(dispatchers.io) {
        safeOutcome {
            if (alert.type == AlertType.PASSWORD_REVIEW && alert.itemId != null) {
                vault.markReviewed(alert.itemId, clock.nowMillis())
            } else if (alert.reminderId != null) {
                reminders.markDone(alert.reminderId)
            }
        }
    }
}

/**
 * Housekeeping that needs the open vault, executed right after each unlock:
 *  - purge Trash entries beyond the retention period
 *  - republish the non-secret reminder schedule (settings may have changed while locked)
 */
class OnUnlockMaintenanceUseCase @Inject constructor(
    private val vault: VaultRepository,
    private val reminders: ReminderRepository,
    private val settings: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke() = withContext(dispatchers.io) {
        runCatching { vault.purgeTrashOlderThan(settings.current().trashRetentionDays) }
        runCatching { reminders.republish() }
    }
}
