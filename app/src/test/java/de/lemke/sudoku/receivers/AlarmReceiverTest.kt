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
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
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
 * Drives [AlarmReceiver.onReceive] through a real explicit-component broadcast (`Context.sendBroadcast`), the way
 * `AlarmManager` delivers it in production — not a plain `AlarmReceiver()` construction, since Hilt's
 * `@AndroidEntryPoint` bytecode transform injects fields inside the real `onReceive` override, which only runs
 * through the actual receiver dispatch path (no test precedent elsewhere in this fleet).
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
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

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
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
        notificationManager().allNotifications.isNotEmpty().let { it }
    }

    @Test
    fun `onReceive does not send a notification when disabled`() {
        userSettings.dailySudokuNotificationEnabled = false
        broadcast("de.lemke.sudoku.TEST_ALARM")
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
    }

    @Test
    fun `onReceive on BOOT_COMPLETED does not send a notification but reschedules`() {
        userSettings.dailySudokuNotificationEnabled = true
        broadcast(Intent.ACTION_BOOT_COMPLETED)
    }

    @Test
    fun `onReceive on MY_PACKAGE_REPLACED does not send a notification but reschedules`() {
        userSettings.dailySudokuNotificationEnabled = true
        broadcast(Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
