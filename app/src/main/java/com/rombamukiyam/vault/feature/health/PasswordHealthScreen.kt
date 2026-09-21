package com.rombamukiyam.vault.feature.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.common.VaultError
import com.rombamukiyam.vault.domain.repository.VaultRepository
import com.rombamukiyam.vault.domain.usecase.CheckPasswordHealthUseCase
import com.rombamukiyam.vault.domain.usecase.HealthIssue
import com.rombamukiyam.vault.domain.usecase.HealthReport
import com.rombamukiyam.vault.ui.components.AttentionLevel
import com.rombamukiyam.vault.ui.components.EmptyState
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.SectionHeader
import com.rombamukiyam.vault.ui.components.SecurityBadge
import com.rombamukiyam.vault.ui.components.VaultCard
import com.rombamukiyam.vault.ui.components.message
import com.rombamukiyam.vault.ui.theme.RombaColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HealthUiState(val report: HealthReport? = null, val error: VaultError? = null, val loading: Boolean = true, val nowMillis: Long = 0L)

@HiltViewModel
class PasswordHealthViewModel @Inject constructor(
    private val vault: VaultRepository,
    private val check: CheckPasswordHealthUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(HealthUiState())
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val items = vault.observeActive().first()
            check(items)
                .onSuccess { _state.value = HealthUiState(report = it, loading = false, nowMillis = clock.nowMillis()) }
                .onFailure { _state.value = HealthUiState(error = it, loading = false) }
        }
    }
}

@Composable
fun PasswordHealthScreen(onBack: () -> Unit, onOpenItem: (String) -> Unit, viewModel: PasswordHealthViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RombaTopBar(stringResource(R.string.health_title), onBack = onBack) }) { padding ->
        val report = state.report
        when {
            state.loading -> Column(modifier = Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            state.error != null -> EmptyState(stringResource(R.string.err_unknown), state.error!!.message(), modifier = Modifier.padding(padding))
            report == null -> Unit
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                item { ScoreCard(report) }
                item { SectionHeader(stringResource(R.string.health_summary)) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat(stringResource(R.string.health_weak, report.weak), report.weak)
                        Stat(stringResource(R.string.health_reused, report.reused), report.reused)
                        Stat(stringResource(R.string.health_old, report.old), report.old)
                        Stat(stringResource(R.string.health_overdue, report.overdue), report.overdue)
                        Stat(stringResource(R.string.health_missing_2fa, report.missing2fa), report.missing2fa)
                        Stat(stringResource(R.string.health_missing_recovery, report.missingRecovery), report.missingRecovery)
                        Stat(stringResource(if (report.backupOverdue) R.string.health_backup_overdue else R.string.health_backup_ok), if (report.backupOverdue) 1 else 0)
                    }
                }
                if (report.findings.isEmpty()) {
                    item { EmptyState(stringResource(R.string.health_all_good_title), stringResource(R.string.health_all_good_body), icon = Icons.Filled.HealthAndSafety) }
                } else {
                    item { SectionHeader(stringResource(R.string.health_findings)) }
                    items(report.findings, key = { it.item.id }) { f ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            VaultCard(f.item, state.nowMillis, onClick = { onOpenItem(f.item.id) })
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 8.dp, top = 6.dp)) {
                                f.issues.forEach { SecurityBadge(issueLabel(it), if (it == HealthIssue.WEAK_PASSWORD || it == HealthIssue.REUSED_PASSWORD) AttentionLevel.CRITICAL else AttentionLevel.ATTENTION) }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ScoreCard(report: HealthReport) {
    val s = RombaColors.semantic
    val color = when {
        report.score >= 80 -> s.safe
        report.score >= 50 -> s.attention
        else -> s.critical
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${report.score}", style = MaterialTheme.typography.headlineLarge, color = color)
            Spacer(Modifier.padding(horizontal = 12.dp))
            Column {
                Text(stringResource(R.string.health_score), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.health_items_checked, report.totalItems), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Stat(text: String, count: Int) {
    SecurityBadge(text, if (count == 0) AttentionLevel.SAFE else AttentionLevel.ATTENTION)
}

@Composable
fun issueLabel(i: HealthIssue): String = stringResource(
    when (i) {
        HealthIssue.WEAK_PASSWORD -> R.string.issue_weak
        HealthIssue.REUSED_PASSWORD -> R.string.issue_reused
        HealthIssue.OLD_PASSWORD -> R.string.issue_old
        HealthIssue.REVIEW_OVERDUE -> R.string.issue_overdue
        HealthIssue.MISSING_2FA -> R.string.issue_missing_2fa
        HealthIssue.MISSING_RECOVERY -> R.string.issue_missing_recovery
        HealthIssue.NO_PASSWORD_DATE -> R.string.issue_no_date
    },
)
