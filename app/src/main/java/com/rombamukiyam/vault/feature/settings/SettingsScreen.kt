package com.rombamukiyam.vault.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.SecondaryButton

/** Phase 2 placeholder; the full settings tree arrives in Phase 6. */
@Composable
fun SettingsScreen(safeMode: Boolean, onLock: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RombaTopBar(stringResource(R.string.nav_settings)) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            if (safeMode) Text(stringResource(R.string.safe_mode_banner), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            SecondaryButton(stringResource(R.string.action_lock), onClick = onLock)
        }
    }
}
