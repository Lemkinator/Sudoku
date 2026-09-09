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

package de.lemke.sudoku.ui

import android.app.NotificationChannel
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_NONE
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.DeleteInvalidSudokusUseCase
import de.lemke.sudoku.domain.IsNotificationPermissionGrantedUseCase
import de.lemke.sudoku.domain.SetDailyNotificationEnabledUseCase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * [SettingsViewModel.systemNotificationsEnabled] branches on `SDK_INT >= TIRAMISU` to decide whether to also
 * consult [androidx.core.content.ContextCompat.checkSelfPermission]. On a plain JVM unit test (no Robolectric,
 * per this task's pure-JVM Kotest style) `Build.VERSION.SDK_INT` resolves to the android.jar stub's default of
 * `0`, so that branch is permanently unreachable here and always falls through to `else -> true` once the first
 * two checks pass — matching sibling repos' precedent (e.g. GetApplicationInfoUseCaseTest's comment on needing
 * Robolectric's `@Config(sdk = ...)` to drive SDK_INT). Exercising the `checkSelfPermission` branch is out of
 * scope for this pure-JVM ViewModel test; see the task report.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest : ShouldSpec(
    {
        val context = mockk<Context>(relaxed = true)
        val notificationManager = mockk<NotificationManagerCompat>()
        lateinit var userSettings: UserSettings
        val setDailyNotificationEnabled = mockk<SetDailyNotificationEnabledUseCase>(relaxUnitFun = true)
        val isNotificationPermissionGranted = mockk<IsNotificationPermissionGrantedUseCase>()
        val deleteInvalidSudokus = mockk<DeleteInvalidSudokusUseCase>()
        lateinit var viewModel: SettingsViewModel

        beforeEach {
            clearMocks(context, notificationManager, setDailyNotificationEnabled, isNotificationPermissionGranted, deleteInvalidSudokus)
            mockkStatic(NotificationManagerCompat::class)
            every { context.getString(R.string.daily_sudoku_notification_channel_id) } returns "channelId"
            every { NotificationManagerCompat.from(context) } returns notificationManager
            every { notificationManager.areNotificationsEnabled() } returns true
            every { notificationManager.getNotificationChannel("channelId") } returns null
            every { isNotificationPermissionGranted() } returns true
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            viewModel =
                SettingsViewModel(
                    context,
                    userSettings,
                    setDailyNotificationEnabled,
                    isNotificationPermissionGranted,
                    deleteInvalidSudokus,
                )
        }

        afterEach { unmockkAll() }

        should("dailySudokuNotificationHour reads through to userSettings") {
            userSettings.dailySudokuNotificationHour = 7
            viewModel.dailySudokuNotificationHour shouldBe 7
        }

        should("dailySudokuNotificationMinute reads through to userSettings") {
            userSettings.dailySudokuNotificationMinute = 45
            viewModel.dailySudokuNotificationMinute shouldBe 45
        }

        context("isDailyNotificationChecked") {
            should("is false and does not consult system state when dailySudokuNotificationEnabled is false") {
                userSettings.dailySudokuNotificationEnabled = false
                viewModel.isDailyNotificationChecked.shouldBeFalse()
                verify(exactly = 0) { notificationManager.areNotificationsEnabled() }
            }

            should("is false when system notifications are disabled") {
                userSettings.dailySudokuNotificationEnabled = true
                every { notificationManager.areNotificationsEnabled() } returns false
                viewModel.isDailyNotificationChecked.shouldBeFalse()
            }

            should("is false when the notification channel's importance is IMPORTANCE_NONE") {
                userSettings.dailySudokuNotificationEnabled = true
                val channel = mockk<NotificationChannel>()
                every { channel.importance } returns IMPORTANCE_NONE
                every { notificationManager.getNotificationChannel("channelId") } returns channel
                viewModel.isDailyNotificationChecked.shouldBeFalse()
            }

            should("is true when enabled, notifications are on, and the channel is not silenced") {
                userSettings.dailySudokuNotificationEnabled = true
                viewModel.isDailyNotificationChecked.shouldBeTrue()
            }
        }

        context("onDailyNotificationToggleRequested") {
            should("returns Applied and disables the notification when enabled is false") {
                val result = viewModel.onDailyNotificationToggleRequested(false)
                result shouldBe DailyNotificationToggleResult.Applied
                verify(exactly = 1) { setDailyNotificationEnabled(false) }
            }

            should("returns NeedsPermission when enabled is true and permission is not granted") {
                every { isNotificationPermissionGranted() } returns false
                val result = viewModel.onDailyNotificationToggleRequested(true)
                result shouldBe DailyNotificationToggleResult.NeedsPermission
                verify(exactly = 0) { setDailyNotificationEnabled(any()) }
            }

            should("returns SystemNotificationsDisabled when permission is granted but system notifications are off") {
                every { notificationManager.areNotificationsEnabled() } returns false
                val result = viewModel.onDailyNotificationToggleRequested(true)
                result shouldBe DailyNotificationToggleResult.SystemNotificationsDisabled
                verify(exactly = 0) { setDailyNotificationEnabled(any()) }
            }

            should("returns Applied and enables the notification when permission and system notifications allow it") {
                val result = viewModel.onDailyNotificationToggleRequested(true)
                result shouldBe DailyNotificationToggleResult.Applied
                verify(exactly = 1) { setDailyNotificationEnabled(true) }
            }
        }

        should("onNotificationPermissionResult enables the notification when granted") {
            viewModel.onNotificationPermissionResult(true)
            verify(exactly = 1) { setDailyNotificationEnabled(true) }
        }

        should("onNotificationPermissionResult disables the notification when not granted") {
            viewModel.onNotificationPermissionResult(false)
            verify(exactly = 1) { setDailyNotificationEnabled(false) }
        }

        should("onDailyNotificationTimeSelected writes hour and minute to userSettings and enables the notification") {
            viewModel.onDailyNotificationTimeSelected(14, 30)
            userSettings.dailySudokuNotificationHour shouldBe 14
            userSettings.dailySudokuNotificationMinute shouldBe 30
            verify(exactly = 1) { setDailyNotificationEnabled(true) }
        }

        should("onDeleteInvalidSudokusConfirmed delegates to deleteInvalidSudokus") {
            coEvery { deleteInvalidSudokus() } returns Unit
            viewModel.onDeleteInvalidSudokusConfirmed()
            coVerify(exactly = 1) { deleteInvalidSudokus() }
        }
    },
)
