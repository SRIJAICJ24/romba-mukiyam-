package com.rombamukiyam.vault.feature.credential

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.domain.model.CategoryTemplates
import com.rombamukiyam.vault.domain.model.FieldType
import com.rombamukiyam.vault.domain.model.VaultItem
import com.rombamukiyam.vault.ui.components.AttentionCard
import com.rombamukiyam.vault.ui.components.AttentionLevel
import com.rombamukiyam.vault.ui.components.ConfirmationDialog
import com.rombamukiyam.vault.ui.components.CredentialFieldRow
import com.rombamukiyam.vault.ui.components.DangerButton
import com.rombamukiyam.vault.ui.components.Dates
import com.rombamukiyam.vault.ui.components.EmptyState
import com.rombamukiyam.vault.ui.components.LinkButton
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.SecondaryButton
import com.rombamukiyam.vault.ui.components.SectionHeader
import com.rombamukiyam.vault.ui.components.SecurityBadge
import com.rombamukiyam.vault.ui.components.categoryLabel
import com.rombamukiyam.vault.ui.components.daysAgo
import com.rombamukiyam.vault.ui.components.historyEventLabel
import com.rombamukiyam.vault.ui.components.message
import com.rombamukiyam.vault.ui.components.relativeDays
import com.rombamukiyam.vault.ui.components.rememberReauthGate

@Composable
fun CredentialDetailScreen(
    itemId: String,
    safeMode: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onChangePassword: () -> Unit = {},
    onEditPolicy: () -> Unit = {},
    viewModel: CredentialDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) { viewModel.load(itemId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val gate = rememberReauthGate(highSecurity = state.item?.highSecurity == true)

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is DetailEffect.Snack -> snackbar.showSnackbar(resources.getString(effect.messageRes))
                is DetailEffect.OpenUrl -> runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effect.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                DetailEffect.Closed -> onBack()
                is DetailEffect.Error -> snackbar.showSnackbar(resources.getString(R.string.err_unknown))
            }
        }
    }

    val item = state.item
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            RombaTopBar(item?.title ?: "", onBack = onBack) {
                if (item != null && !safeMode && item.deletedAt == null) {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            if (item.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = stringResource(if (item.favorite) R.string.action_unfavourite else R.string.action_favourite),
                        )
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
                }
            }
        },
    ) { padding ->
        when {
            !state.loaded -> Unit
            item == null -> EmptyState(title = stringResource(R.string.detail_not_found), body = "", modifier = Modifier.padding(padding))
            else -> DetailBody(item, state, safeMode, gate, viewModel, onChangePassword, onEditPolicy, Modifier.padding(padding))
        }
    }

    when (state.dialog) {
        DetailDialog.Trash -> ConfirmationDialog(
            title = stringResource(R.string.detail_trash_title),
            text = stringResource(R.string.detail_trash_body, state.settings.trashRetentionDays),
            confirmText = stringResource(R.string.action_move_to_trash),
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
        )
        DetailDialog.DeleteForever -> ConfirmationDialog(
            title = stringResource(R.string.detail_delete_title),
            text = stringResource(R.string.detail_delete_body),
            confirmText = stringResource(R.string.action_delete_permanently),
            onConfirm = { gate { viewModel.confirmDialog() } },
            onDismiss = viewModel::dismissDialog,
            destructive = true,
        )
        null -> Unit
    }
}

@Composable
private fun DetailBody(
    item: VaultItem,
    state: DetailUiState,
    safeMode: Boolean,
    gate: (onGranted: () -> Unit) -> Unit,
    viewModel: CredentialDetailViewModel,
    onChangePassword: () -> Unit,
    onEditPolicy: () -> Unit,
    modifier: Modifier,
) {
    val noGate: (onGranted: () -> Unit) -> Unit = { it() }
    val secretGate = if (item.highSecurity) gate else noGate

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                SecurityBadge(categoryLabel(item.category), AttentionLevel.INFO)
                state.owner?.let { SecurityBadge(it.displayName, AttentionLevel.INFO) }
                if (item.highSecurity) SecurityBadge(stringResource(R.string.badge_high_security), AttentionLevel.SAFE)
                if (item.deletedAt != null) SecurityBadge(stringResource(R.string.badge_in_trash), AttentionLevel.ATTENTION)
            }
            item.institution?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        if (item.deletedAt != null) {
            item {
                AttentionCard(stringResource(R.string.badge_in_trash), stringResource(R.string.vault_trash_hint, state.settings.trashRetentionDays), AttentionLevel.ATTENTION)
                if (!safeMode) {
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(stringResource(R.string.action_restore), onClick = viewModel::restoreFromTrash)
                    Spacer(Modifier.height(8.dp))
                    DangerButton(stringResource(R.string.action_delete_permanently), onClick = viewModel::askDeleteForever)
                }
            }
        }

        items(item.fields, key = { it.id }) { f ->
            CredentialFieldRow(
                label = f.label,
                value = f.value,
                isSecret = f.isSecret,
                revealTimeoutSeconds = state.settings.revealTimeoutSeconds,
                requireAuth = secretGate,
                onCopy = { viewModel.copy(it, f.type in CategoryTemplates.primaryPasswordTypes || f.type == FieldType.PROFILE_PASSWORD || f.type == FieldType.TRANSACTION_PASSWORD) },
            )
        }

        if (!item.officialWebsite.isNullOrBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.detail_official_destination), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(item.institution ?: item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.officialDomain ?: item.officialWebsite, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(stringResource(R.string.action_open_website), onClick = viewModel::openWebsite, icon = Icons.Filled.OpenInBrowser)
                    }
                }
            }
        }

        if (CategoryTemplates.hasPrimaryPassword(item.category)) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow(stringResource(R.string.detail_last_changed), item.passwordLastChangedAt?.let { Dates.format(it) } ?: stringResource(R.string.detail_unknown_date))
                        val interval = item.reviewIntervalDays
                        InfoRow(stringResource(R.string.detail_review_policy), if (interval == null) stringResource(R.string.detail_review_none) else stringResource(R.string.detail_review_every, interval))
                        if (interval != null && item.passwordLastChangedAt != null) {
                            InfoRow(stringResource(R.string.detail_next_review), relativeDays(item.passwordLastChangedAt + interval * 86_400_000L, state.nowMillis))
                        }
                        InfoRow(
                            stringResource(R.string.detail_two_factor),
                            when (item.twoFactorEnabled) {
                                true -> stringResource(R.string.detail_two_factor_on)
                                false -> stringResource(R.string.detail_two_factor_off)
                                null -> stringResource(R.string.detail_two_factor_unknown)
                            },
                        )
                        if (!safeMode && item.deletedAt == null) {
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinkButton(stringResource(R.string.detail_mark_reviewed), onClick = viewModel::markReviewed)
                                LinkButton(stringResource(R.string.detail_change_password), onClick = onChangePassword)
                                LinkButton(stringResource(R.string.policy_title), onClick = onEditPolicy)
                            }
                        }
                    }
                }
            }
        }

        if (!item.notes.isNullOrBlank()) {
            item {
                SectionHeader(stringResource(R.string.detail_notes))
                Text(item.notes, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (state.history.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.detail_history)) }
            items(state.history.take(20), key = { it.id }) { ev ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(historyEventLabel(ev.type), style = MaterialTheme.typography.bodyMedium)
                    Text(Dates.format(ev.occurredAt) ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (!safeMode && item.deletedAt == null) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(daysAgo(item.updatedAt, state.nowMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.action_move_to_trash), onClick = viewModel::askTrash)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
