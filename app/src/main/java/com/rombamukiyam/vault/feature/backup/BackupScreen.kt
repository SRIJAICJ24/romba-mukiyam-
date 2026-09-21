package com.rombamukiyam.vault.feature.backup

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.backup.RmvaultFormat
import com.rombamukiyam.vault.ui.components.AttentionCard
import com.rombamukiyam.vault.ui.components.AttentionLevel
import com.rombamukiyam.vault.ui.components.ConfirmationDialog
import com.rombamukiyam.vault.ui.components.Dates
import com.rombamukiyam.vault.ui.components.PassphraseField
import com.rombamukiyam.vault.ui.components.PrimaryButton
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.SecondaryButton
import com.rombamukiyam.vault.ui.components.SectionHeader
import com.rombamukiyam.vault.ui.components.daysAgo
import com.rombamukiyam.vault.ui.components.message

private fun displayNameOf(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()

@Composable
fun BackupScreen(safeMode: Boolean, onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val now = System.currentTimeMillis()

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(RmvaultFormat.MIME)) { uri ->
        viewModel.onFilePicked(BackupOperation.CREATE, uri, uri?.let { displayNameOf(context, it) })
    }
    val verifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onFilePicked(BackupOperation.VERIFY, uri, uri?.let { displayNameOf(context, it) })
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onFilePicked(BackupOperation.RESTORE, uri, uri?.let { displayNameOf(context, it) })
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RombaTopBar(stringResource(R.string.backup_title), onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                val last = state.lastBackupAt
                AttentionCard(
                    title = if (last == null) stringResource(R.string.home_backup_never) else stringResource(R.string.backup_last, daysAgo(last, now)),
                    body = stringResource(R.string.backup_intro),
                    level = if (last == null) AttentionLevel.ATTENTION else AttentionLevel.SAFE,
                    icon = Icons.Filled.Backup,
                )
            }
            item { PrimaryButton(stringResource(R.string.backup_create), onClick = { createLauncher.launch(RmvaultFormat.suggestedFileName(now)) }, icon = Icons.Filled.Backup) }
            item { SecondaryButton(stringResource(R.string.backup_verify), onClick = { verifyLauncher.launch(arrayOf("*/*")) }, icon = Icons.Filled.VerifiedUser) }
            item { SecondaryButton(stringResource(R.string.backup_restore), onClick = { restoreLauncher.launch(arrayOf("*/*")) }, icon = Icons.Filled.Restore) }
            item { Text(stringResource(R.string.backup_where), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            if (state.history.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.backup_history)) }
                items(state.history, key = { it.id }) { r ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(if (r.event == "RESTORED") stringResource(R.string.backup_event_restored) else stringResource(R.string.backup_event_created), style = MaterialTheme.typography.bodyLarge)
                            Text((r.displayName ?: "") + " · " + stringResource(R.string.backup_items, r.itemCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Dates.format(r.createdAt) ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val pending = state.pending
    if (pending != null && state.confirmRestore) {
        ConfirmationDialog(
            title = stringResource(R.string.restore_warning_title),
            text = stringResource(R.string.restore_warning_body),
            confirmText = stringResource(R.string.action_continue),
            onConfirm = viewModel::acknowledgeRestoreWarning,
            onDismiss = viewModel::cancelPending,
            destructive = true,
        )
    } else if (pending != null) {
        PassphraseDialog(pending, state, viewModel)
    }

    state.message?.let { m -> if (state.pending == null) ResultDialog(m, viewModel::dismissMessage) }
}

@Composable
private fun PassphraseDialog(pending: PendingOperation, state: BackupUiState, vm: BackupViewModel) {
    val title = when (pending.operation) {
        BackupOperation.CREATE -> R.string.backup_create
        BackupOperation.VERIFY -> R.string.backup_verify
        BackupOperation.RESTORE -> R.string.backup_restore
    }
    val hint = when (pending.operation) {
        BackupOperation.CREATE -> R.string.backup_pass_hint_create
        else -> R.string.backup_pass_hint_open
    }
    val failed = (state.message as? BackupOutcomeMessage.Failed)
    AlertDialog(
        onDismissRequest = { if (!state.busy) vm.cancelPending() },
        title = { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                pending.displayName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                PassphraseField(
                    value = state.passphrase,
                    onValueChange = vm::onPassphraseChange,
                    label = stringResource(R.string.label_master_passphrase),
                    enabled = !state.busy,
                    isError = failed != null,
                    supportingText = failed?.error?.message(),
                    onImeAction = vm::run,
                )
                if (state.busy) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::run, enabled = state.passphrase.isNotEmpty() && !state.busy) { Text(stringResource(R.string.action_continue)) } },
        dismissButton = { TextButton(onClick = vm::cancelPending, enabled = !state.busy) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ResultDialog(m: BackupOutcomeMessage, onDismiss: () -> Unit) {
    val (title, body) = when (m) {
        is BackupOutcomeMessage.Created -> stringResource(R.string.backup_verified_title) to stringResource(R.string.backup_created_body, m.result.itemCount)
        is BackupOutcomeMessage.Verified -> stringResource(R.string.backup_verified_title) to stringResource(R.string.backup_verified_body, m.info.itemCount, m.info.profileCount, Dates.format(m.info.createdAt) ?: "")
        is BackupOutcomeMessage.Restored -> stringResource(R.string.restore_done_title) to stringResource(R.string.restore_done_body, m.result.itemCount)
        is BackupOutcomeMessage.Failed -> stringResource(
            when (m.operation) {
                BackupOperation.CREATE -> R.string.backup_failed_title
                BackupOperation.VERIFY -> R.string.verify_failed_title
                BackupOperation.RESTORE -> R.string.restore_failed_title
            },
        ) to m.error.message()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
