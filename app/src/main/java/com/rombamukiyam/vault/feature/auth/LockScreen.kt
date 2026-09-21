package com.rombamukiyam.vault.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.ui.components.AttentionCard
import com.rombamukiyam.vault.ui.components.AttentionLevel
import com.rombamukiyam.vault.ui.components.LinkButton
import com.rombamukiyam.vault.ui.components.PassphraseField
import com.rombamukiyam.vault.ui.components.PrimaryButton
import com.rombamukiyam.vault.ui.components.message

@Composable
fun LockScreen(viewModel: LockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val bioTitle = stringResource(R.string.bio_prompt_title)
    val bioSubtitle = stringResource(R.string.bio_prompt_subtitle)
    val bioNegative = stringResource(R.string.lock_use_passphrase)

    // Never keep a typed passphrase when the app leaves the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.clearSensitiveInput() }

    // Offer the fingerprint prompt automatically once on arrival if biometrics are enabled.
    LaunchedEffect(state.biometricAvailable) {
        if (state.biometricAvailable && state.mode == LockMode.BIOMETRIC && activity != null) {
            viewModel.unlockWithBiometric(activity, bioTitle, bioSubtitle, bioNegative)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_name).uppercase(), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            if (state.biometricInvalidated) {
                AttentionCard(
                    title = stringResource(R.string.lock_bio_invalidated_title),
                    body = stringResource(R.string.lock_bio_invalidated_body),
                    level = AttentionLevel.ATTENTION,
                )
                Spacer(Modifier.height(16.dp))
            }

            when (state.mode) {
                LockMode.BIOMETRIC -> BiometricSection(state) {
                    activity?.let { viewModel.unlockWithBiometric(it, bioTitle, bioSubtitle, bioNegative) }
                }
                LockMode.PASSPHRASE -> PassphraseSection(state, viewModel)
                LockMode.RECOVERY -> RecoverySection(state, viewModel)
            }

            state.error?.let { err ->
                Spacer(Modifier.height(16.dp))
                Text(err.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.mode != LockMode.PASSPHRASE) {
                    LinkButton(stringResource(R.string.lock_use_passphrase), onClick = { viewModel.switchMode(LockMode.PASSPHRASE) })
                }
                if (state.mode != LockMode.BIOMETRIC && state.biometricAvailable) {
                    LinkButton(stringResource(R.string.lock_use_fingerprint), onClick = { viewModel.switchMode(LockMode.BIOMETRIC) })
                }
                if (state.mode != LockMode.RECOVERY) {
                    LinkButton(stringResource(R.string.lock_recovery), onClick = { viewModel.switchMode(LockMode.RECOVERY) })
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BiometricSection(state: LockUiState, onUnlock: () -> Unit) {
    PrimaryButton(
        text = stringResource(R.string.lock_unlock_fingerprint),
        onClick = onUnlock,
        loading = state.busy,
        icon = Icons.Filled.Fingerprint,
    )
}

@Composable
private fun PassphraseSection(state: LockUiState, viewModel: LockViewModel) {
    if (state.offerSafeMode) {
        AttentionCard(
            title = stringResource(R.string.safe_mode_offer_title),
            body = stringResource(R.string.safe_mode_offer_body),
            level = AttentionLevel.CRITICAL,
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(checked = state.safeModeRequested, onCheckedChange = viewModel::requestSafeMode)
            Text(stringResource(R.string.safe_mode_open), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
    }
    PassphraseField(
        value = state.passphrase,
        onValueChange = viewModel::onPassphraseChange,
        label = stringResource(R.string.label_master_passphrase),
        enabled = !state.busy && state.cooldownSeconds == 0,
        isError = state.error != null,
        onImeAction = viewModel::unlockWithPassphrase,
    )
    Spacer(Modifier.height(16.dp))
    val label = if (state.cooldownSeconds > 0) {
        stringResource(R.string.lock_wait_seconds, state.cooldownSeconds)
    } else {
        stringResource(R.string.lock_unlock)
    }
    PrimaryButton(
        text = label,
        onClick = viewModel::unlockWithPassphrase,
        enabled = state.passphrase.isNotEmpty() && state.cooldownSeconds == 0,
        loading = state.busy,
    )
}

@Composable
private fun RecoverySection(state: LockUiState, viewModel: LockViewModel) {
    Text(stringResource(R.string.recovery_intro), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.recoveryKey,
        onValueChange = viewModel::onRecoveryKeyChange,
        label = { Text(stringResource(R.string.label_recovery_key)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        enabled = !state.busy,
    )
    Spacer(Modifier.height(12.dp))
    PassphraseField(
        value = state.newPassphrase,
        onValueChange = viewModel::onNewPassphraseChange,
        label = stringResource(R.string.label_new_master_passphrase),
        enabled = !state.busy,
        imeAction = ImeAction.Next,
        strength = state.newPassphraseStrength,
    )
    Spacer(Modifier.height(12.dp))
    PassphraseField(
        value = state.newPassphraseConfirm,
        onValueChange = viewModel::onNewPassphraseConfirmChange,
        label = stringResource(R.string.label_confirm_passphrase),
        enabled = !state.busy,
        isError = state.newPassphraseConfirm.isNotEmpty() && state.newPassphraseConfirm != state.newPassphrase,
        onImeAction = viewModel::recover,
    )
    Spacer(Modifier.height(16.dp))
    PrimaryButton(
        text = stringResource(R.string.recovery_action),
        onClick = viewModel::recover,
        enabled = state.recoveryKey.isNotBlank() && state.newPassphrase.length >= LockViewModel.MIN_PASSPHRASE && state.newPassphrase == state.newPassphraseConfirm,
        loading = state.busy,
    )
}
