package com.rombamukiyam.vault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.domain.model.FamilyProfile
import com.rombamukiyam.vault.domain.model.VaultItemSummary

@Composable
fun ProfileAvatar(profile: FamilyProfile, size: Int = 40) {
    Box(
        modifier = Modifier.size(size.dp).background(Color(profile.avatarColor), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            profile.initials.ifBlank { "?" },
            color = Color.White,
            style = if (size >= 48) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyChip(profile: FamilyProfile, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(profile.displayName, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = { ProfileAvatar(profile, size = 24) },
        modifier = Modifier.height(44.dp),
    )
}

/** Non-secret list row for a vault item. Shows owner, category icon, badges; never a field value. */
@Composable
fun VaultCard(item: VaultItemSummary, nowMillis: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reviewDue = item.reviewIntervalDays != null && item.passwordLastChangedAt != null &&
        item.passwordLastChangedAt + item.reviewIntervalDays * 86_400_000L <= nowMillis
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(categoryIcon(item.category), contentDescription = categoryLabel(item.category), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(item.ownerName, item.institution ?: item.subtitle).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.highSecurity || reviewDue || item.deletedAt != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (item.deletedAt != null) SecurityBadge(stringResource(R.string.badge_in_trash), AttentionLevel.ATTENTION)
                        if (item.highSecurity) SecurityBadge(stringResource(R.string.badge_high_security), AttentionLevel.INFO)
                        if (reviewDue) SecurityBadge(stringResource(R.string.badge_review_due), AttentionLevel.ATTENTION)
                    }
                }
            }
            if (item.favorite) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.action_favourite), tint = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
