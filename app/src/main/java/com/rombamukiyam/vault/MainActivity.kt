package com.rombamukiyam.vault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** FragmentActivity subclass is required by AndroidX BiometricPrompt. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Screenshot / screen-recording / Recent-Apps preview protection for the whole app. The flag must be
        // set before the window surface exists; toggling later needs a recreate, so the stored preference is
        // read synchronously here (tiny DataStore read) and changes trigger recreate().
        val protect = runCatching { runBlocking { settings.settings.first().screenshotProtection } }.getOrDefault(true)
        if (protect) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settings.settings.map { it.screenshotProtection }.distinctUntilChanged().collect { now ->
                    if (now != protect) recreate()
                }
            }
        }
        enableEdgeToEdge()
        setContent { RombaAppRoot() }
    }
}
