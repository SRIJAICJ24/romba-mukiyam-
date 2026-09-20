package com.rombamukiyam.vault.domain.usecase

import com.rombamukiyam.vault.core.common.DispatcherProvider
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.safeOutcome
import com.rombamukiyam.vault.core.crypto.wipe
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
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(passphrase: CharArray): Outcome<String> = withContext(dispatchers.default) {
        safeOutcome {
            val result = passphrase.useAsBytes { keys.createVault(it) }
            try {
                session.unlock(result.vaultKey)
            } finally {
                result.vaultKey.wipe()
            }
            events.log(HistoryEventType.VAULT_CREATED, null)
            result.recoveryKeyEncoded
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
