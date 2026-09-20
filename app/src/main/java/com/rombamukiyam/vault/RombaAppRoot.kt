package com.rombamukiyam.vault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rombamukiyam.vault.feature.auth.LockScreen
import com.rombamukiyam.vault.feature.main.MainShell
import com.rombamukiyam.vault.feature.onboarding.OnboardingScreen
import com.rombamukiyam.vault.ui.components.ConfirmationDialog
import com.rombamukiyam.vault.ui.theme.RombaTheme

/**
 * Root of the Compose hierarchy. Routing is derived purely from session + settings state, so process death
 * (which drops the in-memory Vault Key) always lands on the lock screen.
 */
@Composable
fun RombaAppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    RombaTheme(themeMode = state.settings.themeMode, textScale = state.settings.textScale) {
        when (state.route) {
            AppRoute.LOADING -> Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            AppRoute.ONBOARDING -> OnboardingScreen(onFinished = { /* route flips via settings flow */ })
            AppRoute.LOCKED -> LockScreen()
            AppRoute.MAIN -> MainShell(safeMode = state.safeMode, onLock = viewModel::lockNow)
        }
        if (state.showRootWarning && state.route == AppRoute.MAIN) {
            ConfirmationDialog(
                title = stringResource(R.string.root_warning_title),
                text = stringResource(R.string.root_warning_body),
                confirmText = stringResource(R.string.action_understood),
                onConfirm = viewModel::dismissRootWarning,
                onDismiss = viewModel::dismissRootWarning,
                dismissText = stringResource(R.string.action_close),
            )
        }
    }
}
