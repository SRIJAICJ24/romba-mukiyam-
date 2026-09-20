package com.rombamukiyam.vault.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rombamukiyam.vault.core.security.SecureClipboard
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Fallback clipboard clearing if the process died before the in-memory timer fired. */
@HiltWorker
class ClipboardClearWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val clipboard: SecureClipboard,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        clipboard.clearNow()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "clipboard-clear"
    }
}
