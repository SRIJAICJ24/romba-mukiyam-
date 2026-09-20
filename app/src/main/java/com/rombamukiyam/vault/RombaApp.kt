package com.rombamukiyam.vault

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import com.rombamukiyam.vault.core.security.AutoLockController
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RombaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var autoLock: AutoLockController

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()

    override fun onCreate() {
        super.onCreate()
        // SQLCipher native library must be loaded once before any database is opened.
        System.loadLibrary("sqlcipher")
        autoLock.start()
    }
}
