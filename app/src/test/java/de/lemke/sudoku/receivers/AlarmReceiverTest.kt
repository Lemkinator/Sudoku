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

package de.lemke.sudoku.receivers

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.InstallIn
import dagger.hilt.android.EarlyEntryPoint
import dagger.hilt.android.EarlyEntryPoints
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@EarlyEntryPoint
@InstallIn(SingletonComponent::class)
interface AlarmReceiverTestEntryPoint {
    fun userSettings(): UserSettings

    fun saveSudoku(): SaveSudokuUseCase

    fun sendDailyNotification(): SendDailyNotificationUseCase
}

/**
 * No HiltAndroidRule: the receiver must work before any test component exists. The early component it reads ignores
 * @BindValue and @UninstallModules, so the tests seed through that graph and await the receiver's goAsync result.
 *
 * sdk = 36: Robolectric's max supported SDK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class AlarmReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
    private val graph = EarlyEntryPoints.get(context, AlarmReceiverTestEntryPoint::class.java)
    private val notificationManager = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val alarmManager = shadowOf(context.getSystemService(AlarmManager::class.java))

    @Before
    fun setup() {
        shadowOf(context).grantPermissions(POST_NOTIFICATIONS)
    }

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(context, AlarmReceiver::class.java).setAction(action))
        shadowOf(Looper.getMainLooper()).idle()
        val receiver =
            shadowOf(context)
                .registeredReceivers
                .map { it.broadcastReceiver }
                .filterIsInstance<AlarmReceiver>()
                .single()
        shadowOf(shadowOf(receiver).originalPendingResult).future.get(10, TimeUnit.SECONDS)
    }

    private fun scheduledHourAndMinute(): Pair<Int, Int> {
        val alarm = alarmManager.peekNextScheduledAlarm().shouldNotBeNull()
        alarm.getType() shouldBe AlarmManager.RTC_WAKEUP
        val trigger = Calendar.getInstance().apply { timeInMillis = alarm.triggerAtMs }
        return trigger.get(Calendar.HOUR_OF_DAY) to trigger.get(Calendar.MINUTE)
    }

    @Test
    fun `onReceive sends the daily notification when enabled and today's sudoku is not completed`() {
        graph.userSettings().dailySudokuNotificationEnabled = true

        broadcast("de.lemke.sudoku.TEST_ALARM")

        val notifications = notificationManager.allNotifications
        notifications shouldHaveSize 1
        val notification = notifications.single()
        notification.channelId shouldBe "Daily_Sudoku_Notification_Channel"
        notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString() shouldBe "Daily Sudoku"
        notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "Hey, it is time for your daily Sudoku!"
        scheduledHourAndMinute() shouldBe (9 to 0)
    }

    @Test
    fun `onReceive does not send a notification and cancels the alarm when disabled`() {
        graph.sendDailyNotification().setDailySudokuNotification(enable = true)
        alarmManager.peekNextScheduledAlarm().shouldNotBeNull()
        graph.userSettings().dailySudokuNotificationEnabled = false

        broadcast("de.lemke.sudoku.TEST_ALARM")

        notificationManager.allNotifications.shouldBeEmpty()
        alarmManager.peekNextScheduledAlarm().shouldBeNull()
    }

    @Test
    fun `onReceive does not send a notification when today's daily sudoku is already completed`() {
        graph.userSettings().dailySudokuNotificationEnabled = true
        val size = 4
        val blockSize = 2
        val today =
            Sudoku.create(
                size = size,
                difficulty = Difficulty.VERY_EASY,
                modeLevel = MODE_DAILY,
                fields =
                    MutableList(size * size) { index ->
                        val row = index / size
                        val col = index % size
                        val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size + 1
                        Field(position = Position.create(index, size), solution = solution, value = solution, given = true)
                    },
            )
        runBlocking { graph.saveSudoku()(today) }

        broadcast("de.lemke.sudoku.TEST_ALARM")

        notificationManager.allNotifications.shouldBeEmpty()
        scheduledHourAndMinute() shouldBe (9 to 0)
    }

    @Test
    fun `onReceive on BOOT_COMPLETED does not send a notification but reschedules`() {
        graph.userSettings().dailySudokuNotificationEnabled = true
        graph.userSettings().dailySudokuNotificationHour = 18
        graph.userSettings().dailySudokuNotificationMinute = 30

        broadcast(Intent.ACTION_BOOT_COMPLETED)

        notificationManager.allNotifications.shouldBeEmpty()
        scheduledHourAndMinute() shouldBe (18 to 30)
    }

    @Test
    fun `onReceive on MY_PACKAGE_REPLACED does not send a notification but reschedules`() {
        graph.userSettings().dailySudokuNotificationEnabled = true

        broadcast(Intent.ACTION_MY_PACKAGE_REPLACED)

        notificationManager.allNotifications.shouldBeEmpty()
        scheduledHourAndMinute() shouldBe (9 to 0)
    }
}
