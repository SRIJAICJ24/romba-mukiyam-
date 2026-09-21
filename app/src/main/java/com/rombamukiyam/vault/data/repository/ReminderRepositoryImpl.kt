package com.rombamukiyam.vault.data.repository

import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.security.ReminderSchedulePublisher
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.data.entity.ReminderEntity
import com.rombamukiyam.vault.domain.model.FieldDraft
import com.rombamukiyam.vault.domain.model.FieldType
import com.rombamukiyam.vault.domain.model.Reminder
import com.rombamukiyam.vault.domain.model.ReminderType
import com.rombamukiyam.vault.domain.model.ScheduledReminder
import com.rombamukiyam.vault.domain.model.VaultCategory
import com.rombamukiyam.vault.domain.repository.ReminderRepository
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderRepositoryImpl(
    private val session: VaultSession,
    private val settings: SettingsRepository,
    private val publisher: ReminderSchedulePublisher,
    private val clock: Clock,
) : ReminderRepository {

    private val dao get() = session.database().reminderDao()

    override fun observeAll(): Flow<List<Reminder>> = session.state.flatMapLatest {
        if (session.isUnlocked) dao.observeEnabled().map { l -> l.map { it.toDomain() } } else flowOf(emptyList())
    }

    override suspend fun forItem(itemId: String): List<Reminder> = dao.getForItem(itemId).map { it.toDomain() }

    override suspend fun upsert(reminder: Reminder) {
        val now = clock.nowMillis()
        val existing = reminder.vaultItemId?.let { dao.get(it, reminder.type.name) }
        dao.upsert(
            ReminderEntity(
                id = existing?.id ?: reminder.id.ifBlank { UUID.randomUUID().toString() },
                vaultItemId = reminder.vaultItemId,
                type = reminder.type.name,
                enabled = reminder.enabled,
                lastActionDate = reminder.lastActionDate,
                nextDueDate = reminder.nextDueDate,
                intervalDays = reminder.intervalDays,
                notifyBeforeDays = reminder.notifyBeforeDays,
                customLabel = reminder.customLabel,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        republish()
    }

    override suspend fun delete(itemId: String, type: ReminderType) {
        dao.delete(itemId, type.name)
        republish()
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled, clock.nowMillis())
        republish()
    }

    override suspend fun markDone(id: String) {
        val all = dao.getAll()
        val r = all.firstOrNull { it.id == id } ?: return
        val now = clock.nowMillis()
        val interval = r.intervalDays
        if (interval == null) dao.setEnabled(id, false, now) else dao.reschedule(id, now + interval * DAY, now)
        republish()
    }

    /**
     * Derives item reminders from the saved draft: review policy -> PASSWORD_REVIEW, expiry date fields ->
     * CARD_EXPIRY / DOCUMENT_EXPIRY. Called inside the item save transaction by the vault repository.
     */
    override suspend fun syncForItem(itemId: String, category: VaultCategory, passwordLastChangedAt: Long?, reviewIntervalDays: Int?, fields: List<FieldDraft>) {
        val now = clock.nowMillis()
        // Review reminder
        if (reviewIntervalDays != null && passwordLastChangedAt != null) {
            val existing = dao.get(itemId, ReminderType.PASSWORD_REVIEW.name)
            dao.upsert(
                ReminderEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(), vaultItemId = itemId, type = ReminderType.PASSWORD_REVIEW.name,
                    enabled = existing?.enabled ?: true, lastActionDate = passwordLastChangedAt,
                    nextDueDate = passwordLastChangedAt + reviewIntervalDays * DAY, intervalDays = reviewIntervalDays,
                    notifyBeforeDays = existing?.notifyBeforeDays ?: 7, customLabel = null,
                    createdAt = existing?.createdAt ?: now, updatedAt = now,
                ),
            )
        } else {
            dao.delete(itemId, ReminderType.PASSWORD_REVIEW.name)
        }
        // Expiry reminder
        val expiry = fields.firstOrNull { it.type == FieldType.EXPIRY_DATE }?.value?.let { parseExpiry(it) }
        val expiryType = if (category == VaultCategory.CARD) ReminderType.CARD_EXPIRY else ReminderType.DOCUMENT_EXPIRY
        if (expiry != null) {
            val existing = dao.get(itemId, expiryType.name)
            dao.delete(itemId, (if (expiryType == ReminderType.CARD_EXPIRY) ReminderType.DOCUMENT_EXPIRY else ReminderType.CARD_EXPIRY).name)
            dao.upsert(
                ReminderEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(), vaultItemId = itemId, type = expiryType.name,
                    enabled = existing?.enabled ?: true, lastActionDate = null, nextDueDate = expiry, intervalDays = null,
                    notifyBeforeDays = existing?.notifyBeforeDays ?: 30, customLabel = null,
                    createdAt = existing?.createdAt ?: now, updatedAt = now,
                ),
            )
        } else {
            dao.delete(itemId, ReminderType.CARD_EXPIRY.name)
            dao.delete(itemId, ReminderType.DOCUMENT_EXPIRY.name)
        }
    }

    /** Rewrites the non-secret schedule file from the current reminders + settings. Safe to call often. */
    override suspend fun republish() {
        if (!session.isUnlocked) return
        val s = settings.settings.first()
        val items = session.database().vaultItemDao().getAllActive().associateBy { it.id }
        val entries = dao.getAll().filter { it.enabled && (it.vaultItemId == null || it.vaultItemId in items) }.mapNotNull { r ->
            val type = runCatching { ReminderType.valueOf(r.type) }.getOrNull() ?: return@mapNotNull null
            if (!enabledInSettings(type, s)) return@mapNotNull null
            ScheduledReminder(r.nextDueDate - r.notifyBeforeDays * DAY, type, r.vaultItemId?.let { items[it]?.title })
        }.toMutableList()
        // Backup reminder lives in settings, not the DB, so it is always available.
        val last = s.lastBackupAt
        entries += ScheduledReminder((last ?: clock.nowMillis()) + s.backupReminderDays * DAY, ReminderType.BACKUP_REMINDER, null)
        publisher.publish(entries, includeLabels = s.notificationShowItemNames, now = clock.nowMillis())
    }

    private fun enabledInSettings(type: ReminderType, s: com.rombamukiyam.vault.domain.repository.AppSettings): Boolean = when (type) {
        ReminderType.PASSWORD_REVIEW, ReminderType.PASSWORD_CHANGE, ReminderType.SECURITY_REVIEW -> s.passwordReviewAlerts
        ReminderType.CARD_EXPIRY -> s.cardExpiryAlerts
        ReminderType.DOCUMENT_EXPIRY -> s.documentExpiryAlerts
        ReminderType.BACKUP_REMINDER, ReminderType.CUSTOM -> true
    }

    private fun ReminderEntity.toDomain() = Reminder(
        id = id, vaultItemId = vaultItemId, type = runCatching { ReminderType.valueOf(type) }.getOrDefault(ReminderType.CUSTOM),
        enabled = enabled, lastActionDate = lastActionDate, nextDueDate = nextDueDate, intervalDays = intervalDays,
        notifyBeforeDays = notifyBeforeDays, customLabel = customLabel,
    )

    companion object {
        private const val DAY = 86_400_000L
        private val zone: ZoneId get() = ZoneId.systemDefault()

        /**
         * Accepts what people actually type for expiry: "MM/YY", "MM/YYYY", "YYYY-MM", "YYYY-MM-DD", "DD/MM/YYYY".
         * Month-only formats expire at the end of that month.
         */
        fun parseExpiry(raw: String): Long? {
            val v = raw.trim()
            if (v.isEmpty()) return null
            runCatching { return LocalDate.parse(v).atStartOfDay(zone).toInstant().toEpochMilli() }
            runCatching { return LocalDate.parse(v, DateTimeFormatter.ofPattern("dd/MM/yyyy")).atStartOfDay(zone).toInstant().toEpochMilli() }
            runCatching { return LocalDate.parse(v, DateTimeFormatter.ofPattern("d/M/yyyy")).atStartOfDay(zone).toInstant().toEpochMilli() }
            val mmYY = Regex("^(\\d{1,2})\\s*/\\s*(\\d{2}|\\d{4})$").matchEntire(v)
            if (mmYY != null) {
                val month = mmYY.groupValues[1].toInt()
                var year = mmYY.groupValues[2].toInt()
                if (year < 100) year += 2000
                if (month in 1..12) return YearMonth.of(year, month).atEndOfMonth().atStartOfDay(zone).toInstant().toEpochMilli()
            }
            val ym = Regex("^(\\d{4})-(\\d{1,2})$").matchEntire(v)
            if (ym != null) {
                val month = ym.groupValues[2].toInt()
                if (month in 1..12) return YearMonth.of(ym.groupValues[1].toInt(), month).atEndOfMonth().atStartOfDay(zone).toInstant().toEpochMilli()
            }
            return null
        }
    }
}
