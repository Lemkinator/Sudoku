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

import androidx.lifecycle.SavedStateHandle
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.data.FakeDailyNotifications
import de.lemke.sudoku.data.FakeDailyNotifications.AlarmTime
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.IsNotificationPermissionGrantedUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.SetDailyNotificationEnabledUseCase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class IntroViewModelTest : ShouldSpec(
    {
        lateinit var userSettings: UserSettings
        lateinit var dailyNotifications: FakeDailyNotifications

        beforeEach {
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            dailyNotifications = FakeDailyNotifications()
        }

        fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
            IntroViewModel(
                SetDailyNotificationEnabledUseCase(userSettings, SendDailyNotificationUseCase(dailyNotifications, userSettings)),
                IsNotificationPermissionGrantedUseCase(dailyNotifications),
                savedStateHandle,
            )

        should("openedFromSettings is true when the saved state handle carries true") {
            val viewModel = newViewModel(SavedStateHandle(mapOf(IntroActivity.KEY_OPENED_FROM_SETTINGS to true)))
            viewModel.openedFromSettings.shouldBeTrue()
        }

        should("openedFromSettings is false when the saved state handle carries false") {
            val viewModel = newViewModel(SavedStateHandle(mapOf(IntroActivity.KEY_OPENED_FROM_SETTINGS to false)))
            viewModel.openedFromSettings.shouldBeFalse()
        }

        should("openedFromSettings defaults to false when the key is missing from the saved state handle") {
            val viewModel = newViewModel(SavedStateHandle())
            viewModel.openedFromSettings.shouldBeFalse()
        }

        should("notificationChoice starts pending") {
            val viewModel = newViewModel()
            viewModel.notificationChoice.value shouldBe NotificationChoice.Pending
        }

        should("onNotificationsDeclined disables notifications and reports Saved") {
            val viewModel = newViewModel()

            viewModel.onNotificationsDeclined()

            viewModel.notificationChoice.value shouldBe NotificationChoice.Saved
            userSettings.dailySudokuNotificationEnabled.shouldBeFalse()
            dailyNotifications.scheduled.shouldBeNull()
        }

        should("onNotificationsAccepted enables notifications and reports Saved when permission is granted") {
            dailyNotifications.permissionGranted = true
            val viewModel = newViewModel()

            viewModel.onNotificationsAccepted()

            viewModel.notificationChoice.value shouldBe NotificationChoice.Saved
            userSettings.dailySudokuNotificationEnabled.shouldBeTrue()
            dailyNotifications.scheduled shouldBe AlarmTime(hour = 9, minute = 0)
        }

        should("onNotificationsAccepted only reports RequestPermission when permission is not granted") {
            dailyNotifications.permissionGranted = false
            val viewModel = newViewModel()

            viewModel.onNotificationsAccepted()

            viewModel.notificationChoice.value shouldBe NotificationChoice.RequestPermission
            userSettings.dailySudokuNotificationEnabled.shouldBeTrue()
            dailyNotifications.scheduled.shouldBeNull()
        }

        should("onNotificationPermissionResult enables notifications and reports Saved when granted") {
            val viewModel = newViewModel()

            viewModel.onNotificationPermissionResult(true)

            viewModel.notificationChoice.value shouldBe NotificationChoice.Saved
            userSettings.dailySudokuNotificationEnabled.shouldBeTrue()
            dailyNotifications.scheduled shouldBe AlarmTime(hour = 9, minute = 0)
        }

        should("onNotificationPermissionResult disables notifications and reports Saved when not granted") {
            val viewModel = newViewModel()

            viewModel.onNotificationPermissionResult(false)

            viewModel.notificationChoice.value shouldBe NotificationChoice.Saved
            userSettings.dailySudokuNotificationEnabled.shouldBeFalse()
            dailyNotifications.scheduled.shouldBeNull()
        }

        should("handling the current result returns to pending") {
            val viewModel = newViewModel()
            viewModel.onNotificationsDeclined()

            viewModel.onNotificationChoiceHandled(NotificationChoice.Saved)

            viewModel.notificationChoice.value shouldBe NotificationChoice.Pending
        }

        should("handling a requested permission returns to pending, and the permission result then reports Saved") {
            dailyNotifications.permissionGranted = false
            val viewModel = newViewModel()
            viewModel.onNotificationsAccepted()

            viewModel.onNotificationChoiceHandled(NotificationChoice.RequestPermission)

            viewModel.notificationChoice.value shouldBe NotificationChoice.Pending
            viewModel.onNotificationPermissionResult(true)
            viewModel.notificationChoice.value shouldBe NotificationChoice.Saved
        }

        should("handling a stale result keeps the current result") {
            dailyNotifications.permissionGranted = false
            val viewModel = newViewModel()
            viewModel.onNotificationsAccepted()

            viewModel.onNotificationChoiceHandled(NotificationChoice.Saved)

            viewModel.notificationChoice.value shouldBe NotificationChoice.RequestPermission
        }
    },
)
