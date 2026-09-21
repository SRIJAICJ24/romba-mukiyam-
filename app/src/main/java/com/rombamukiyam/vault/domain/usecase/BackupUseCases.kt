package com.rombamukiyam.vault.domain.usecase

import android.net.Uri
import com.rombamukiyam.vault.core.backup.BackupInfo
import com.rombamukiyam.vault.core.backup.BackupManager
import com.rombamukiyam.vault.core.backup.BackupResult
import com.rombamukiyam.vault.core.common.DispatcherProvider
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.safeOutcome
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.domain.model.HistoryEventType
import com.rombamukiyam.vault.domain.repository.BackupRecord
import com.rombamukiyam.vault.domain.repository.ReminderRepository
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.repository.VaultEventLogger
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CreateBackupUseCase @Inject constructor(
    private val manager: BackupManager,
    private val settings: SettingsRepository,
    private val events: VaultEventLogger,
    private val reminders: ReminderRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(destination: Uri, passphrase: CharArray, displayName: String?): Outcome<BackupResult> = withContext(dispatchers.io) {
        safeOutcome {
            val result = passphrase.useAsBytes { manager.createBackup(destination, it, displayName) }
            settings.update { it.copy(lastBackupAt = result.createdAt, lastBackupVerified = result.verified) }
            events.log(HistoryEventType.BACKUP_CREATED, "${result.itemCount} items")
            events.log(HistoryEventType.BACKUP_VERIFIED, null)
            reminders.republish()
            result
        }
    }
}

class VerifyBackupUseCase @Inject constructor(private val manager: BackupManager, private val dispatchers: DispatcherProvider) {
    suspend operator fun invoke(source: Uri, passphrase: CharArray): Outcome<BackupInfo> = withContext(dispatchers.io) {
        safeOutcome { passphrase.useAsBytes { manager.verify(source, it) } }
    }
}

class RestoreBackupUseCase @Inject constructor(
    private val manager: BackupManager,
    private val events: VaultEventLogger,
    private val reminders: ReminderRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(source: Uri, backupPassphrase: CharArray, displayName: String?): Outcome<BackupResult> = withContext(dispatchers.io) {
        safeOutcome {
            val result = backupPassphrase.useAsBytes { manager.restore(source, it, displayName) }
            events.log(HistoryEventType.BACKUP_RESTORED, "${result.itemCount} items")
            reminders.republish()
            result
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveBackupHistoryUseCase @Inject constructor(private val session: VaultSession) {
    operator fun invoke(): Flow<List<BackupRecord>> = session.state.flatMapLatest {
        if (session.isUnlocked) {
            session.database().reminderDao().observeBackups().map { l -> l.map { BackupRecord(it.id, it.createdAt, it.displayName, it.sizeBytes, it.itemCount, it.verified, it.event) } }
        } else {
            flowOf(emptyList())
        }
    }
}
