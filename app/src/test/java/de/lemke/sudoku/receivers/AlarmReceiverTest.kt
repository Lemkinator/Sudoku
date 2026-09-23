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
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.di.MainDispatcher
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.di.DispatchersModule
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
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

/**
 * Hilt's `@AndroidEntryPoint` transform injects inside `onReceive`, so only a real broadcast dispatch runs it.
 *
 * sdk = 36: Robolectric's max supported SDK.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class AlarmReceiverTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @DefaultDispatcher
    @JvmField
    val testDefaultDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()

    @BindValue
    @IoDispatcher
    @JvmField
    val testIoDispatcher: CoroutineDispatcher = Dispatchers.IO

    @BindValue
    @MainDispatcher
    @JvmField
    val testMainDispatcher: CoroutineDispatcher = Dispatchers.Main

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var userSettings: UserSettings

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Inject
    lateinit var sendDailyNotification: SendDailyNotificationUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        shadowOf(ApplicationProvider.getApplicationContext<HiltTestApplication>()).grantPermissions(POST_NOTIFICATIONS)
    }

    private fun broadcast(action: String) {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        context.sendBroadcast(Intent(context, AlarmReceiver::class.java).setAction(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun notificationManager(): ShadowNotificationManager {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        return shadowOf(context.getSystemService(android.app.NotificationManager::class.java))
    }

    @Test
    fun `onReceive sends the daily notification when enabled and today's sudoku is not completed`() {
        userSettings.dailySudokuNotificationEnabled = true
        broadcast("de.lemke.sudoku.TEST_ALARM")

        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val notifications = notificationManager().allNotifications
        notifications shouldHaveSize 1
        val notification = notifications.single()
        notification.channelId shouldBe context.getString(R.string.daily_sudoku_notification_channel_id)
        notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString() shouldBe context.getString(R.string.daily_sudoku)
    }

    @Test
    fun `onReceive does not send a notification and cancels the alarm when disabled`() {
        sendDailyNotification.setDailySudokuNotification(enable = true)
        shadowOf(alarmManager()).nextScheduledAlarm.shouldNotBeNull()
        userSettings.dailySudokuNotificationEnabled = false
        broadcast("de.lemke.sudoku.TEST_ALARM")
        notificationManager().allNotifications.shouldBeEmpty()
        shadowOf(alarmManager()).nextScheduledAlarm.shouldBeNull()
    }

    @Test
    fun `onReceive does not send a notification when today's daily sudoku is already completed`() {
        userSettings.dailySudokuNotificationEnabled = true
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
        runBlocking { saveSudoku(today) }
        broadcast("de.lemke.sudoku.TEST_ALARM")
        notificationManager().allNotifications.shouldBeEmpty()
        shadowOf(alarmManager()).nextScheduledAlarm.shouldNotBeNull()
    }

    private fun alarmManager(): AlarmManager {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        return context.getSystemService(AlarmManager::class.java)
    }

    @Test
    fun `onReceive on BOOT_COMPLETED does not send a notification but reschedules`() {
        userSettings.dailySudokuNotificationEnabled = true
        broadcast(Intent.ACTION_BOOT_COMPLETED)
        notificationManager().allNotifications.shouldBeEmpty()
        shadowOf(alarmManager()).nextScheduledAlarm.shouldNotBeNull()
    }

    @Test
    fun `onReceive on MY_PACKAGE_REPLACED does not send a notification but reschedules`() {
        userSettings.dailySudokuNotificationEnabled = true
        broadcast(Intent.ACTION_MY_PACKAGE_REPLACED)
        notificationManager().allNotifications.shouldBeEmpty()
        shadowOf(alarmManager()).nextScheduledAlarm.shouldNotBeNull()
    }
}
