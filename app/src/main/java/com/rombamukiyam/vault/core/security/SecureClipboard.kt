package com.rombamukiyam.vault.core.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.rombamukiyam.vault.core.common.SafeLog
import com.rombamukiyam.vault.worker.ClipboardClearWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies secrets to the clipboard with the "sensitive" flag (hides the value from the Android 13+ clipboard
 * preview) and clears it after the configured delay. Two clearing mechanisms run:
 *  - an in-process delayed job (works while the app is alive),
 *  - a WorkManager one-shot as a fallback if the process is killed first.
 * Limitation: Android may ignore clearing from a background app; Android 13+ also auto-clears after ~1 h.
 */
class SecureClipboard(private val context: Context, private val scope: CoroutineScope) {

    private var clearJob: Job? = null
    /** SHA-256 of the last value we copied, so we can recognise our clip even if the label was rewritten. */
    @Volatile private var lastDigest: ByteArray? = null

    fun copySecret(value: String, clearAfterSeconds: Int) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(LABEL, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        lastDigest = digest(value)
        cm.setPrimaryClip(clip)
        scheduleClear(clearAfterSeconds)
    }

    private fun scheduleClear(seconds: Int) {
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(seconds * 1000L)
            clearNow()
        }
        val request = OneTimeWorkRequestBuilder<ClipboardClearWorker>()
            .setInitialDelay(seconds.toLong() + 2, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ClipboardClearWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun clearNow() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            // Only clear if the clip is ours so we never wipe something the user copied elsewhere.
            val description = cm.primaryClipDescription
            // Ownership: our label, OR same content as what we copied (labels can be rewritten, e.g. by the
            // emulator's host-clipboard sync), OR the description is hidden from us (unfocused) in which case we
            // err on the side of clearing because a secret was copied within the window.
            val sameLabel = description?.label?.toString() == LABEL
            val sameContent = lastDigest?.let { d ->
                val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
                text != null && digest(text).contentEquals(d)
            } ?: false
            val ours = description == null || sameLabel || sameContent
            if (!ours) {
                SafeLog.d(TAG, "clipboard holds foreign content; not clearing")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
            lastDigest = null
            SafeLog.d(TAG, "clipboard cleared")
        } catch (e: Exception) {
            SafeLog.w(TAG, "clipboard clear failed", e)
        }
    }

    private fun digest(value: String): ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

    companion object {
        private const val TAG = "Clipboard"
        const val LABEL = "Romba Mukiyam"
    }
}
