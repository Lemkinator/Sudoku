/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.sudoku.data

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.R
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DefaultDailyNotificationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val fixedNow = Instant.parse("2026-06-15T12:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneId.systemDefault())
    private val dailyNotifications = DefaultDailyNotifications(context, clock)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val channelId get() = context.getString(R.string.daily_sudoku_notification_channel_id)
    private val notificationId = 5

    @Test
    fun `post posts a notification when permission is granted`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        dailyNotifications.post()

        shadowOf(notificationManager).getNotification(notificationId).shouldNotBeNull()
        notificationManager.getNotificationChannel(channelId).shouldNotBeNull()
    }

    @Test
    fun `post creates the channel but skips the notification when permission is denied`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        dailyNotifications.post()

        shadowOf(notificationManager).getNotification(notificationId).shouldBeNull()
        notificationManager.getNotificationChannel(channelId).shouldNotBeNull()
    }

    @Test
    fun `schedule sets an alarm later today when the configured time has not passed yet`() {
        val now = Calendar.getInstance().apply { timeInMillis = clock.millis() }

        dailyNotifications.schedule(hour = 23, minute = 59)

        val scheduled = shadowOf(alarmManager).nextScheduledAlarm
        scheduled.shouldNotBeNull()
        scheduled.type shouldBe AlarmManager.RTC_WAKEUP
        val trigger = Calendar.getInstance().apply { timeInMillis = scheduled.triggerAtTime }
        trigger.get(Calendar.DAY_OF_YEAR) shouldBe now.get(Calendar.DAY_OF_YEAR)
        trigger.get(Calendar.YEAR) shouldBe now.get(Calendar.YEAR)
        trigger.get(Calendar.HOUR_OF_DAY) shouldBe 23
        trigger.get(Calendar.MINUTE) shouldBe 59
    }

    @Test
    fun `schedule rolls the alarm to tomorrow when the configured time already passed today`() {
        val tomorrow =
            Calendar.getInstance().apply {
                timeInMillis = clock.millis()
                add(Calendar.DATE, 1)
            }

        dailyNotifications.schedule(hour = 0, minute = 0)

        val scheduled = shadowOf(alarmManager).nextScheduledAlarm
        scheduled.shouldNotBeNull()
        val trigger = Calendar.getInstance().apply { timeInMillis = scheduled.triggerAtTime }
        trigger.get(Calendar.DAY_OF_YEAR) shouldBe tomorrow.get(Calendar.DAY_OF_YEAR)
        trigger.get(Calendar.YEAR) shouldBe tomorrow.get(Calendar.YEAR)
        trigger.get(Calendar.HOUR_OF_DAY) shouldBe 0
        trigger.get(Calendar.MINUTE) shouldBe 0
    }

    @Test
    fun `cancel removes the scheduled alarm`() {
        dailyNotifications.schedule(hour = 12, minute = 0)
        shadowOf(alarmManager).nextScheduledAlarm.shouldNotBeNull()

        dailyNotifications.cancel()

        shadowOf(alarmManager).nextScheduledAlarm.shouldBeNull()
    }

    @Test
    fun `isPermissionGranted follows the POST_NOTIFICATIONS grant`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        dailyNotifications.isPermissionGranted() shouldBe true

        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        dailyNotifications.isPermissionGranted() shouldBe false
    }

    @Test
    fun `isBlocked is false while notifications, the channel and the permission are on`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        dailyNotifications.post()

        dailyNotifications.isBlocked() shouldBe false
    }

    @Test
    fun `isBlocked is true while the app's notifications are off`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(notificationManager).setNotificationsEnabled(false)

        dailyNotifications.isBlocked() shouldBe true
    }

    @Test
    fun `isBlocked is true while the daily channel is muted`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationManager.createNotificationChannel(NotificationChannel(channelId, "Daily", NotificationManager.IMPORTANCE_NONE))

        dailyNotifications.isBlocked() shouldBe true
    }

    @Test
    fun `isBlocked is true while the permission is denied`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        dailyNotifications.isBlocked() shouldBe true
    }
}
