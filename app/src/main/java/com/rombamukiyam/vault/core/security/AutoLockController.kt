package com.rombamukiyam.vault.core.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.rombamukiyam.vault.core.common.SafeLog
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Locks the vault when:
 *  - the app has been in the background for the configured timeout (0 = immediately),
 *  - the device screen turns off (ACTION_SCREEN_OFF, runtime receiver — nothing exported in the manifest),
 *  - the user taps Lock.
 * Also exposes [inBackground] so screens can hide secrets the moment the app leaves the foreground.
 */
class AutoLockController(
    private val context: Context,
    private val session: VaultSession,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private var lockJob: Job? = null
    private val _inBackground = MutableStateFlow(false)
    val inBackground: StateFlow<Boolean> = _inBackground.asStateFlow()

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                SafeLog.d(TAG, "screen off -> lock")
                lockNow()
            }
        }
    }

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        context.registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStart(owner: LifecycleOwner) {
        _inBackground.value = false
        lockJob?.cancel()
        lockJob = null
    }

    override fun onStop(owner: LifecycleOwner) {
        _inBackground.value = true
        if (!session.isUnlocked) return
        lockJob?.cancel()
        lockJob = scope.launch {
            val seconds = settings.settings.first().autoLockSeconds
            if (seconds > 0) delay(seconds * 1000L)
            SafeLog.d(TAG, "background timeout -> lock")
            session.lock()
        }
    }

    fun lockNow() {
        lockJob?.cancel()
        lockJob = null
        session.lock()
    }

    companion object {
        private const val TAG = "AutoLock"
    }
}
