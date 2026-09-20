package com.rombamukiyam.vault.di

import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.data.repository.FamilyRepositoryImpl
import com.rombamukiyam.vault.data.repository.VaultEventLoggerImpl
import com.rombamukiyam.vault.data.repository.VaultRepositoryImpl
import com.rombamukiyam.vault.domain.repository.FamilyRepository
import com.rombamukiyam.vault.domain.repository.VaultEventLogger
import com.rombamukiyam.vault.domain.repository.VaultRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides @Singleton
    fun familyRepository(session: VaultSession, clock: Clock): FamilyRepository = FamilyRepositoryImpl(session, clock)

    @Provides @Singleton
    fun vaultRepository(session: VaultSession, clock: Clock): VaultRepository = VaultRepositoryImpl(session, clock)

    @Provides @Singleton
    fun eventLogger(session: VaultSession, clock: Clock): VaultEventLogger = VaultEventLoggerImpl(session, clock)
}
