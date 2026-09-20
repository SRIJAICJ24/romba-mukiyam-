package com.rombamukiyam.vault.domain.usecase

import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.domain.model.VaultItemSummary
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.repository.VaultRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

enum class AlertType { PASSWORD_REVIEW, CARD_EXPIRY, DOCUMENT_EXPIRY, BACKUP }

enum class AlertBucket { OVERDUE, NEXT_7_DAYS, NEXT_30_DAYS, LATER }

data class AlertItem(
    val itemId: String?,
    val title: String,
    val ownerName: String?,
    val type: AlertType,
    val dueAt: Long,
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
 * Alerts derived from item metadata (review policy) and, from Phase 4, the reminders table.
 * Everything here is non-secret: titles, owners and dates only.
 */
class GetUpcomingAlertsUseCase @Inject constructor(
    private val vault: VaultRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    fun observeAll(): Flow<List<AlertItem>> = combine(vault.observeActive(), settings.settings) { items, s ->
        val now = clock.nowMillis()
        val alerts = mutableListOf<AlertItem>()
        items.forEach { item -> reviewAlert(item)?.let { alerts += it } }
        val last = s.lastBackupAt
        val backupDue = (last ?: 0L) + s.backupReminderDays * 86_400_000L
        if (last == null || backupDue <= now + 30L * 86_400_000L) {
            alerts += AlertItem(null, "backup", null, AlertType.BACKUP, if (last == null) now else backupDue)
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

    private fun reviewAlert(item: VaultItemSummary): AlertItem? {
        val interval = item.reviewIntervalDays ?: return null
        val base = item.passwordLastChangedAt ?: return null
        return AlertItem(item.id, item.title, item.ownerName, AlertType.PASSWORD_REVIEW, base + interval * 86_400_000L)
    }
}
