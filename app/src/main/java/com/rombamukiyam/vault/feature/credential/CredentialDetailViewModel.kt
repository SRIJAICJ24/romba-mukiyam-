package com.rombamukiyam.vault.feature.credential

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.core.security.SecureClipboard
import com.rombamukiyam.vault.domain.model.FamilyProfile
import com.rombamukiyam.vault.domain.model.HistoryEvent
import com.rombamukiyam.vault.domain.model.VaultItem
import com.rombamukiyam.vault.domain.repository.AppSettings
import com.rombamukiyam.vault.domain.repository.FamilyRepository
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.repository.VaultRepository
import com.rombamukiyam.vault.domain.usecase.DeleteVaultItemUseCase
import com.rombamukiyam.vault.domain.usecase.MarkReviewedUseCase
import com.rombamukiyam.vault.domain.usecase.PermanentlyDeleteVaultItemUseCase
import com.rombamukiyam.vault.domain.usecase.RestoreVaultItemUseCase
import com.rombamukiyam.vault.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class DetailDialog {
    data object Trash : DetailDialog()
    data object DeleteForever : DetailDialog()
}

sealed class DetailEffect {
    data class Snack(val messageRes: Int) : DetailEffect()
    data class OpenUrl(val url: String) : DetailEffect()
    data object Closed : DetailEffect()
    data class Error(val error: VaultError) : DetailEffect()
}

data class DetailUiState(
    val item: VaultItem? = null,
    val owner: FamilyProfile? = null,
    val history: List<HistoryEvent> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val dialog: DetailDialog? = null,
    val nowMillis: Long = 0L,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CredentialDetailViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val family: FamilyRepository,
    settings: SettingsRepository,
    private val clipboard: SecureClipboard,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val moveToTrash: DeleteVaultItemUseCase,
    private val restore: RestoreVaultItemUseCase,
    private val deleteForever: PermanentlyDeleteVaultItemUseCase,
    private val markReviewed: MarkReviewedUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val itemId = MutableStateFlow<String?>(null)
    private val dialog = MutableStateFlow<DetailDialog?>(null)
    private val effects = Channel<DetailEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    private val itemFlow = itemId.flatMapLatest { id -> if (id == null) kotlinx.coroutines.flow.flowOf(null) else vault.observeItem(id) }
    private val historyFlow = itemId.flatMapLatest { id -> if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else vault.observeHistory(id) }

    val state: StateFlow<DetailUiState> = combine(itemFlow, historyFlow, settings.settings, family.observeAll(), dialog) { item, history, s, profiles, d ->
        DetailUiState(
            item = item,
            owner = profiles.firstOrNull { it.id == item?.ownerId },
            history = history,
            settings = s,
            dialog = d,
            nowMillis = clock.nowMillis(),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun load(id: String) { itemId.value = id }

    fun copy(value: String, isPassword: Boolean) {
        clipboard.copySecret(value, state.value.settings.clipboardClearSeconds)
        viewModelScope.launch {
            effects.send(DetailEffect.Snack(if (isPassword) com.rombamukiyam.vault.R.string.detail_password_copied else com.rombamukiyam.vault.R.string.detail_copied))
        }
    }

    fun openWebsite() {
        val url = state.value.item?.officialWebsite ?: return
        val normalised = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        viewModelScope.launch { effects.send(DetailEffect.OpenUrl(normalised)) }
    }

    fun toggleFavorite() {
        val item = state.value.item ?: return
        viewModelScope.launch { toggleFavorite.invoke(item.id, !item.favorite).onFailure { effects.send(DetailEffect.Error(it)) } }
    }

    fun markReviewed() {
        val item = state.value.item ?: return
        viewModelScope.launch { markReviewed.invoke(item.id).onFailure { effects.send(DetailEffect.Error(it)) } }
    }

    fun askTrash() { dialog.value = DetailDialog.Trash }
    fun askDeleteForever() { dialog.value = DetailDialog.DeleteForever }
    fun dismissDialog() { dialog.value = null }

    fun confirmDialog() {
        val item = state.value.item ?: return
        val d = dialog.value ?: return
        dialog.value = null
        viewModelScope.launch {
            val result: Outcome<Unit> = when (d) {
                DetailDialog.Trash -> moveToTrash(item.id)
                DetailDialog.DeleteForever -> deleteForever(item.id)
            }
            when (result) {
                is Outcome.Success -> effects.send(DetailEffect.Closed)
                is Outcome.Failure -> effects.send(DetailEffect.Error(result.error))
            }
        }
    }

    fun restoreFromTrash() {
        val item = state.value.item ?: return
        viewModelScope.launch { restore.invoke(item.id).onFailure { effects.send(DetailEffect.Error(it)) } }
    }
}
