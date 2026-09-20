package com.rombamukiyam.vault.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.rombamukiyam.vault.R
import kotlinx.coroutines.delay

/**
 * Displays one credential field. Secret values are masked; Reveal is gated through [requireAuth] (a no-op
 * gate for ordinary items) and auto-hides after [revealTimeoutSeconds]. Visibility is plain `remember`
 * (never saveable) and is reset when the app goes to background. TalkBack reads the label and "hidden"
 * for masked secrets — never the value unless revealed.
 */
@Composable
fun CredentialFieldRow(
    label: String,
    value: String,
    isSecret: Boolean,
    revealTimeoutSeconds: Int,
    requireAuth: (onGranted: () -> Unit) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { revealed = false }
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(revealTimeoutSeconds * 1000L)
            revealed = false
        }
    }
    val hiddenDesc = stringResource(R.string.detail_hidden_until_reveal)
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                if (isSecret && !revealed) {
                    Text(
                        stringResource(R.string.detail_secret_masked),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.clearAndSetSemantics { contentDescription = "$label: $hiddenDesc" },
                    )
                } else {
                    Text(
                        value,
                        style = if (isSecret) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (isSecret) {
                IconButton(
                    onClick = { if (revealed) revealed = false else requireAuth { revealed = true } },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(if (revealed) R.string.action_hide else R.string.action_reveal),
                    )
                }
            }
            IconButton(
                onClick = { if (isSecret) requireAuth { onCopy(value) } else onCopy(value) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_copy))
            }
        }
    }
}
