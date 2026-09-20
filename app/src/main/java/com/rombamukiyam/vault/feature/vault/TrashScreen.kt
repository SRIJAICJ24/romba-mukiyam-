package com.rombamukiyam.vault.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.domain.model.VaultItemSummary
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.repository.VaultRepository
import com.rombamukiyam.vault.domain.usecase.EmptyTrashUseCase
import com.rombamukiyam.vault.domain.usecase.PermanentlyDeleteVaultItemUseCase
import com.rombamukiyam.vault.domain.usecase.RestoreVaultItemUseCase
import com.rombamukiyam.vault.ui.components.ConfirmationDialog
import com.rombamukiyam.vault.ui.components.EmptyState
import com.rombamukiyam.vault.ui.components.LinkButton
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.VaultCard
import com.rombamukiyam.vault.ui.components.rememberReauthGate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class TrashDialog {
    data class DeleteOne(val id: String) : TrashDialog()
    data object EmptyAll : TrashDialog()
}

data class TrashUiState(val items: List<VaultItemSummary> = emptyList(), val retentionDays: Int = 30, val dialog: TrashDialog? = null, val nowMillis: Long = 0L)

@HiltViewModel
class TrashViewModel @Inject constructor(
    vault: VaultRepository,
    settings: SettingsRepository,
    private val restore: RestoreVaultItemUseCase,
    private val deleteOne: PermanentlyDeleteVaultItemUseCase,
    private val emptyTrash: EmptyTrashUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val dialog = MutableStateFlow<TrashDialog?>(null)

    val state: StateFlow<TrashUiState> = combine(vault.observeTrash(), settings.settings, dialog) { items, s, d ->
        TrashUiState(items, s.trashRetentionDays, d, clock.nowMillis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    fun restore(id: String) = viewModelScope.launch { restore.invoke(id) }
    fun askDelete(id: String) { dialog.value = TrashDialog.DeleteOne(id) }
    fun askEmpty() { dialog.value = TrashDialog.EmptyAll }
    fun dismiss() { dialog.value = null }
    fun confirm() {
        val d = dialog.value ?: return
        dialog.value = null
        viewModelScope.launch {
            when (d) {
                is TrashDialog.DeleteOne -> deleteOne(d.id)
                TrashDialog.EmptyAll -> emptyTrash()
            }
        }
    }
}

@Composable
fun TrashScreen(safeMode: Boolean, onBack: () -> Unit, onOpenItem: (String) -> Unit, viewModel: TrashViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Permanent deletion always requires re-authentication, regardless of item security level.
    val gate = rememberReauthGate(highSecurity = true)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RombaTopBar(stringResource(R.string.vault_trash), onBack = onBack) {
                if (!safeMode && state.items.isNotEmpty()) {
                    IconButton(onClick = viewModel::askEmpty) { Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.action_empty_trash)) }
                }
            }
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyState(title = stringResource(R.string.vault_trash_empty), body = stringResource(R.string.vault_trash_hint, state.retentionDays), modifier = Modifier.padding(padding))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                item {
                    Text(stringResource(R.string.vault_trash_hint, state.retentionDays), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(state.items, key = { it.id }) { item ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        VaultCard(item, state.nowMillis, onClick = { onOpenItem(item.id) })
                        if (!safeMode) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                LinkButton(stringResource(R.string.action_restore), onClick = { viewModel.restore(item.id) })
                                LinkButton(stringResource(R.string.action_delete_permanently), onClick = { viewModel.askDelete(item.id) })
                            }
                        }
                    }
                }
            }
        }
    }

    when (state.dialog) {
        is TrashDialog.DeleteOne -> ConfirmationDialog(
            title = stringResource(R.string.detail_delete_title),
            text = stringResource(R.string.detail_delete_body),
            confirmText = stringResource(R.string.action_delete_permanently),
            onConfirm = { gate { viewModel.confirm() } },
            onDismiss = viewModel::dismiss,
            destructive = true,
        )
        TrashDialog.EmptyAll -> ConfirmationDialog(
            title = stringResource(R.string.detail_empty_trash_title),
            text = stringResource(R.string.detail_empty_trash_body),
            confirmText = stringResource(R.string.action_empty_trash),
            onConfirm = { gate { viewModel.confirm() } },
            onDismiss = viewModel::dismiss,
            destructive = true,
        )
        null -> Unit
    }
}
