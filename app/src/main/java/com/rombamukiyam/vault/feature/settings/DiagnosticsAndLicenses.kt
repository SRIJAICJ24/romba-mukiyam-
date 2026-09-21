package com.rombamukiyam.vault.feature.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.domain.repository.DiagnosticEvent
import com.rombamukiyam.vault.domain.repository.DiagnosticsRepository
import com.rombamukiyam.vault.ui.components.Dates
import com.rombamukiyam.vault.ui.components.EmptyState
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.SecondaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(private val repo: DiagnosticsRepository) : ViewModel() {
    val events: StateFlow<List<DiagnosticEvent>> = repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun clear() = viewModelScope.launch { repo.clear() }
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RombaTopBar(stringResource(R.string.diagnostics_title), onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item { Text(stringResource(R.string.diagnostics_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Spacer(Modifier.height(12.dp)) }
            if (events.isEmpty()) {
                item { EmptyState(stringResource(R.string.diagnostics_empty), "") }
            } else {
                item { SecondaryButton(stringResource(R.string.diagnostics_clear), onClick = viewModel::clear) }
                items(events, key = { it.id }) { e ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("${e.module} · ${e.code}", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace))
                            Text(Dates.format(e.occurredAt) ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("v${e.appVersion} · schema ${e.schemaVersion}" + (e.exceptionClass?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private data class LicenseEntry(val name: String, val license: String)

private val licenses = listOf(
    LicenseEntry("AndroidX (Compose, Room, Biometric, WorkManager, DataStore, Navigation, Lifecycle, Hilt integrations)", "Apache License 2.0"),
    LicenseEntry("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0"),
    LicenseEntry("Dagger / Hilt", "Apache License 2.0"),
    LicenseEntry("SQLCipher for Android (net.zetetic:sqlcipher-android)", "BSD-style license (Zetetic LLC)"),
    LicenseEntry("Argon2Kt (com.lambdapioneer.argon2kt) and the Argon2 reference implementation", "MIT License / CC0 & Apache 2.0 (reference code)"),
    LicenseEntry("EFF Short Wordlist", "CC BY 3.0 (Electronic Frontier Foundation)"),
    LicenseEntry("Material Icons", "Apache License 2.0"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RombaTopBar(stringResource(R.string.about_licenses), onBack = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            items(licenses) { l ->
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    Text(l.name, style = MaterialTheme.typography.bodyLarge)
                    Text(l.license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
