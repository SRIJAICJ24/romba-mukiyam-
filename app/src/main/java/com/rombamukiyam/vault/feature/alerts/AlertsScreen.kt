package com.rombamukiyam.vault.feature.alerts

import android.Manifest
import android.os.Build
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
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.domain.usecase.AlertBucket
import com.rombamukiyam.vault.domain.usecase.AlertItem
import com.rombamukiyam.vault.domain.usecase.AlertType
import com.rombamukiyam.vault.domain.usecase.GetUpcomingAlertsUseCase
import com.rombamukiyam.vault.domain.usecase.MarkReminderDoneUseCase
import com.rombamukiyam.vault.ui.components.AttentionCard
import com.rombamukiyam.vault.ui.components.AttentionLevel
import com.rombamukiyam.vault.ui.components.EmptyState
import com.rombamukiyam.vault.ui.components.LinkButton
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.SectionHeader
import com.rombamukiyam.vault.ui.components.relativeDays
import com.rombamukiyam.vault.worker.Notifications
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AlertsUiState(val buckets: Map<AlertBucket, List<AlertItem>> = emptyMap(), val nowMillis: Long = 0L, val loaded: Boolean = false)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    alerts: GetUpcomingAlertsUseCase,
    private val markDone: MarkReminderDoneUseCase,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<AlertsUiState> = alerts.observeAll().map { list ->
        val now = clock.nowMillis()
        AlertsUiState(list.groupBy { it.bucket(now) }, now, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState())

    fun done(alert: AlertItem) = viewModelScope.launch { markDone(alert) }
}

@Composable
fun AlertsScreen(onOpenItem: (String) -> Unit, onOpenBackup: () -> Unit = {}, viewModel: AlertsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(Notifications.canPost(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canPost = it }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RombaTopBar(stringResource(R.string.nav_alerts)) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            if (!canPost) {
                item {
                    AttentionCard(
                        stringResource(R.string.alerts_permission_title),
                        stringResource(R.string.alerts_permission_body),
                        AttentionLevel.ATTENTION,
                        onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (state.loaded && state.buckets.isEmpty()) {
                item { EmptyState(stringResource(R.string.alerts_empty_title), stringResource(R.string.alerts_empty_body), icon = Icons.Filled.NotificationsNone) }
            }
            AlertBucket.entries.forEach { bucket ->
                val items = state.buckets[bucket] ?: return@forEach
                item { SectionHeader(bucketLabel(bucket)) }
                items(items, key = { "${it.type}-${it.itemId}-${it.reminderId}" }) { a ->
                    AlertRow(a, state.nowMillis, onOpen = { if (a.type == AlertType.BACKUP) onOpenBackup() else a.itemId?.let(onOpenItem) }, onDone = if (a.canMarkDone) ({ viewModel.done(a) }) else null)
                }
            }
        }
    }
}

@Composable
private fun bucketLabel(b: AlertBucket): String = stringResource(
    when (b) {
        AlertBucket.OVERDUE -> R.string.alerts_overdue
        AlertBucket.NEXT_7_DAYS -> R.string.alerts_next_7
        AlertBucket.NEXT_30_DAYS -> R.string.alerts_next_30
        AlertBucket.LATER -> R.string.alerts_later
    },
)

@Composable
fun alertTypeLabel(t: AlertType): String = stringResource(
    when (t) {
        AlertType.PASSWORD_REVIEW -> R.string.alert_type_review
        AlertType.CARD_EXPIRY -> R.string.alert_type_card
        AlertType.DOCUMENT_EXPIRY -> R.string.alert_type_document
        AlertType.BACKUP -> R.string.alert_type_backup
        AlertType.CUSTOM -> R.string.ft_custom
    },
)

@Composable
private fun AlertRow(a: AlertItem, now: Long, onOpen: () -> Unit, onDone: (() -> Unit)?) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(if (a.type == AlertType.BACKUP) stringResource(R.string.alert_type_backup) else a.title, style = MaterialTheme.typography.titleMedium)
            val owner = a.ownerName?.let { "$it · " } ?: ""
            Text(owner + alertTypeLabel(a.type) + " · " + relativeDays(a.dueAt, now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onDone != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    LinkButton(stringResource(R.string.detail_mark_reviewed), onClick = onDone)
                }
            }
        }
    }
}
