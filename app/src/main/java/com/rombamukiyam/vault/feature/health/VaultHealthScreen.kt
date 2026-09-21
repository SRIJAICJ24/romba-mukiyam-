package com.rombamukiyam.vault.feature.health

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.backup.BackupManager
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.common.DispatcherProvider
import com.rombamukiyam.vault.core.database.DatabaseFactory
import com.rombamukiyam.vault.core.database.IntegrityChecker
import com.rombamukiyam.vault.core.database.IntegrityLevel
import com.rombamukiyam.vault.core.database.IntegrityResult
import com.rombamukiyam.vault.core.database.RombaDatabase
import com.rombamukiyam.vault.core.security.SessionState
import com.rombamukiyam.vault.core.security.VaultSession
import com.rombamukiyam.vault.domain.model.HistoryEventType
import com.rombamukiyam.vault.domain.repository.DiagnosticsRepository
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import com.rombamukiyam.vault.domain.repository.VaultEventLogger
import com.rombamukiyam.vault.ui.components.AttentionCard
import com.rombamukiyam.vault.ui.components.AttentionLevel
import com.rombamukiyam.vault.ui.components.Dates
import com.rombamukiyam.vault.ui.components.LinkButton
import com.rombamukiyam.vault.ui.components.PrimaryButton
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.SectionHeader
import com.rombamukiyam.vault.ui.components.SecurityBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VaultHealthUiState(
    val databaseAccessible: Boolean = false,
    val encryptionActive: Boolean = false,
    val schemaVersion: Int = RombaDatabase.SCHEMA_VERSION,
    val safeMode: Boolean = false,
    val lastCheck: IntegrityResult? = null,
    val lastCheckAt: Long? = null,
    val lastBackupAt: Long? = null,
    val lastBackupVerified: Boolean = false,
    val snapshots: List<File> = emptyList(),
    val databaseSizeBytes: Long = 0,
    val checking: Boolean = false,
)

@HiltViewModel
class VaultHealthViewModel @Inject constructor(
    private val session: VaultSession,
    private val integrity: IntegrityChecker,
    private val databaseFactory: DatabaseFactory,
    private val backupManager: BackupManager,
    private val settings: SettingsRepository,
    private val events: VaultEventLogger,
    private val diagnostics: DiagnosticsRepository,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(VaultHealthUiState())
    val state: StateFlow<VaultHealthUiState> = _state.asStateFlow()

    fun refresh() = viewModelScope.launch {
        val s = settings.settings.first()
        val file = databaseFactory.databaseFile()
        _state.update {
            it.copy(
                databaseAccessible = session.isUnlocked,
                encryptionActive = session.isUnlocked && isEncryptedOnDisk(file),
                safeMode = (session.state.value as? SessionState.Unlocked)?.safeMode == true,
                lastCheckAt = s.lastIntegrityCheckAt,
                lastBackupAt = s.lastBackupAt,
                lastBackupVerified = s.lastBackupVerified,
                snapshots = backupManager.listSnapshots(),
                databaseSizeBytes = listOf(file, File(file.path + "-wal")).filter { it.exists() }.sumOf { it.length() },
            )
        }
    }

    /** A SQLCipher file must not start with the plaintext SQLite header. */
    private suspend fun isEncryptedOnDisk(file: File): Boolean = withContext(dispatchers.io) {
        runCatching {
            if (!file.exists()) return@runCatching false
            val head = ByteArray(16)
            file.inputStream().use { it.read(head) }
            !String(head, Charsets.US_ASCII).startsWith("SQLite format 3")
        }.getOrDefault(false)
    }

    fun runFullCheck() = viewModelScope.launch {
        if (!session.isUnlocked) return@launch
        _state.update { it.copy(checking = true) }
        val result = withContext(dispatchers.io) { integrity.check(session.database().openHelper.writableDatabase, IntegrityLevel.FULL, clock.nowMillis()) }
        settings.update { it.copy(lastIntegrityCheckAt = result.checkedAt) }
        events.log(HistoryEventType.INTEGRITY_CHECK, if (result.ok) "ok" else "problems=${result.problemCount}")
        if (!result.ok) diagnostics.record("integrity", "E_DB_INTEGRITY", null)
        _state.update { it.copy(checking = false, lastCheck = result, lastCheckAt = result.checkedAt) }
    }

    fun deleteSnapshot(dir: File) = viewModelScope.launch {
        withContext(dispatchers.io) { backupManager.deleteSnapshot(dir) }
        refresh()
    }
}

@Composable
fun VaultHealthScreen(onBack: () -> Unit, viewModel: VaultHealthViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val s by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RombaTopBar(stringResource(R.string.vault_health_title), onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { StatusRow(stringResource(R.string.vh_db_accessible), s.databaseAccessible) }
            item { StatusRow(stringResource(R.string.vh_encryption), s.encryptionActive) }
            item { StatusRow(stringResource(R.string.vh_schema, s.schemaVersion), true) }
            item { StatusRow(stringResource(R.string.vh_items_readable), s.lastCheck?.ok ?: s.databaseAccessible) }
            item { StatusRow(stringResource(R.string.vh_last_check, Dates.format(s.lastCheckAt) ?: stringResource(R.string.detail_never)), s.lastCheckAt != null) }
            item { StatusRow(stringResource(R.string.vh_last_backup, Dates.format(s.lastBackupAt) ?: stringResource(R.string.detail_never)), s.lastBackupAt != null) }
            item { StatusRow(stringResource(R.string.vh_backup_verified), s.lastBackupVerified) }
            item { Text(stringResource(R.string.vh_db_size, s.databaseSizeBytes / 1024), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (s.safeMode) item { AttentionCard(stringResource(R.string.safe_mode_banner), stringResource(R.string.safe_mode_health_hint), AttentionLevel.ATTENTION) }
            item { PrimaryButton(stringResource(R.string.vh_run_check), onClick = viewModel::runFullCheck, loading = s.checking, enabled = s.databaseAccessible) }
            s.lastCheck?.let { r ->
                item {
                    if (r.ok) {
                        AttentionCard(stringResource(R.string.vh_check_ok_title), r.tableCounts.entries.joinToString("\n") { "${it.key}: ${it.value}" }, AttentionLevel.SAFE)
                    } else {
                        AttentionCard(stringResource(R.string.vh_check_fail_title), stringResource(R.string.vh_check_fail_body), AttentionLevel.CRITICAL)
                    }
                }
            }
            if (s.snapshots.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.vh_snapshots)) }
                item { Text(stringResource(R.string.vh_snapshots_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(s.snapshots, key = { it.name }) { dir ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(Dates.format(dir.name.toLongOrNull()) ?: dir.name, style = MaterialTheme.typography.bodyLarge)
                        LinkButton(stringResource(R.string.action_delete), onClick = { viewModel.deleteSnapshot(dir) })
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        SecurityBadge(stringResource(if (ok) R.string.vh_ok else R.string.vh_attention), if (ok) AttentionLevel.SAFE else AttentionLevel.ATTENTION)
    }
}
