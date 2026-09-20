package com.rombamukiyam.vault.domain.usecase

import com.rombamukiyam.vault.core.common.DispatcherProvider
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.safeOutcome
import com.rombamukiyam.vault.core.crypto.wipe
import com.rombamukiyam.vault.core.database.DatabaseFactory
import com.rombamukiyam.vault.core.security.VaultKeyManager
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.domain.model.AvatarType
import com.rombamukiyam.vault.domain.model.HistoryEventType
import com.rombamukiyam.vault.domain.model.Relationship
import com.rombamukiyam.vault.domain.repository.FamilyRepository
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.repository.VaultEventLogger
import javax.inject.Inject
import kotlinx.coroutines.withContext

class VaultExistsUseCase @Inject constructor(private val keys: VaultKeyManager) {
    operator fun invoke(): Boolean = keys.vaultExists()
}

/**
 * Onboarding step: derive KDF params for this device, create the Vault Key + slots, open (create) the
 * encrypted database and leave the session unlocked so the remaining onboarding steps can write to it.
 * Returns the one-time recovery key text.
 */
class CreateVaultUseCase @Inject constructor(
    private val keys: VaultKeyManager,
    private val session: VaultSession,
    private val events: VaultEventLogger,
    private val databaseFactory: DatabaseFactory,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(passphrase: CharArray): Outcome<String> = withContext(dispatchers.default) {
        safeOutcome {
            val result = passphrase.useAsBytes { keys.createVault(it) }
            try {
                session.unlock(result.vaultKey)
            } catch (e: Exception) {
                // Nothing was ever stored in a database: undo the keyring so onboarding can be retried cleanly.
                keys.rollbackFailedCreation(databaseExists = databaseFactory.exists())
                throw e
            } finally {
                result.vaultKey.wipe()
            }
            events.log(HistoryEventType.VAULT_CREATED, null)
            result.recoveryKeyEncoded
        }
    }
}

/**
 * Onboarding was interrupted after the vault existed (process death, crash). The original recovery key
 * can no longer be shown, so a fresh one is generated for the user to record.
 */
class ResumeOnboardingUseCase @Inject constructor(
    private val keys: VaultKeyManager,
    private val session: VaultSession,
    private val events: VaultEventLogger,
    private val dispatchers: DispatcherProvider,
) {
    fun shouldResume(): Boolean = keys.vaultExists() && session.isUnlocked

    suspend fun regenerateRecoveryKey(): Outcome<String> = withContext(dispatchers.default) {
        safeOutcome {
            val vk = session.vaultKeyCopy()
            try {
                keys.regenerateRecoveryKey(vk).also { events.log(HistoryEventType.RECOVERY_KEY_REGENERATED, "onboarding resumed") }
            } finally {
                vk.wipe()
            }
        }
    }
}

class CreateFirstProfileUseCase @Inject constructor(
    private val family: FamilyRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(name: String, relationship: Relationship, color: Long): Outcome<Unit> = withContext(dispatchers.io) {
        safeOutcome { family.create(name, relationship, AvatarType.INITIALS, color); Unit }
    }
}

class CompleteOnboardingUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(backupReminderDays: Int) {
        settings.update { it.copy(onboardingComplete = true, backupReminderDays = backupReminderDays) }
    }
}
