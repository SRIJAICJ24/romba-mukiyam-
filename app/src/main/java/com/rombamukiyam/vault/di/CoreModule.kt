package com.rombamukiyam.vault.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.common.DefaultDispatcherProvider
import com.rombamukiyam.vault.core.common.DispatcherProvider
import com.rombamukiyam.vault.core.common.SystemClock
import com.rombamukiyam.vault.core.crypto.Argon2Kdf
import com.rombamukiyam.vault.core.crypto.PassphraseKdf
import com.rombamukiyam.vault.core.database.DatabaseFactory
import com.rombamukiyam.vault.core.database.IntegrityChecker
import com.rombamukiyam.vault.core.security.AutoLockController
import com.rombamukiyam.vault.core.security.BiometricAuthenticator
import com.rombamukiyam.vault.core.security.KeyringStore
import com.rombamukiyam.vault.core.security.KeystoreManager
import com.rombamukiyam.vault.core.security.RootDetector
import com.rombamukiyam.vault.core.security.VaultKeyManager
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.data.repository.DataStoreSettingsRepository
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides @Singleton fun clock(): Clock = SystemClock()

    @Provides @Singleton fun dispatchers(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("romba_settings") }

    @Provides @Singleton
    fun settingsRepository(dataStore: DataStore<Preferences>): SettingsRepository = DataStoreSettingsRepository(dataStore)

    @Provides @Singleton
    fun keyringStore(@ApplicationContext context: Context): KeyringStore =
        KeyringStore(File(context.filesDir, KeyringStore.DIR_NAME))

    @Provides @Singleton fun keystoreManager(): KeystoreManager = KeystoreManager()

    @Provides @Singleton fun passphraseKdf(): PassphraseKdf = Argon2Kdf()

    @Provides @Singleton
    fun vaultKeyManager(store: KeyringStore, kdf: PassphraseKdf, keystore: KeystoreManager, clock: Clock): VaultKeyManager =
        VaultKeyManager(store, kdf, keystore, clock)

    @Provides @Singleton
    fun databaseFactory(@ApplicationContext context: Context): DatabaseFactory = DatabaseFactory(context)

    @Provides @Singleton fun integrityChecker(): IntegrityChecker = IntegrityChecker()

    @Provides @Singleton
    fun vaultSession(factory: DatabaseFactory, integrity: IntegrityChecker, clock: Clock): VaultSession =
        VaultSession(factory, integrity, clock)

    @Provides @Singleton
    fun biometricAuthenticator(@ApplicationContext context: Context): BiometricAuthenticator = BiometricAuthenticator(context)

    @Provides @Singleton
    fun autoLockController(
        @ApplicationContext context: Context,
        session: VaultSession,
        settings: SettingsRepository,
        @ApplicationScope scope: CoroutineScope,
    ): AutoLockController = AutoLockController(context, session, settings, scope)

    @Provides @Singleton fun rootDetector(): RootDetector = RootDetector()
}
