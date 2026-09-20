package com.rombamukiyam.vault.feature.alerts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.domain.usecase.AlertBucket
import com.rombamukiyam.vault.domain.usecase.AlertItem
import com.rombamukiyam.vault.domain.usecase.AlertType
import com.rombamukiyam.vault.domain.usecase.GetUpcomingAlertsUseCase
import com.rombamukiyam.vault.ui.components.EmptyState
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.SectionHeader
import com.rombamukiyam.vault.ui.components.relativeDays
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AlertsUiState(val buckets: Map<AlertBucket, List<AlertItem>> = emptyMap(), val nowMillis: Long = 0L, val loaded: Boolean = false)

@HiltViewModel
class AlertsViewModel @Inject constructor(alerts: GetUpcomingAlertsUseCase, private val clock: Clock) : ViewModel() {
    val state: StateFlow<AlertsUiState> = alerts.observeAll().map { list ->
        val now = clock.nowMillis()
        AlertsUiState(list.groupBy { it.bucket(now) }, now, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState())
}

@Composable
fun AlertsScreen(onOpenItem: (String) -> Unit, viewModel: AlertsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RombaTopBar(stringResource(R.string.nav_alerts)) }) { padding ->
        if (state.loaded && state.buckets.isEmpty()) {
            EmptyState(stringResource(R.string.alerts_empty_title), stringResource(R.string.alerts_empty_body), icon = Icons.Filled.NotificationsNone, modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            AlertBucket.entries.forEach { bucket ->
                val items = state.buckets[bucket] ?: return@forEach
                item { SectionHeader(bucketLabel(bucket)) }
                items(items, key = { "${it.type}-${it.itemId}-${it.dueAt}" }) { a -> AlertRow(a, state.nowMillis) { a.itemId?.let(onOpenItem) } }
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
    },
)

@Composable
private fun AlertRow(a: AlertItem, now: Long, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(if (a.type == AlertType.BACKUP) stringResource(R.string.alert_type_backup) else a.title, style = MaterialTheme.typography.titleMedium)
            Row {
                val owner = a.ownerName?.let { "$it · " } ?: ""
                Text(owner + alertTypeLabel(a.type) + " · " + relativeDays(a.dueAt, now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
