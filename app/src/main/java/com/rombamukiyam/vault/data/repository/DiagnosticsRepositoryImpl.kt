package com.rombamukiyam.vault.data.repository

import com.rombamukiyam.vault.BuildConfig
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.database.RombaDatabase
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.data.entity.DiagnosticEventEntity
import com.rombamukiyam.vault.domain.repository.DiagnosticEvent
import com.rombamukiyam.vault.domain.repository.DiagnosticsRepository
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Diagnostics live inside the encrypted database (so even codes stay private) and hold only:
 * timestamp, app version, schema version, module, safe code, exception class name. Never messages or data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsRepositoryImpl(private val session: VaultSession, private val clock: Clock) : DiagnosticsRepository {

    override fun observe(): Flow<List<DiagnosticEvent>> = session.state.flatMapLatest {
        if (session.isUnlocked) {
            session.database().reminderDao().observeDiagnostics().map { l -> l.map { DiagnosticEvent(it.id, it.occurredAt, it.appVersion, it.schemaVersion, it.module, it.code, it.exceptionClass) } }
        } else {
            flowOf(emptyList())
        }
    }

    override suspend fun record(module: String, code: String, exception: Throwable?) {
        if (!session.isUnlocked) return
        runCatching {
            session.database().reminderDao().insertDiagnostic(
                DiagnosticEventEntity(
                    id = UUID.randomUUID().toString(), occurredAt = clock.nowMillis(), appVersion = BuildConfig.VERSION_NAME,
                    schemaVersion = RombaDatabase.SCHEMA_VERSION, module = module, code = code, exceptionClass = exception?.javaClass?.simpleName,
                ),
            )
        }
    }

    override suspend fun clear() {
        if (session.isUnlocked) session.database().reminderDao().clearDiagnostics()
    }
}
