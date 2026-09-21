package com.rombamukiyam.vault.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.domain.model.VaultCategory
import com.rombamukiyam.vault.ui.components.AttentionCard
import com.rombamukiyam.vault.ui.components.AttentionLevel
import com.rombamukiyam.vault.ui.components.FamilyChip
import com.rombamukiyam.vault.ui.components.SectionHeader
import com.rombamukiyam.vault.ui.components.VaultCard
import com.rombamukiyam.vault.ui.components.categoryIcon
import com.rombamukiyam.vault.ui.components.categoryLabel

private val homeCategories = listOf(
    VaultCategory.BANK, VaultCategory.WEBSITE, VaultCategory.EMAIL, VaultCategory.CARD,
    VaultCategory.DOCUMENT, VaultCategory.SECURE_NOTE,
)

@Composable
fun HomeScreen(
    safeMode: Boolean,
    onSearch: () -> Unit,
    onOpenCategory: (VaultCategory) -> Unit,
    onOpenOwner: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onAdd: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenHealth: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!safeMode) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_add_credential)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
        ) {
            item {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                SearchBarButton(onClick = onSearch)
                if (safeMode) {
                    Spacer(Modifier.height(12.dp))
                    AttentionCard(stringResource(R.string.safe_mode_banner), null, AttentionLevel.ATTENTION, icon = Icons.Filled.Warning)
                }
            }

            if (state.profiles.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.home_section_family)) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.profiles, key = { it.id }) { p -> FamilyChip(p, selected = false, onClick = { onOpenOwner(p.id) }) }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.home_section_attention)) }
            item { AttentionSection(state, onOpenAlerts, onOpenBackup) }
            item {
                Spacer(Modifier.height(8.dp))
                AttentionCard(stringResource(R.string.health_title), stringResource(R.string.health_home_hint), AttentionLevel.INFO, icon = Icons.Filled.HealthAndSafety, onClick = onOpenHealth)
            }

            item { SectionHeader(stringResource(R.string.home_section_vault)) }
            item { CategoryGrid(state, onOpenCategory) }

            item { SectionHeader(stringResource(R.string.home_section_favourites)) }
            if (state.favorites.isEmpty()) {
                item {
                    Text(stringResource(R.string.home_no_favourites), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(state.favorites, key = { it.id }) { fav ->
                    VaultCard(fav, state.nowMillis, onClick = { onOpenItem(fav.id) }, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchBarButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(modifier = Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.home_search_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AttentionSection(state: HomeUiState, onOpenAlerts: () -> Unit, onOpenBackup: () -> Unit) {
    val cards = mutableListOf<@Composable () -> Unit>()
    if (state.reviewsDue > 0) {
        cards += { AttentionCard(stringResource(R.string.home_reviews_due, state.reviewsDue), null, AttentionLevel.ATTENTION, onClick = onOpenAlerts) }
    }
    if (state.expiringSoon > 0) {
        cards += { AttentionCard(stringResource(R.string.home_expiring, state.expiringSoon), null, AttentionLevel.ATTENTION, onClick = onOpenAlerts) }
    }
    val last = state.lastBackupAt
    if (state.loaded && last == null) {
        cards += { AttentionCard(stringResource(R.string.home_backup_never), stringResource(R.string.home_backup_never_body), AttentionLevel.ATTENTION, icon = Icons.Filled.Backup, onClick = onOpenBackup) }
    } else if (last != null) {
        val days = ((state.nowMillis - last) / 86_400_000L).toInt()
        if (days >= state.backupReminderDays) {
            cards += { AttentionCard(stringResource(R.string.home_backup_old, days), null, AttentionLevel.ATTENTION, icon = Icons.Filled.Backup, onClick = onOpenBackup) }
        }
    }
    if (cards.isEmpty()) {
        AttentionCard(stringResource(R.string.home_all_good), stringResource(R.string.home_all_good_body), AttentionLevel.SAFE, icon = Icons.Filled.CheckCircle)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { cards.forEach { it() } }
    }
}

@Composable
private fun CategoryGrid(state: HomeUiState, onOpenCategory: (VaultCategory) -> Unit) {
    val counts = state.categoryCounts.associate { it.category to it.count }
    val rows = homeCategories.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { c ->
                    CategoryTile(c, counts[c] ?: 0, onClick = { onOpenCategory(c) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(category: VaultCategory, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(categoryIcon(category), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(categoryLabel(category), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.home_items_count, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
