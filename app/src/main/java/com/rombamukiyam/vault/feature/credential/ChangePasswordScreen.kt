package com.rombamukiyam.vault.feature.credential

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.common.Outcome
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.core.crypto.PasswordStrength
import com.rombamukiyam.vault.core.crypto.StrengthReport
import com.rombamukiyam.vault.core.security.SecureClipboard
import com.rombamukiyam.vault.domain.model.CredentialField
import com.rombamukiyam.vault.domain.model.FieldType
import com.rombamukiyam.vault.domain.model.PasswordPolicy
import com.rombamukiyam.vault.domain.model.VaultItem
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.repository.VaultRepository
import com.rombamukiyam.vault.domain.usecase.GetVaultItemUseCase
import com.rombamukiyam.vault.domain.usecase.UpdatePasswordUseCase
import com.rombamukiyam.vault.ui.components.AttentionCard
import com.rombamukiyam.vault.ui.components.AttentionLevel
import com.rombamukiyam.vault.ui.components.PassphraseField
import com.rombamukiyam.vault.ui.components.PrimaryButton
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.SecondaryButton
import com.rombamukiyam.vault.ui.components.SectionHeader
import com.rombamukiyam.vault.ui.components.message
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val item: VaultItem? = null,
    val passwordFields: List<CredentialField> = emptyList(),
    val selectedFieldId: String? = null,
    val loginField: CredentialField? = null,
    val policy: PasswordPolicy? = null,
    val newPassword: String = "",
    val strength: StrengthReport? = null,
    val violations: List<String> = emptyList(),
    val confirmedChangedOnSite: Boolean = false,
    val allowReuse: Boolean = false,
    val saving: Boolean = false,
    val error: VaultError? = null,
    val loaded: Boolean = false,
)

sealed class ChangePasswordEffect {
    data object Done : ChangePasswordEffect()
    data class Snack(val res: Int) : ChangePasswordEffect()
    data class OpenUrl(val url: String) : ChangePasswordEffect()
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val getItem: GetVaultItemUseCase,
    private val vault: VaultRepository,
    private val updatePassword: UpdatePasswordUseCase,
    private val clipboard: SecureClipboard,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()
    private val effects = Channel<ChangePasswordEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()
    private var loadedId: String? = null

    fun load(itemId: String) {
        if (loadedId == itemId) return
        loadedId = itemId
        viewModelScope.launch {
            val item = getItem(itemId).getOrNull()
            val policy = vault.getPolicy(itemId)
            val pwFields = item?.fields?.filter { it.type in PASSWORD_TYPES }.orEmpty()
            val login = item?.fields?.firstOrNull { it.type in setOf(FieldType.USERNAME, FieldType.LOGIN_ID, FieldType.CUSTOMER_ID, FieldType.EMAIL) }
            _state.update {
                it.copy(item = item, passwordFields = pwFields, selectedFieldId = pwFields.firstOrNull()?.id, loginField = login, policy = policy, loaded = true)
            }
        }
    }

    fun selectField(id: String) = _state.update { it.copy(selectedFieldId = id) }

    fun onPasswordChange(v: String) = _state.update {
        it.copy(newPassword = v, strength = if (v.isEmpty()) null else PasswordStrength.evaluate(v), violations = it.policy?.violations(v).orEmpty(), error = null)
    }

    fun onConfirmedChange(v: Boolean) = _state.update { it.copy(confirmedChangedOnSite = v) }
    fun onAllowReuse(v: Boolean) = _state.update { it.copy(allowReuse = v) }

    fun copyLogin() {
        val login = _state.value.loginField ?: return
        viewModelScope.launch {
            clipboard.copySecret(login.value, settings.settings.first().clipboardClearSeconds)
            effects.send(ChangePasswordEffect.Snack(R.string.detail_copied))
        }
    }

    fun copyNewPassword() {
        val pw = _state.value.newPassword
        if (pw.isEmpty()) return
        viewModelScope.launch {
            clipboard.copySecret(pw, settings.settings.first().clipboardClearSeconds)
            effects.send(ChangePasswordEffect.Snack(R.string.detail_password_copied))
        }
    }

    fun openWebsite() {
        val url = _state.value.item?.officialWebsite ?: return
        viewModelScope.launch { effects.send(ChangePasswordEffect.OpenUrl(if (url.startsWith("http")) url else "https://$url")) }
    }

