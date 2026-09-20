package com.rombamukiyam.vault.feature.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.ui.components.SecondaryButton

/** Phase 1 placeholder; replaced by the bottom-navigation shell in Phase 2. */
@Composable
fun MainShell(safeMode: Boolean, onLock: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (safeMode) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.safe_mode_banner), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(32.dp))
            SecondaryButton(text = stringResource(R.string.action_lock), onClick = onLock)
        }
    }
}
