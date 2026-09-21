package com.rombamukiyam.vault.feature.settings

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rombamukiyam.vault.core.common.BiometricUnavailableReason
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.core.security.BiometricAuthenticator
import com.rombamukiyam.vault.core.security.BiometricOutcome
import com.rombamukiyam.vault.core.security.KeySecurityLevel
import com.rombamukiyam.vault.core.security.KeystoreManager
import com.rombamukiyam.vault.core.security.VaultKeyManager
import com.rombamukiyam.vault.domain.repository.AppSettings
import com.rombamukiyam.vault.domain.repository.ReminderRepository
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.usecase.DisableBiometricUseCase
import com.rombamukiyam.vault.domain.usecase.EnableBiometricUseCase
import com.rombamukiyam.vault.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val biometricEnabled: Boolean = false,
    val biometricAvailability: BiometricUnavailableReason? = null,
    val keySecurityLevel: KeySecurityLevel = KeySecurityLevel.UNKNOWN,
    val busy: Boolean = false,
    val error: VaultError? = null,
    val message: Int? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val keys: VaultKeyManager,
    private val keystore: KeystoreManager,
    private val biometrics: BiometricAuthenticator,
    private val enableBiometric: EnableBiometricUseCase,
    private val disableBiometric: DisableBiometricUseCase,
    private val reminders: ReminderRepository,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = combine(settingsRepo.settings, local) { s, l ->
        l.copy(
            settings = s,
            biometricEnabled = keys.biometricEnabled(),
            biometricAvailability = biometrics.availability(),
            keySecurityLevel = if (keys.biometricEnabled()) keystore.securityLevel(KeystoreManager.BIOMETRIC_ALIAS) else KeySecurityLevel.UNKNOWN,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settingsRepo.update(transform)
        // Notification-related settings change what the background schedule may contain.
        runCatching { reminders.republish() }
    }

    fun setTheme(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setTextScale(scale: Float) = update { it.copy(textScale = scale) }
    fun setAutoLock(seconds: Int) = update { it.copy(autoLockSeconds = seconds) }
    fun setClipboard(seconds: Int) = update { it.copy(clipboardClearSeconds = seconds) }
    fun setReveal(seconds: Int) = update { it.copy(revealTimeoutSeconds = seconds) }
    fun setTrashRetention(days: Int) = update { it.copy(trashRetentionDays = days) }
    fun setBackupReminder(days: Int) = update { it.copy(backupReminderDays = days) }
    fun setDefaultReview(days: Int?) = update { it.copy(defaultReviewDays = days) }

    fun toggleBiometric(activity: FragmentActivity?, title: String, subtitle: String, negative: String) {
        val s = local.value
        if (s.busy) return
        local.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            if (keys.biometricEnabled()) {
                when (val r = disableBiometric()) {
                    is Outcome.Success -> local.update { it.copy(busy = false) }
                    is Outcome.Failure -> local.update { it.copy(busy = false, error = r.error) }
                }
                return@launch
            }
            if (activity == null) { local.update { it.copy(busy = false) }; return@launch }
            when (val prep = enableBiometric.prepare()) {
                is Outcome.Failure -> local.update { it.copy(busy = false, error = prep.error) }
                is Outcome.Success -> when (val outcome = biometrics.authenticate(activity, prep.value, title, subtitle, negative)) {
                    is BiometricOutcome.Success -> when (val done = enableBiometric.complete(outcome.cipher)) {
                        is Outcome.Success -> local.update { it.copy(busy = false) }
                        is Outcome.Failure -> local.update { it.copy(busy = false, error = done.error) }
                    }
                    else -> {
                        // Enrolment cancelled: remove the half-created Keystore key so state stays consistent.
                        runCatching { keys.disableBiometric() }
                        local.update { it.copy(busy = false) }
                    }
                }
            }
        }
    }

    fun clearError() = local.update { it.copy(error = null, message = null) }
}
