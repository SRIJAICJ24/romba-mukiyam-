package com.rombamukiyam.vault.domain.usecase

import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.common.DispatcherProvider
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.core.common.safeOutcome
import com.rombamukiyam.vault.core.crypto.PasswordStrength
import com.rombamukiyam.vault.core.crypto.StrengthLevel
import com.rombamukiyam.vault.domain.model.CategoryTemplates
import com.rombamukiyam.vault.domain.model.FieldType
import com.rombamukiyam.vault.domain.model.PasswordPolicy
import com.rombamukiyam.vault.domain.model.VaultCategory
import com.rombamukiyam.vault.domain.model.VaultItemSummary
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.repository.VaultRepository
import javax.inject.Inject
import kotlinx.coroutines.withContext

enum class HealthIssue { WEAK_PASSWORD, REUSED_PASSWORD, OLD_PASSWORD, REVIEW_OVERDUE, MISSING_2FA, MISSING_RECOVERY, NO_PASSWORD_DATE }

data class HealthFinding(val item: VaultItemSummary, val issues: List<HealthIssue>)

data class HealthReport(
    val findings: List<HealthFinding>,
    val totalItems: Int,
    val weak: Int,
    val reused: Int,
    val old: Int,
    val overdue: Int,
    val missing2fa: Int,
    val missingRecovery: Int,
    val lastBackupAt: Long?,
    val backupOverdue: Boolean,
) {
    /** 0..100, a simple, explainable score. */
    val score: Int
        get() {
            if (totalItems == 0) return 100
            val penalties = weak * 3 + reused * 3 + old + overdue * 2 + missing2fa + missingRecovery + (if (backupOverdue) 5 else 0)
            return (100 - (penalties * 100) / (totalItems * 6 + 5)).coerceIn(0, 100)
        }
}

/**
 * Local-only password health. Passwords are decrypted in memory just long enough to score them and to compute
 * reuse via keyed digests; nothing is written anywhere and no network is involved.
 */
