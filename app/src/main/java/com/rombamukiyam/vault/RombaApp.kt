package com.rombamukiyam.vault

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import com.rombamukiyam.vault.core.common.DiagnosticsBus
import com.rombamukiyam.vault.core.common.SafeLog
import com.rombamukiyam.vault.di.ApplicationScope
import com.rombamukiyam.vault.domain.repository.DiagnosticsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.rombamukiyam.vault.core.security.AutoLockController
import androidx.work.Configuration
import com.rombamukiyam.vault.worker.Notifications
import com.rombamukiyam.vault.worker.ReminderCheckWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RombaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var autoLock: AutoLockController

    @Inject
    lateinit var diagnostics: DiagnosticsRepository

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(SafeLog.WORKMANAGER_MIN_LEVEL)
            .build()

    override fun onCreate() {
        super.onCreate()
        // SQLCipher native library must be loaded once before any database is opened.
        System.loadLibrary("sqlcipher")
        autoLock.start()
        Notifications.ensureChannel(this)
        ReminderCheckWorker.schedule(this)
        DiagnosticsBus.sink = { code, exceptionClass ->
            appScope.launch { diagnostics.record("app", code, exceptionClass?.let { RuntimeException(it) }) }
        }
    }
}
