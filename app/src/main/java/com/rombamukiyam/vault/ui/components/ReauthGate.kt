package com.rombamukiyam.vault.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.common.DispatcherProvider
import com.rombamukiyam.vault.core.security.BiometricAuthenticator
import com.rombamukiyam.vault.core.security.VaultKeyManager
import com.rombamukiyam.vault.core.crypto.wipe
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Re-authentication for High Security items. Remembers a successful confirmation for a short grace period
 * so a user reading one bank record is not prompted for every single field.
 */
@HiltViewModel
class ReauthViewModel @Inject constructor(
    private val biometrics: BiometricAuthenticator,
    private val keys: VaultKeyManager,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private var lastConfirmedAt: Long = 0L
    var passphraseDialogVisible by mutableStateOf(false)
        private set
    private var pendingGrant: (() -> Unit)? = null
    var passphraseError by mutableStateOf(false)
        private set

    suspend fun isRequired(highSecurity: Boolean): Boolean {
        if (!highSecurity) return false
        if (!settings.settings.first().requireReauthForHighSecurity) return false
        return clock.nowMillis() - lastConfirmedAt > GRACE_MILLIS
    }

    fun require(activity: FragmentActivity?, highSecurity: Boolean, title: String, subtitle: String, onGranted: () -> Unit) {
        viewModelScope.launch {
            if (!isRequired(highSecurity)) {
                onGranted(); return@launch
            }
            if (activity != null && biometrics.canConfirmIdentity()) {
                if (biometrics.confirmIdentity(activity, title, subtitle)) {
                    lastConfirmedAt = clock.nowMillis()
                    onGranted()
                }
            } else {
                pendingGrant = onGranted
                passphraseError = false
                passphraseDialogVisible = true
            }
        }
    }

    fun submitPassphrase(passphrase: String) {
        viewModelScope.launch {
            val ok = withContext(dispatchers.default) {
                val bytes = passphrase.toByteArray(Charsets.UTF_8)
                try {
                    keys.unlockWithPassphrase(bytes)?.also { it.wipe() } != null
                } finally {
                    bytes.wipe()
                }
            }
            if (ok) {
                lastConfirmedAt = clock.nowMillis()
                passphraseDialogVisible = false
                pendingGrant?.invoke()
                pendingGrant = null
            } else {
                passphraseError = true
            }
        }
    }

    fun dismissPassphrase() {
        passphraseDialogVisible = false
        pendingGrant = null
    }

    companion object {
        const val GRACE_MILLIS = 30_000L
    }
}

/** Returns a gate function: `gate(onGranted)`; also hosts the passphrase fallback dialog. */
@Composable
fun rememberReauthGate(highSecurity: Boolean, viewModel: ReauthViewModel = hiltViewModel()): (onGranted: () -> Unit) -> Unit {
    val activity = LocalContext.current as? FragmentActivity
    val title = stringResource(R.string.bio_confirm_title)
    val subtitle = stringResource(R.string.bio_confirm_subtitle)
    if (viewModel.passphraseDialogVisible) {
        PassphraseConfirmDialog(
            isError = viewModel.passphraseError,
            onSubmit = viewModel::submitPassphrase,
            onDismiss = viewModel::dismissPassphrase,
        )
    }
    return remember(highSecurity, activity) {
        { onGranted -> viewModel.require(activity, highSecurity, title, subtitle, onGranted) }
    }
}

@Composable
private fun PassphraseConfirmDialog(isError: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bio_confirm_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(stringResource(R.string.bio_confirm_subtitle), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                PassphraseField(
                    value = value,
                    onValueChange = { value = it },
                    label = stringResource(R.string.label_master_passphrase),
                    isError = isError,
                    supportingText = if (isError) stringResource(R.string.err_auth_failed) else null,
                    onImeAction = { scope.launch { onSubmit(value); value = "" } },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(value); value = "" }, enabled = value.isNotEmpty()) {
                Text(stringResource(R.string.action_continue))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
