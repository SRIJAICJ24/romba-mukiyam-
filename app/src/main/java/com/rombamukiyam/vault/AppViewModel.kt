package com.rombamukiyam.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rombamukiyam.vault.core.security.AutoLockController
import com.rombamukiyam.vault.core.security.RootDetector
import com.rombamukiyam.vault.core.security.SessionState
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.domain.repository.AppSettings
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.usecase.OnUnlockMaintenanceUseCase
import com.rombamukiyam.vault.domain.usecase.VaultExistsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppRoute { LOADING, ONBOARDING, LOCKED, MAIN }

data class AppUiState(
    val route: AppRoute = AppRoute.LOADING,
    val settings: AppSettings = AppSettings(),
    val safeMode: Boolean = false,
    val showRootWarning: Boolean = false,
    val inBackground: Boolean = false,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val session: VaultSession,
    private val settingsRepo: SettingsRepository,
    private val vaultExists: VaultExistsUseCase,
    private val autoLock: AutoLockController,
    private val maintenance: OnUnlockMaintenanceUseCase,
    rootDetector: RootDetector,
) : ViewModel() {

    private val rootSuspect = rootDetector.looksCompromised()

    init {
        // Housekeeping (trash purge, reminder schedule) runs each time the vault becomes unlocked.
        viewModelScope.launch {
            session.state.collect { if (it is SessionState.Unlocked) maintenance() }
        }
    }

    val state: StateFlow<AppUiState> = combine(session.state, settingsRepo.settings, autoLock.inBackground) { sessionState, settings, background ->
        val exists = vaultExists()
        val route = when {
            !exists -> AppRoute.ONBOARDING
            // Onboarding started but was interrupted after vault creation: continue onboarding while unlocked,
            // otherwise ask for the passphrase first.
            !settings.onboardingComplete && sessionState is SessionState.Unlocked -> AppRoute.ONBOARDING
            sessionState is SessionState.Locked -> AppRoute.LOCKED
            else -> AppRoute.MAIN
        }
        AppUiState(
            route = route,
            settings = settings,
            safeMode = (sessionState as? SessionState.Unlocked)?.safeMode == true,
            showRootWarning = rootSuspect && !settings.rootWarningDismissed,
            inBackground = background,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    fun lockNow() = autoLock.lockNow()

    fun dismissRootWarning() = viewModelScope.launch { settingsRepo.update { it.copy(rootWarningDismissed = true) } }
}
