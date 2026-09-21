package com.rombamukiyam.vault.feature.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rombamukiyam.vault.BuildConfig
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.backup.RmvaultFormat
import com.rombamukiyam.vault.core.common.BiometricUnavailableReason
import com.rombamukiyam.vault.core.database.RombaDatabase
import com.rombamukiyam.vault.core.security.KeySecurityLevel
import com.rombamukiyam.vault.domain.repository.AppSettings
import com.rombamukiyam.vault.ui.components.AttentionCard
import com.rombamukiyam.vault.ui.components.AttentionLevel
import com.rombamukiyam.vault.ui.components.RombaTopBar
import com.rombamukiyam.vault.ui.components.SecondaryButton
import com.rombamukiyam.vault.ui.components.SectionHeader
import com.rombamukiyam.vault.ui.components.message
import com.rombamukiyam.vault.ui.theme.ThemeMode

data class SettingsNav(
    val onOpenBackup: () -> Unit = {},
    val onOpenRecoveryKit: () -> Unit = {},
    val onChangePassphrase: () -> Unit = {},
    val onOpenHealth: () -> Unit = {},
    val onOpenVaultHealth: () -> Unit = {},
    val onOpenDiagnostics: () -> Unit = {},
    val onOpenFamily: () -> Unit = {},
    val onOpenLicenses: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    safeMode: Boolean,
    onLock: () -> Unit,
    nav: SettingsNav = SettingsNav(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    val activity = LocalActivity.current as? FragmentActivity
    val bioTitle = stringResource(R.string.bio_enroll_title)
    val bioSubtitle = stringResource(R.string.bio_enroll_subtitle)
    val cancel = stringResource(R.string.action_cancel)

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { RombaTopBar(stringResource(R.string.nav_settings)) }) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            if (safeMode) {
                item { AttentionCard(stringResource(R.string.safe_mode_banner), stringResource(R.string.safe_mode_settings_hint), AttentionLevel.ATTENTION) }
            }
            state.error?.let { err -> item { Text(err.message(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) } }

            // ---- Security
            item { SectionHeader(stringResource(R.string.settings_security)) }
            item { NavRow(stringResource(R.string.settings_change_passphrase), null, enabled = !safeMode, onClick = nav.onChangePassphrase) }
            item {
                val availability = state.biometricAvailability
                val subtitle = when {
                    state.biometricEnabled -> when (state.keySecurityLevel) {
                        KeySecurityLevel.STRONGBOX -> stringResource(R.string.settings_bio_level_strongbox)
                        KeySecurityLevel.TEE -> stringResource(R.string.settings_bio_level_tee)
                        KeySecurityLevel.SOFTWARE -> stringResource(R.string.settings_bio_level_software)
                        KeySecurityLevel.UNKNOWN -> stringResource(R.string.settings_bio_on)
                    }
                    availability == BiometricUnavailableReason.NONE_ENROLLED -> stringResource(R.string.err_bio_none_enrolled)
                    availability != null -> stringResource(R.string.err_bio_unavailable)
                    else -> stringResource(R.string.settings_bio_off)
                }
                SwitchRow(
                    stringResource(R.string.settings_biometric), subtitle, checked = state.biometricEnabled,
                    enabled = !safeMode && !state.busy && (state.biometricEnabled || availability == null),
                    onChange = { viewModel.toggleBiometric(activity, bioTitle, bioSubtitle, cancel) },
                )
            }
            item { SwitchRow(stringResource(R.string.settings_reauth_high), stringResource(R.string.settings_reauth_high_body), s.requireReauthForHighSecurity) { v -> viewModel.update { it.copy(requireReauthForHighSecurity = v) } } }
            item {
                ChoiceRow(stringResource(R.string.settings_auto_lock), AppSettings.AUTO_LOCK_OPTIONS, s.autoLockSeconds, { sec -> if (sec == 0) stringResource(R.string.settings_immediately) else secondsLabel(sec) }, viewModel::setAutoLock)
            }
            item { SwitchRow(stringResource(R.string.settings_screenshot), stringResource(R.string.settings_screenshot_body), s.screenshotProtection) { v -> viewModel.update { it.copy(screenshotProtection = v) } } }
            item { ChoiceRow(stringResource(R.string.settings_clipboard), AppSettings.CLIPBOARD_OPTIONS, s.clipboardClearSeconds, { secondsLabel(it) }, viewModel::setClipboard) }
            item { ChoiceRow(stringResource(R.string.settings_reveal), AppSettings.REVEAL_OPTIONS, s.revealTimeoutSeconds, { secondsLabel(it) }, viewModel::setReveal) }
            item { SwitchRow(stringResource(R.string.settings_max_privacy), stringResource(R.string.settings_max_privacy_body), s.maximumPrivacyWhenLocked) { v -> viewModel.update { it.copy(maximumPrivacyWhenLocked = v) } } }

            // ---- Notifications
            item { SectionHeader(stringResource(R.string.settings_notifications)) }
            item { SwitchRow(stringResource(R.string.settings_notif_enable), null, s.notificationsEnabled) { v -> viewModel.update { it.copy(notificationsEnabled = v) } } }
            item { SwitchRow(stringResource(R.string.settings_notif_review), null, s.passwordReviewAlerts, enabled = s.notificationsEnabled) { v -> viewModel.update { it.copy(passwordReviewAlerts = v) } } }
            item { SwitchRow(stringResource(R.string.settings_notif_doc), null, s.documentExpiryAlerts, enabled = s.notificationsEnabled) { v -> viewModel.update { it.copy(documentExpiryAlerts = v) } } }
            item { SwitchRow(stringResource(R.string.settings_notif_card), null, s.cardExpiryAlerts, enabled = s.notificationsEnabled) { v -> viewModel.update { it.copy(cardExpiryAlerts = v) } } }
            item { ChoiceRow(stringResource(R.string.settings_backup_reminder), AppSettings.BACKUP_REMINDER_OPTIONS, s.backupReminderDays, { stringResource(R.string.review_days, it) }, viewModel::setBackupReminder) }
            item { SwitchRow(stringResource(R.string.settings_notif_names), stringResource(R.string.settings_notif_names_body), s.notificationShowItemNames, enabled = s.notificationsEnabled) { v -> viewModel.update { it.copy(notificationShowItemNames = v) } } }

            // ---- Vault
            item { SectionHeader(stringResource(R.string.settings_vault)) }
            item { NavRow(stringResource(R.string.family_title), null, onClick = nav.onOpenFamily) }
            item {
                ChoiceRow(stringResource(R.string.settings_default_review), listOf(0, 30, 60, 90, 180, 365), s.defaultReviewDays ?: 0, { d -> if (d == 0) stringResource(R.string.review_none) else stringResource(R.string.review_days, d) }) { d -> viewModel.setDefaultReview(if (d == 0) null else d) }
            }
            item { ChoiceRow(stringResource(R.string.settings_trash_retention), AppSettings.TRASH_RETENTION_OPTIONS, s.trashRetentionDays, { stringResource(R.string.review_days, it) }, viewModel::setTrashRetention) }
            item { NavRow(stringResource(R.string.vault_health_title), null, onClick = nav.onOpenVaultHealth) }
            item { NavRow(stringResource(R.string.health_title), null, onClick = nav.onOpenHealth) }

            // ---- Backup & recovery
            item { SectionHeader(stringResource(R.string.backup_title)) }
            item { NavRow(stringResource(R.string.backup_title), stringResource(R.string.settings_backup_body), onClick = nav.onOpenBackup) }
            item { NavRow(stringResource(R.string.recovery_kit_title), null, onClick = nav.onOpenRecoveryKit) }
            item { InfoRow(stringResource(R.string.settings_recovery_info), stringResource(R.string.settings_recovery_info_body)) }

            // ---- Appearance
            item { SectionHeader(stringResource(R.string.settings_appearance)) }
            item {
                ChoiceRow(stringResource(R.string.settings_theme), ThemeMode.entries, s.themeMode, { m ->
                    when (m) {
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                    }
                }, viewModel::setTheme)
            }
            item { ChoiceRow(stringResource(R.string.settings_text_size), listOf(1.0f, 1.15f, 1.3f, 1.5f), s.textScale, { "${(it * 100).toInt()}%" }, viewModel::setTextScale) }

            // ---- Privacy
            item { SectionHeader(stringResource(R.string.settings_privacy)) }
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(R.string.privacy_internet, R.string.privacy_cloud, R.string.privacy_analytics, R.string.privacy_telemetry).forEach { res ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.privacy_disabled), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }

            // ---- About
            item { SectionHeader(stringResource(R.string.settings_about)) }
            item { InfoRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")") }
            item { InfoRow(stringResource(R.string.about_schema), RombaDatabase.SCHEMA_VERSION.toString()) }
            item { InfoRow(stringResource(R.string.about_backup_format), RmvaultFormat.FORMAT_VERSION.toString()) }
            item { InfoRow(stringResource(R.string.about_security_summary), stringResource(R.string.about_security_summary_body)) }
            item { NavRow(stringResource(R.string.about_licenses), null, onClick = nav.onOpenLicenses) }
            item { NavRow(stringResource(R.string.diagnostics_title), null, onClick = nav.onOpenDiagnostics) }

            item {
                Spacer(Modifier.height(24.dp))
                SecondaryButton(stringResource(R.string.action_lock), onClick = onLock)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun secondsLabel(sec: Int): String = if (sec >= 60) stringResource(R.string.minutes_n, sec / 60) else stringResource(R.string.seconds_n, sec)

@Composable
fun NavRow(title: String, subtitle: String?, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
        }
    }
}

@Composable
fun InfoRow(title: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
