package com.rombamukiyam.vault.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.domain.model.FamilyProfile
import com.rombamukiyam.vault.domain.model.VaultItemSummary
import com.rombamukiyam.vault.domain.repository.CategoryCountSummary
import com.rombamukiyam.vault.domain.repository.FamilyRepository
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.repository.VaultRepository
import com.rombamukiyam.vault.domain.usecase.GetUpcomingAlertsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val profiles: List<FamilyProfile> = emptyList(),
    val favorites: List<VaultItemSummary> = emptyList(),
    val categoryCounts: List<CategoryCountSummary> = emptyList(),
    val totalItems: Int = 0,
    val reviewsDue: Int = 0,
    val expiringSoon: Int = 0,
    val lastBackupAt: Long? = null,
    val backupReminderDays: Int = 30,
    val nowMillis: Long = 0L,
    val loaded: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    family: FamilyRepository,
    vault: VaultRepository,
    settings: SettingsRepository,
    alerts: GetUpcomingAlertsUseCase,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        family.observeActive(),
        vault.observeFavorites(),
        vault.observeCategoryCounts(),
        settings.settings,
        alerts.observeSummary(),
    ) { profiles, favorites, counts, s, alertSummary ->
        HomeUiState(
            profiles = profiles,
            favorites = favorites.take(6),
            categoryCounts = counts,
            totalItems = counts.sumOf { it.count },
            reviewsDue = alertSummary.reviewsDue,
            expiringSoon = alertSummary.expiringSoon,
            lastBackupAt = s.lastBackupAt,
            backupReminderDays = s.backupReminderDays,
            nowMillis = clock.nowMillis(),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