    fun save() {
        val s = _state.value
        val fieldId = s.selectedFieldId ?: return
        val item = s.item ?: return
        if (s.saving || !s.confirmedChangedOnSite || s.newPassword.isEmpty()) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val r = updatePassword(item.id, fieldId, s.newPassword, allowReuse = s.allowReuse)) {
                is Outcome.Success -> {
                    _state.update { it.copy(newPassword = "", strength = null, saving = false) }
                    effects.send(ChangePasswordEffect.Done)
                }
                is Outcome.Failure -> _state.update { it.copy(saving = false, error = r.error) }
            }
        }
    }

    fun clearSensitive() = _state.update { it.copy(newPassword = "", strength = null) }

    companion object {
        val PASSWORD_TYPES = setOf(FieldType.PASSWORD, FieldType.PROFILE_PASSWORD, FieldType.TRANSACTION_PASSWORD, FieldType.WIFI_PASSWORD, FieldType.CUSTOM_SECRET)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(itemId: String, onBack: () -> Unit, viewModel: ChangePasswordViewModel = hiltViewModel()) {
    LaunchedEffect(itemId) { viewModel.load(itemId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { /* keep the typed password while the user visits the bank site */ }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { e ->
            when (e) {
                ChangePasswordEffect.Done -> onBack()
                is ChangePasswordEffect.Snack -> snackbar.showSnackbar(resources.getString(e.res))
                is ChangePasswordEffect.OpenUrl -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { RombaTopBar(stringResource(R.string.detail_change_password), onBack = onBack) },
    ) { padding ->
        val item = state.item
        if (!state.loaded || item == null) return@Scaffold
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleLarge)
            AttentionCard(stringResource(R.string.change_intro_title), stringResource(R.string.change_intro_body), AttentionLevel.INFO)

            if (state.passwordFields.size > 1) {
                SectionHeader(stringResource(R.string.change_which_password))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.passwordFields.forEach { f ->
                        FilterChip(selected = state.selectedFieldId == f.id, onClick = { viewModel.selectField(f.id) }, label = { Text(f.label) })
                    }
                }
            }

            SectionHeader(stringResource(R.string.change_step1))
            PassphraseField(
                value = state.newPassword,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.change_new_password),
                strength = state.strength,
                isError = state.violations.isNotEmpty(),
                supportingText = state.violations.firstOrNull()?.let { policyViolationText(it, state.policy) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordGeneratorEntry(onGenerated = viewModel::onPasswordChange)
            }
            state.policy?.let { PolicySummary(it) }

            SectionHeader(stringResource(R.string.change_step2))
            state.loginField?.let { login ->
                SecondaryButton(stringResource(R.string.change_copy_login, login.label), onClick = viewModel::copyLogin, icon = Icons.Filled.ContentCopy)
            }
            SecondaryButton(stringResource(R.string.change_copy_new), onClick = viewModel::copyNewPassword, icon = Icons.Filled.ContentCopy, enabled = state.newPassword.isNotEmpty())
            if (!item.officialWebsite.isNullOrBlank()) {
                SecondaryButton(stringResource(R.string.action_open_website), onClick = viewModel::openWebsite, icon = Icons.Filled.OpenInBrowser)
                Text(item.officialDomain ?: item.officialWebsite, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.change_manual_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionHeader(stringResource(R.string.change_step3))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = state.confirmedChangedOnSite, onCheckedChange = viewModel::onConfirmedChange)
                Text(stringResource(R.string.change_confirm_site), style = MaterialTheme.typography.bodyLarge)
            }
            state.error?.let { err ->
                val reused = err is VaultError.ValidationFailed && err.messages.firstOrNull() == "reused"
                if (reused) {
                    AttentionCard(stringResource(R.string.change_reused_title), stringResource(R.string.change_reused_body), AttentionLevel.ATTENTION)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.allowReuse, onCheckedChange = viewModel::onAllowReuse)
                        Text(stringResource(R.string.change_reuse_anyway), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(if (err is VaultError.ValidationFailed) policyViolationText(err.messages.firstOrNull() ?: "", state.policy) else err.message(), color = MaterialTheme.colorScheme.error)
                }
            }
            PrimaryButton(
                stringResource(R.string.change_save),
                onClick = viewModel::save,
                enabled = state.confirmedChangedOnSite && state.newPassword.isNotEmpty() && state.violations.isEmpty(),
                loading = state.saving,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun policyViolationText(key: String, policy: PasswordPolicy?): String = when (key) {
    "min_length" -> stringResource(R.string.pv_min_length, policy?.minLength ?: 0)
    "max_length" -> stringResource(R.string.pv_max_length, policy?.maxLength ?: 0)
    "uppercase" -> stringResource(R.string.pv_uppercase)
    "lowercase" -> stringResource(R.string.pv_lowercase)
    "digit" -> stringResource(R.string.pv_digit)
    "symbol" -> stringResource(R.string.pv_symbol)
    "disallowed_symbol" -> stringResource(R.string.pv_disallowed_symbol, policy?.allowedSymbols ?: "")
    "forbidden_char" -> stringResource(R.string.pv_forbidden, policy?.forbiddenCharacters ?: "")
    "reused" -> stringResource(R.string.change_reused_title)
    "empty" -> stringResource(R.string.err_validation)
    else -> stringResource(R.string.err_validation)
}

@Composable
fun PolicySummary(p: PasswordPolicy) {
    val parts = buildList {
        p.minLength?.let { add(stringResource(R.string.policy_min, it)) }
        p.maxLength?.let { add(stringResource(R.string.policy_max, it)) }
        if (p.requireUppercase) add(stringResource(R.string.pv_uppercase))
        if (p.requireLowercase) add(stringResource(R.string.pv_lowercase))
        if (p.requireDigit) add(stringResource(R.string.pv_digit))
        if (p.requireSymbol) add(stringResource(R.string.pv_symbol))
        p.allowedSymbols?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.policy_allowed, it)) }
    }
    if (parts.isNotEmpty()) {
        Text(stringResource(R.string.policy_summary_prefix) + " " + parts.joinToString("; "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
