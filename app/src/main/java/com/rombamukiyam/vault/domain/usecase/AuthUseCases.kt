package com.rombamukiyam.vault.domain.usecase

import com.rombamukiyam.vault.core.common.DispatcherProvider
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.core.common.VaultException
import com.rombamukiyam.vault.core.common.safeOutcome
import com.rombamukiyam.vault.core.crypto.RecoveryKeyCodec
import com.rombamukiyam.vault.core.crypto.wipe
import com.rombamukiyam.vault.core.security.VaultKeyManager
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.domain.repository.VaultEventLogger
import com.rombamukiyam.vault.domain.model.HistoryEventType
import javax.crypto.Cipher
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Encodes a passphrase to UTF-8 bytes, runs [block], then wipes both. */
internal inline fun <T> CharArray.useAsBytes(block: (ByteArray) -> T): T {
    val bytes = String(this).toByteArray(Charsets.UTF_8)
    try {
        return block(bytes)
    } finally {
        bytes.wipe()
    }
}

class UnlockVaultUseCase @Inject constructor(
    private val keys: VaultKeyManager,
    private val session: VaultSession,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(passphrase: CharArray): Outcome<Unit> = withContext(dispatchers.default) {
        safeOutcome {
            val vk = passphrase.useAsBytes { keys.unlockWithPassphrase(it) }
                ?: throw VaultException(VaultError.AuthenticationFailed())
            try {
                session.unlock(vk)
            } finally {
                vk.wipe()
            }
        }
    }
}

class LockVaultUseCase @Inject constructor(private val session: VaultSession) {
    operator fun invoke() = session.lock()
}

/**
 * Recovery: unlock with the recovery key and immediately set a new master passphrase.
 * The recovery key stays valid (it wraps the same Vault Key).
 */
class RecoverVaultUseCase @Inject constructor(
    private val keys: VaultKeyManager,
    private val session: VaultSession,
    private val dispatchers: DispatcherProvider,
    private val events: VaultEventLogger,
) {
    suspend operator fun invoke(recoveryKeyText: String, newPassphrase: CharArray): Outcome<Unit> = withContext(dispatchers.default) {
        safeOutcome {
            val rk = RecoveryKeyCodec.decode(recoveryKeyText) ?: throw VaultException(VaultError.WrongRecoveryCredential)
            val vk = try {
                keys.unlockWithRecoveryKey(rk) ?: throw VaultException(VaultError.WrongRecoveryCredential)
            } finally {
                rk.wipe()
            }
            try {
                newPassphrase.useAsBytes { keys.changePassphrase(vk, it) }
                session.unlock(vk)
                events.log(HistoryEventType.MASTER_PASSPHRASE_CHANGED, "via recovery key")
            } finally {
                vk.wipe()
            }
        }
    }
}

class ChangeMasterPassphraseUseCase @Inject constructor(
    private val keys: VaultKeyManager,
    private val session: VaultSession,
    private val dispatchers: DispatcherProvider,
    private val events: VaultEventLogger,
) {
    suspend operator fun invoke(current: CharArray, new: CharArray): Outcome<Unit> = withContext(dispatchers.default) {
        safeOutcome {
            val vk = current.useAsBytes { keys.unlockWithPassphrase(it) } ?: throw VaultException(VaultError.AuthenticationFailed())
            try {
                new.useAsBytes { keys.changePassphrase(vk, it) }
            } finally {
                vk.wipe()
            }
            if (session.isUnlocked) events.log(HistoryEventType.MASTER_PASSPHRASE_CHANGED, null)
        }
    }
}

class RegenerateRecoveryKeyUseCase @Inject constructor(
    private val keys: VaultKeyManager,
    private val session: VaultSession,
    private val dispatchers: DispatcherProvider,
    private val events: VaultEventLogger,
) {
    suspend operator fun invoke(currentPassphrase: CharArray): Outcome<String> = withContext(dispatchers.default) {
        safeOutcome {
            val vk = currentPassphrase.useAsBytes { keys.unlockWithPassphrase(it) } ?: throw VaultException(VaultError.AuthenticationFailed())
            try {
                keys.regenerateRecoveryKey(vk).also { events.log(HistoryEventType.RECOVERY_KEY_REGENERATED, null) }
            } finally {
                vk.wipe()
            }
        }
    }
}

/** Two-step biometric unlock; the UI performs the BiometricPrompt between [prepare] and [complete]. */
class BiometricUnlockUseCase @Inject constructor(
    private val keys: VaultKeyManager,
    private val session: VaultSession,
    private val dispatchers: DispatcherProvider,
) {
    fun isEnabled(): Boolean = keys.biometricEnabled()

    suspend fun prepare(): Outcome<Cipher> = withContext(dispatchers.io) { safeOutcome { keys.prepareBiometricUnlock() } }

    suspend fun complete(authenticatedCipher: Cipher): Outcome<Unit> = withContext(dispatchers.default) {
        safeOutcome {
            val vk = keys.completeBiometricUnlock(authenticatedCipher)
            try {
                session.unlock(vk)
            } finally {
                vk.wipe()
            }
        }
    }

    /** Called when the Keystore reports invalidation: drop the stale slot, keep everything else. */
    suspend fun clearInvalidatedSlot() = withContext(dispatchers.io) { runCatching { keys.disableBiometric() } }
}

/** Two-step biometric enrolment; requires an unlocked session (we need the Vault Key to wrap). */
class EnableBiometricUseCase @Inject constructor(
    private val keys: VaultKeyManager,
    private val session: VaultSession,
    private val dispatchers: DispatcherProvider,
    private val events: VaultEventLogger,
) {
    suspend fun prepare(): Outcome<Cipher> = withContext(dispatchers.io) { safeOutcome { keys.prepareBiometricEnrollment() } }

    suspend fun complete(authenticatedCipher: Cipher): Outcome<Unit> = withContext(dispatchers.default) {
        safeOutcome {
            val vk = session.vaultKeyCopy()
            try {
                keys.completeBiometricEnrollment(authenticatedCipher, vk)
            } finally {
                vk.wipe()
            }
            events.log(HistoryEventType.BIOMETRIC_ENABLED, null)
        }
    }
}

class DisableBiometricUseCase @Inject constructor(
    private val keys: VaultKeyManager,
    private val dispatchers: DispatcherProvider,
    private val events: VaultEventLogger,
) {
    suspend operator fun invoke(): Outcome<Unit> = withContext(dispatchers.io) {
        safeOutcome {
            keys.disableBiometric()
            events.log(HistoryEventType.BIOMETRIC_DISABLED, null)
        }
    }
}
