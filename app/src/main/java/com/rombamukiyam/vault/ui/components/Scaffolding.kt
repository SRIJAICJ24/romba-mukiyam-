package com.rombamukiyam.vault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.ui.theme.RombaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RombaTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

enum class AttentionLevel { INFO, SAFE, ATTENTION, CRITICAL }

/** Calm status card. Colour follows the semantic level; red only for CRITICAL. */
@Composable
fun AttentionCard(
    title: String,
    body: String?,
    level: AttentionLevel,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val s = RombaColors.semantic
    val (container, content) = when (level) {
        AttentionLevel.INFO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        AttentionLevel.SAFE -> s.safeContainer to s.onSafeContainer
        AttentionLevel.ATTENTION -> s.attentionContainer to s.onAttentionContainer
        AttentionLevel.CRITICAL -> s.criticalContainer to s.onCriticalContainer
    }
    val cardModifier = modifier.fillMaxWidth()
    val content2: @Composable () -> Unit = {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = content)
                if (body != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = content)
                }
            }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = cardModifier, colors = CardDefaults.cardColors(containerColor = container), shape = MaterialTheme.shapes.large) { content2() }
    } else {
        Card(modifier = cardModifier, colors = CardDefaults.cardColors(containerColor = container), shape = MaterialTheme.shapes.large) { content2() }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = stringResource(R.string.action_cancel),
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (destructive) RombaColors.semantic.critical else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText, style = MaterialTheme.typography.labelLarge) } },
    )
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            PrimaryButton(text = actionText, onClick = onAction)
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/** Small coloured pill, e.g. "High security", "Review due". */
@Composable
fun SecurityBadge(text: String, level: AttentionLevel, modifier: Modifier = Modifier) {
    val s = RombaColors.semantic
    val (container, content) = when (level) {
        AttentionLevel.INFO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        AttentionLevel.SAFE -> s.safeContainer to s.onSafeContainer
        AttentionLevel.ATTENTION -> s.attentionContainer to s.onAttentionContainer
        AttentionLevel.CRITICAL -> s.criticalContainer to s.onCriticalContainer
    }
    androidx.compose.material3.Surface(color = container, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = content, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

