package com.rombamukiyam.vault.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.crypto.RecoveryKeyCodec
import com.rombamukiyam.vault.domain.model.Relationship
import com.rombamukiyam.vault.ui.components.AttentionCard
import com.rombamukiyam.vault.ui.components.AttentionLevel
import com.rombamukiyam.vault.ui.components.LinkButton
import com.rombamukiyam.vault.ui.components.PassphraseField
import com.rombamukiyam.vault.ui.components.PrimaryButton
import com.rombamukiyam.vault.ui.components.SecondaryButton
import com.rombamukiyam.vault.ui.components.message
import com.rombamukiyam.vault.ui.theme.ProfileColors

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.clearSensitiveInput() }
    BackHandler(enabled = viewModel.canGoBack) { viewModel.back() }

    if (state.step == OnboardingStep.DONE) {
        onFinished()
        return
    }

    val progress = (state.step.ordinal + 1f) / OnboardingStep.entries.size
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp))
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                when (state.step) {
                    OnboardingStep.WELCOME -> Welcome(viewModel)
                    OnboardingStep.LOCAL_PROMISE -> LocalPromise(viewModel)
                    OnboardingStep.CREATE_PASSPHRASE -> CreatePassphrase(state, viewModel)
                    OnboardingStep.CONFIRM_PASSPHRASE -> ConfirmPassphrase(state, viewModel)
                    OnboardingStep.GENERATING -> Generating()
                    OnboardingStep.RECOVERY_KIT -> RecoveryKit(state, viewModel)
                    OnboardingStep.BIOMETRIC -> Biometric(state, viewModel)
                    OnboardingStep.FIRST_PROFILE -> FirstProfile(state, viewModel)
                    OnboardingStep.BACKUP_REMINDER -> BackupReminder(state, viewModel)
                    OnboardingStep.DONE -> Unit
                }
                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, body: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    if (body != null) {
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun Welcome(vm: OnboardingViewModel) {
    Spacer(Modifier.height(48.dp))
    Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(24.dp))
    StepTitle(stringResource(R.string.ob_welcome_title), stringResource(R.string.ob_welcome_body))
    Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(40.dp))
    PrimaryButton(stringResource(R.string.action_get_started), onClick = vm::next)
}

@Composable
private fun LocalPromise(vm: OnboardingViewModel) {
    StepTitle(stringResource(R.string.ob_local_title))
    listOf(R.string.ob_local_point1, R.string.ob_local_point2, R.string.ob_local_point3, R.string.ob_local_point4).forEach { res ->
        Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(12.dp))
            Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
        }
    }
    Spacer(Modifier.height(16.dp))
    AttentionCard(title = stringResource(R.string.ob_local_warning_title), body = stringResource(R.string.ob_local_warning_body), level = AttentionLevel.ATTENTION)
    Spacer(Modifier.height(32.dp))
    PrimaryButton(stringResource(R.string.action_continue), onClick = vm::next)
}

@Composable
private fun CreatePassphrase(state: OnboardingUiState, vm: OnboardingViewModel) {
    StepTitle(stringResource(R.string.ob_pass_title), stringResource(R.string.ob_pass_body))
    PassphraseField(
        value = state.passphrase,
        onValueChange = vm::onPassphraseChange,
        label = stringResource(R.string.label_master_passphrase),
        strength = state.strength,
        imeAction = ImeAction.Next,
        onImeAction = { if (vm.passphraseAcceptable()) vm.next() },
        supportingText = stringResource(R.string.ob_pass_min, OnboardingViewModel.MIN_LENGTH),
    )
    Spacer(Modifier.height(24.dp))
    PrimaryButton(stringResource(R.string.action_continue), onClick = vm::next, enabled = vm.passphraseAcceptable())
}

@Composable
private fun ConfirmPassphrase(state: OnboardingUiState, vm: OnboardingViewModel) {
    StepTitle(stringResource(R.string.ob_confirm_title), stringResource(R.string.ob_confirm_body))
    PassphraseField(
        value = state.passphraseConfirm,
        onValueChange = vm::onPassphraseConfirmChange,
        label = stringResource(R.string.label_confirm_passphrase),
        isError = state.passphraseConfirm.isNotEmpty() && !vm.confirmMatches(),
        supportingText = if (state.passphraseConfirm.isNotEmpty() && !vm.confirmMatches()) stringResource(R.string.ob_confirm_mismatch) else null,
        onImeAction = { if (vm.confirmMatches()) vm.next() },
    )
    Spacer(Modifier.height(24.dp))
    PrimaryButton(stringResource(R.string.ob_create_vault), onClick = vm::next, enabled = vm.confirmMatches(), loading = state.busy)
}

