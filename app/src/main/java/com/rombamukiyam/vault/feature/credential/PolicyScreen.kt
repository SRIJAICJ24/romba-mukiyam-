package com.rombamukiyam.vault.feature.credential

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.domain.model.PasswordPolicy
import com.rombamukiyam.vault.domain.repository.VaultRepository
import com.rombamukiyam.vault.domain.usecase.DeletePasswordPolicyUseCase
import com.rombamukiyam.vault.domain.usecase.SavePasswordPolicyUseCase
import com.rombamukiyam.vault.ui.components.AttentionCard
import com.rombamukiyam.vault.ui.components.AttentionLevel
import com.rombamukiyam.vault.ui.components.Dates
import com.rombamukiyam.vault.ui.components.LinkButton
import com.rombamukiyam.vault.ui.components.PrimaryButton
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.message
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PolicyUiState(
    val itemId: String = "",
    val minLength: String = "",
    val maxLength: String = "",
    val requireUppercase: Boolean = false,
    val requireLowercase: Boolean = false,
    val requireDigit: Boolean = false,
    val requireSymbol: Boolean = false,
    val allowedSymbols: String = "",
    val forbiddenCharacters: String = "",
    val notes: String = "",
    val policySource: String = "",
    val lastVerifiedAt: Long? = null,
    val exists: Boolean = false,
    val saving: Boolean = false,
    val loaded: Boolean = false,
    val error: VaultError? = null,
)

@HiltViewModel
class PolicyViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val save: SavePasswordPolicyUseCase,
    private val delete: DeletePasswordPolicyUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(PolicyUiState())
    val state: StateFlow<PolicyUiState> = _state.asStateFlow()
    private val done = Channel<Unit>(Channel.BUFFERED)
    val doneEvents = done.receiveAsFlow()
    private var loadedId: String? = null

    fun load(itemId: String) {
        if (loadedId == itemId) return
        loadedId = itemId
        viewModelScope.launch {
            val p = vault.getPolicy(itemId)
            _state.value = if (p == null) PolicyUiState(itemId = itemId, loaded = true) else PolicyUiState(
                itemId = itemId, minLength = p.minLength?.toString() ?: "", maxLength = p.maxLength?.toString() ?: "",
                requireUppercase = p.requireUppercase, requireLowercase = p.requireLowercase, requireDigit = p.requireDigit,
                requireSymbol = p.requireSymbol, allowedSymbols = p.allowedSymbols ?: "", forbiddenCharacters = p.forbiddenCharacters ?: "",
                notes = p.notes ?: "", policySource = p.policySource ?: "", lastVerifiedAt = p.lastVerifiedAt, exists = true, loaded = true,
            )
        }
    }

    fun update(block: (PolicyUiState) -> PolicyUiState) = _state.update { block(it).copy(error = null) }

    fun markVerified() = update { it.copy(lastVerifiedAt = clock.nowMillis()) }

    fun savePolicy() {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        val policy = PasswordPolicy(
            itemId = s.itemId,
            minLength = s.minLength.toIntOrNull(), maxLength = s.maxLength.toIntOrNull(),
            requireUppercase = s.requireUppercase, requireLowercase = s.requireLowercase, requireDigit = s.requireDigit, requireSymbol = s.requireSymbol,
            allowedSymbols = s.allowedSymbols.ifBlank { null }, forbiddenCharacters = s.forbiddenCharacters.ifEmpty { null },
            notes = s.notes.ifBlank { null }, policySource = s.policySource.ifBlank { null }, lastVerifiedAt = s.lastVerifiedAt,
        )
        viewModelScope.launch {
            when (val r = save(policy)) {
                is Outcome.Success -> done.send(Unit)
                is Outcome.Failure -> _state.update { it.copy(saving = false, error = r.error) }
            }
        }
    }

    fun removePolicy() = viewModelScope.launch { delete(_state.value.itemId); done.send(Unit) }
}

@Composable
fun PolicyScreen(itemId: String, onBack: () -> Unit, viewModel: PolicyViewModel = hiltViewModel()) {
    LaunchedEffect(itemId) { viewModel.load(itemId) }
    val s by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.doneEvents.collect { onBack() } }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RombaTopBar(stringResource(R.string.policy_title), onBack = onBack) }) { padding ->
        if (!s.loaded) return@Scaffold
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AttentionCard(stringResource(R.string.policy_intro_title), stringResource(R.string.policy_intro_body), AttentionLevel.INFO)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(s.minLength, { v -> viewModel.update { it.copy(minLength = v.filter(Char::isDigit).take(3)) } }, label = { Text(stringResource(R.string.policy_min_length)) }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(s.maxLength, { v -> viewModel.update { it.copy(maxLength = v.filter(Char::isDigit).take(3)) } }, label = { Text(stringResource(R.string.policy_max_length)) }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            CheckRow(stringResource(R.string.pv_uppercase), s.requireUppercase) { v -> viewModel.update { it.copy(requireUppercase = v) } }
            CheckRow(stringResource(R.string.pv_lowercase), s.requireLowercase) { v -> viewModel.update { it.copy(requireLowercase = v) } }
            CheckRow(stringResource(R.string.pv_digit), s.requireDigit) { v -> viewModel.update { it.copy(requireDigit = v) } }
            CheckRow(stringResource(R.string.pv_symbol), s.requireSymbol) { v -> viewModel.update { it.copy(requireSymbol = v) } }
            OutlinedTextField(s.allowedSymbols, { v -> viewModel.update { it.copy(allowedSymbols = v.take(40)) } }, label = { Text(stringResource(R.string.policy_allowed_symbols)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.forbiddenCharacters, { v -> viewModel.update { it.copy(forbiddenCharacters = v.take(40)) } }, label = { Text(stringResource(R.string.policy_forbidden)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.policySource, { v -> viewModel.update { it.copy(policySource = v.take(120)) } }, label = { Text(stringResource(R.string.policy_source)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(s.notes, { v -> viewModel.update { it.copy(notes = v.take(500)) } }, label = { Text(stringResource(R.string.form_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.policy_last_verified, Dates.format(s.lastVerifiedAt) ?: stringResource(R.string.detail_never)), style = MaterialTheme.typography.bodyMedium)
                LinkButton(stringResource(R.string.policy_mark_verified), onClick = viewModel::markVerified)
            }
            s.error?.let { Text(if (it is VaultError.ValidationFailed) stringResource(R.string.policy_err_min_gt_max) else it.message(), color = MaterialTheme.colorScheme.error) }
            PrimaryButton(stringResource(R.string.action_save), onClick = viewModel::savePolicy, loading = s.saving)
            if (s.exists) LinkButton(stringResource(R.string.policy_remove), onClick = viewModel::removePolicy)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
