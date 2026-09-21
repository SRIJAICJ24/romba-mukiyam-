package com.rombamukiyam.vault.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rombamukiyam.vault.MainActivity
import com.rombamukiyam.vault.R
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.common.SafeLog
import com.rombamukiyam.vault.core.security.ReminderSchedulePublisher
import com.rombamukiyam.vault.domain.model.ReminderType
import com.rombamukiyam.vault.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Periodic check (every 12 h, survives reboot via WorkManager) that reads ONLY the non-secret schedule file
 * and posts a privacy-preserving notification. It never touches the encrypted database or any key.
 */
@HiltWorker
class ReminderCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val publisher: ReminderSchedulePublisher,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val s = settings.settings.first()
        if (!s.notificationsEnabled) return Result.success()
        val now = clock.nowMillis()
        val due = publisher.read().filter { it.dueAt <= now }
        if (due.isEmpty()) return Result.success()
        Notifications.postAttention(applicationContext, due.size, if (s.notificationShowItemNames) due.mapNotNull { it.label }.distinct() else emptyList(), due.map { it.type })
        SafeLog.d(TAG, "posted attention notification for ${due.size} item(s)")
        return Result.success()
    }

    companion object {
        private const val TAG = "ReminderWorker"
        const val UNIQUE_NAME = "reminder-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

object Notifications {
    const val CHANNEL_ID = "attention"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notif_channel_desc)
                // Never show content on the lock screen beyond the app name.
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Generic by default: "N items need attention". With opt-in, item names (never usernames/ids/secrets).
     * The public (lock-screen) version is always generic.
     */
    fun postAttention(context: Context, count: Int, names: List<String>, types: List<ReminderType>) {
        if (!canPost(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val generic = context.resources.getQuantityString(R.plurals.notif_generic, count, count)
        val text = when {
            names.isNotEmpty() -> names.take(3).joinToString(", ") + if (names.size > 3) " …" else ""
            types.all { it == ReminderType.BACKUP_REMINDER } -> context.getString(R.string.notif_backup_due)
            else -> generic
        }
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(generic)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
    }
}