@Composable
private fun Generating() {
    Spacer(Modifier.height(80.dp))
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.ob_generating_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ob_generating_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RecoveryKeyCard(recoveryKey: String, modifier: Modifier = Modifier) {
    val lines = RecoveryKeyCodec.displayLines(recoveryKey)
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.recovery_key_heading), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(12.dp))
            Text(RecoveryKeyCodec.PREFIX + "-", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onPrimaryContainer)
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RecoveryKit(state: OnboardingUiState, vm: OnboardingViewModel) {
    StepTitle(stringResource(R.string.ob_recovery_title), stringResource(R.string.ob_recovery_body))
    state.recoveryKey?.let { RecoveryKeyCard(it) }
    Spacer(Modifier.height(16.dp))
    AttentionCard(title = stringResource(R.string.ob_recovery_warning_title), body = stringResource(R.string.ob_recovery_warning_body), level = AttentionLevel.CRITICAL)
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth().clickable { vm.acknowledgeRecovery(!state.recoveryAcknowledged) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = state.recoveryAcknowledged, onCheckedChange = vm::acknowledgeRecovery)
        Text(stringResource(R.string.ob_recovery_ack), style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(Modifier.height(16.dp))
    PrimaryButton(stringResource(R.string.action_continue), onClick = vm::next, enabled = state.recoveryAcknowledged)
}

@Composable
private fun Biometric(state: OnboardingUiState, vm: OnboardingViewModel) {
    val activity = LocalContext.current as? FragmentActivity
    val title = stringResource(R.string.bio_enroll_title)
    val subtitle = stringResource(R.string.bio_enroll_subtitle)
    val negative = stringResource(R.string.action_cancel)
    StepTitle(stringResource(R.string.ob_bio_title), stringResource(R.string.ob_bio_body))
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(24.dp))
    if (state.biometricAvailable) {
        PrimaryButton(stringResource(R.string.ob_bio_enable), onClick = { activity?.let { vm.enableFingerprint(it, title, subtitle, negative) } }, loading = state.busy, icon = Icons.Filled.Fingerprint)
        Spacer(Modifier.height(12.dp))
        SecondaryButton(stringResource(R.string.action_skip_for_now), onClick = vm::next, enabled = !state.busy)
    } else {
        AttentionCard(title = stringResource(R.string.ob_bio_unavailable_title), body = stringResource(R.string.ob_bio_unavailable_body), level = AttentionLevel.INFO)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.action_continue), onClick = vm::next)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FirstProfile(state: OnboardingUiState, vm: OnboardingViewModel) {
    StepTitle(stringResource(R.string.ob_profile_title), stringResource(R.string.ob_profile_body))
    OutlinedTextField(
        value = state.profileName,
        onValueChange = vm::onProfileNameChange,
        label = { Text(stringResource(R.string.label_profile_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
    )
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.label_relationship), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    RelationshipChips(selected = state.profileRelationship, onSelect = vm::onProfileRelationshipChange)
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.label_colour), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    ColorPicker(selected = state.profileColor, onSelect = vm::onProfileColorChange)
    Spacer(Modifier.height(24.dp))
    PrimaryButton(stringResource(R.string.action_continue), onClick = vm::next, enabled = state.profileName.isNotBlank(), loading = state.busy)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationshipChips(selected: Relationship, onSelect: (Relationship) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Relationship.entries.forEach { r ->
            FilterChip(selected = r == selected, onClick = { onSelect(r) }, label = { Text(relationshipLabel(r)) })
        }
    }
}

@Composable
fun relationshipLabel(r: Relationship): String = stringResource(
    when (r) {
        Relationship.DAD -> R.string.rel_dad
        Relationship.MOM -> R.string.rel_mom
        Relationship.ME -> R.string.rel_me
        Relationship.SIBLING -> R.string.rel_sibling
        Relationship.JOINT -> R.string.rel_joint
        Relationship.OTHER -> R.string.rel_other
    },
)

@Composable
fun ColorPicker(selected: Long, onSelect: (Long) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileColors.forEach { c ->
            val isSelected = c == selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(c), CircleShape)
                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                    .clickable { onSelect(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupReminder(state: OnboardingUiState, vm: OnboardingViewModel) {
    StepTitle(stringResource(R.string.ob_backup_title), stringResource(R.string.ob_backup_body))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7, 14, 30, 60).forEach { d ->
            FilterChip(selected = state.backupReminderDays == d, onClick = { vm.onBackupReminderChange(d) }, label = { Text(stringResource(R.string.every_n_days, d)) })
        }
    }
    Spacer(Modifier.height(32.dp))
    PrimaryButton(stringResource(R.string.action_finish), onClick = vm::next, loading = state.busy)
    Spacer(Modifier.height(8.dp))
    LinkButton(stringResource(R.string.ob_backup_later), onClick = { vm.onBackupReminderChange(30); vm.next() })
}