class CheckPasswordHealthUseCase @Inject constructor(
    private val vault: VaultRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(items: List<VaultItemSummary>): Outcome<HealthReport> = withContext(dispatchers.default) {
        safeOutcome {
            val now = clock.nowMillis()
            val s = settings.current()
            val passwords = vault.primaryPasswordsForHealth() // (itemId, fieldId, value)
            val byItem = passwords.groupBy { it.first }
            val reusedItemIds = passwords.groupBy { it.third }.filter { it.value.map { p -> p.first }.distinct().size > 1 }
                .values.flatten().map { it.first }.toSet()

            val findings = items.filter { it.deletedAt == null }.mapNotNull { item ->
                val issues = mutableListOf<HealthIssue>()
                val pws = byItem[item.id].orEmpty()
                if (pws.any { PasswordStrength.evaluate(it.third).level <= StrengthLevel.WEAK }) issues += HealthIssue.WEAK_PASSWORD
                if (item.id in reusedItemIds) issues += HealthIssue.REUSED_PASSWORD
                val hasPassword = pws.isNotEmpty() && CategoryTemplates.hasPrimaryPassword(item.category)
                if (hasPassword) {
                    val last = item.passwordLastChangedAt
                    if (last == null) {
                        issues += HealthIssue.NO_PASSWORD_DATE
                    } else {
                        if (now - last > 365L * 86_400_000L) issues += HealthIssue.OLD_PASSWORD
                        item.reviewIntervalDays?.let { if (last + it * 86_400_000L <= now) issues += HealthIssue.REVIEW_OVERDUE }
                    }
                }
                if (issues.isEmpty()) null else HealthFinding(item, issues)
            }.toMutableList()

            // 2FA / recovery information: needs full item; only check password-bearing categories.
            val enriched = findings.map { f -> f }.toMutableList()
            items.filter { it.deletedAt == null && CategoryTemplates.hasPrimaryPassword(it.category) && it.category != VaultCategory.WIFI }.forEach { summary ->
                val full = vault.getItem(summary.id) ?: return@forEach
                val extra = mutableListOf<HealthIssue>()
                if (full.twoFactorEnabled != true && full.category in setOf(VaultCategory.BANK, VaultCategory.EMAIL, VaultCategory.INVESTMENT, VaultCategory.WEBSITE, VaultCategory.APP)) {
                    extra += HealthIssue.MISSING_2FA
                }
                val hasRecovery = full.fields.any {
                    it.type in setOf(FieldType.RECOVERY_EMAIL, FieldType.RECOVERY_PHONE, FieldType.RECOVERY_CODE, FieldType.RECOVERY_NOTES, FieldType.PHONE, FieldType.EMAIL) && it.value.isNotBlank()
                }
                if (!hasRecovery) extra += HealthIssue.MISSING_RECOVERY
                if (extra.isNotEmpty()) {
                    val idx = enriched.indexOfFirst { it.item.id == summary.id }
                    if (idx >= 0) enriched[idx] = enriched[idx].copy(issues = enriched[idx].issues + extra) else enriched += HealthFinding(summary, extra)
                }
            }
            val all = enriched.flatMap { it.issues }
            HealthReport(
                findings = enriched.sortedByDescending { it.issues.size },
                totalItems = items.count { it.deletedAt == null },
                weak = all.count { it == HealthIssue.WEAK_PASSWORD },
                reused = all.count { it == HealthIssue.REUSED_PASSWORD },
                old = all.count { it == HealthIssue.OLD_PASSWORD },
                overdue = all.count { it == HealthIssue.REVIEW_OVERDUE },
                missing2fa = all.count { it == HealthIssue.MISSING_2FA },
                missingRecovery = all.count { it == HealthIssue.MISSING_RECOVERY },
                lastBackupAt = s.lastBackupAt,
                backupOverdue = s.lastBackupAt == null || s.lastBackupAt + s.backupReminderDays * 86_400_000L <= now,
            )
        }
    }
}

/**
 * The password-change workflow's final step. Validates against the account policy and history, then performs
 * the single atomic transaction (field + lastChanged + history event + digest).
 */
class UpdatePasswordUseCase @Inject constructor(
    private val vault: VaultRepository,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(itemId: String, fieldId: String, newPassword: String, allowReuse: Boolean = false): Outcome<Unit> = withContext(dispatchers.io) {
        if (newPassword.isBlank()) return@withContext Outcome.Failure(VaultError.ValidationFailed(listOf("empty")))
        val policy = vault.getPolicy(itemId)
        val violations = policy?.violations(newPassword).orEmpty()
        if (violations.isNotEmpty()) return@withContext Outcome.Failure(VaultError.ValidationFailed(violations))
        if (!allowReuse && vault.wasUsedBefore(itemId, fieldId, newPassword)) {
            return@withContext Outcome.Failure(VaultError.ValidationFailed(listOf("reused")))
        }
        safeOutcome { vault.changePassword(itemId, fieldId, newPassword, clock.nowMillis()) }
    }
}

class SavePasswordPolicyUseCase @Inject constructor(private val vault: VaultRepository, private val dispatchers: DispatcherProvider) {
    suspend operator fun invoke(policy: PasswordPolicy): Outcome<Unit> = withContext(dispatchers.io) {
        if (policy.minLength != null && policy.maxLength != null && policy.minLength > policy.maxLength) {
            return@withContext Outcome.Failure(VaultError.ValidationFailed(listOf("min_gt_max")))
        }
        safeOutcome { vault.savePolicy(policy) }
    }
}

class DeletePasswordPolicyUseCase @Inject constructor(private val vault: VaultRepository, private val dispatchers: DispatcherProvider) {
    suspend operator fun invoke(itemId: String): Outcome<Unit> = withContext(dispatchers.io) { safeOutcome { vault.deletePolicy(itemId) } }
}
