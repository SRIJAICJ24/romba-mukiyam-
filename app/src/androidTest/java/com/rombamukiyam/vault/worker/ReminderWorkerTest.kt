package com.rombamukiyam.vault.worker

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.rombamukiyam.vault.core.common.Clock
import com.rombamukiyam.vault.core.security.ReminderSchedulePublisher
import com.rombamukiyam.vault.data.repository.DataStoreSettingsRepository
import com.rombamukiyam.vault.domain.model.ReminderType
import com.rombamukiyam.vault.domain.model.ScheduledReminder
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import androidx.test.rule.GrantPermissionRule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderWorkerTest {

    @get:Rule
    val grant: GrantPermissionRule = if (android.os.Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dir = File(context.cacheDir, "sched-${System.nanoTime()}")
    private val publisher = ReminderSchedulePublisher(dir)
    private val now = 1_800_000_000_000L
    private val clock = object : Clock { override fun nowMillis() = now }
    private val settings = DataStoreSettingsRepository(PreferenceDataStoreFactory.create { File(context.cacheDir, "settings-${System.nanoTime()}.preferences_pb") })

    private fun worker(): ReminderCheckWorker = TestListenableWorkerBuilder<ReminderCheckWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                ReminderCheckWorker(appContext, workerParameters, publisher, settings, clock)
        })
        .build() as ReminderCheckWorker

    @Before
    fun setUp() {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
    }

    @Test
    fun scheduleFileHoldsNoLabelsUnlessOptedIn() {
        publisher.publish(listOf(ScheduledReminder(now, ReminderType.PASSWORD_REVIEW, "SBI - Dad")), includeLabels = false, now = now)
        val text = File(dir, ReminderSchedulePublisher.FILE_NAME).readText()
        assertThat(text).doesNotContain("SBI")
        assertThat(publisher.read().single().label).isNull()
        publisher.publish(listOf(ScheduledReminder(now, ReminderType.PASSWORD_REVIEW, "SBI - Dad")), includeLabels = true, now = now)
        assertThat(publisher.read().single().label).isEqualTo("SBI - Dad")
    }

    @Test
    fun dueReminderPostsGenericNotificationWithoutSecrets() = runBlocking<Unit> {
        assumeTrue("notification permission not granted on this device", Notifications.canPost(context))
        publisher.publish(
            listOf(
                ScheduledReminder(now - 1000, ReminderType.PASSWORD_REVIEW, "SBI - Dad"),
                ScheduledReminder(now + 86_400_000L * 40, ReminderType.CARD_EXPIRY, "HDFC Card"),
            ),
            includeLabels = false,
            now = now,
        )
        val result = worker().doWork()
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        val active = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).activeNotifications
        assertThat(active).hasLength(1)
        val n = active.single().notification
        val text = n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
        assertThat(text).isEqualTo("1 item needs attention.")
        assertThat(text).doesNotContain("SBI")
        assertThat(n.publicVersion).isNotNull()
    }

    @Test
    fun nothingDueMeansNoNotification() = runBlocking<Unit> {
        publisher.publish(listOf(ScheduledReminder(now + 86_400_000L, ReminderType.PASSWORD_REVIEW, null)), includeLabels = false, now = now)
        worker().doWork()
        val active = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).activeNotifications
        assertThat(active).isEmpty()
    }
}
