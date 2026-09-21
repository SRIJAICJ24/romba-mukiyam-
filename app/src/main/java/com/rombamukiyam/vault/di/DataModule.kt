package com.rombamukiyam.vault.di

import android.content.Context
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.security.ReminderSchedulePublisher
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.data.repository.DiagnosticsRepositoryImpl
import com.rombamukiyam.vault.data.repository.FamilyRepositoryImpl
import com.rombamukiyam.vault.data.repository.ReminderRepositoryImpl
import com.rombamukiyam.vault.data.repository.VaultEventLoggerImpl
import com.rombamukiyam.vault.data.repository.VaultRepositoryImpl
import com.rombamukiyam.vault.domain.repository.DiagnosticsRepository
import com.rombamukiyam.vault.domain.repository.FamilyRepository
import com.rombamukiyam.vault.domain.repository.ReminderRepository
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.repository.VaultEventLogger
import com.rombamukiyam.vault.domain.repository.VaultRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides @Singleton
    fun familyRepository(session: VaultSession, clock: Clock): FamilyRepository = FamilyRepositoryImpl(session, clock)

    @Provides @Singleton
    fun schedulePublisher(@ApplicationContext context: Context): ReminderSchedulePublisher =
        ReminderSchedulePublisher(File(context.filesDir, "schedule"))

    @Provides @Singleton
    fun reminderRepository(session: VaultSession, settings: SettingsRepository, publisher: ReminderSchedulePublisher, clock: Clock): ReminderRepository =
        ReminderRepositoryImpl(session, settings, publisher, clock)

    @Provides @Singleton
    fun vaultRepository(session: VaultSession, clock: Clock, reminders: ReminderRepository): VaultRepository =
        VaultRepositoryImpl(session, clock, reminders)

    @Provides @Singleton
    fun eventLogger(session: VaultSession, clock: Clock): VaultEventLogger = VaultEventLoggerImpl(session, clock)

    @Provides @Singleton
    fun diagnosticsRepository(session: VaultSession, clock: Clock): DiagnosticsRepository = DiagnosticsRepositoryImpl(session, clock)
}
