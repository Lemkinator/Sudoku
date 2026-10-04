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
import de.lemke.sudoku.domain.IsNotificationPermissionGrantedUseCase
import de.lemke.sudoku.domain.SetDailyNotificationEnabledUseCase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class IntroViewModelTest : ShouldSpec(
    {
        val setDailyNotificationEnabled = mockk<SetDailyNotificationEnabledUseCase>(relaxUnitFun = true)
        val isNotificationPermissionGranted = mockk<IsNotificationPermissionGrantedUseCase>()

        beforeEach {
            clearMocks(setDailyNotificationEnabled, isNotificationPermissionGranted)
        }

        fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
            IntroViewModel(setDailyNotificationEnabled, isNotificationPermissionGranted, savedStateHandle)

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
            verify(exactly = 1) { setDailyNotificationEnabled(false) }
        }

        should("onNotificationsAccepted enables notifications and reports Saved when permission is granted") {
            every { isNotificationPermissionGranted() } returns true
            val viewModel = newViewModel()

            viewModel.onNotificationsAccepted()

            viewModel.notificationChoice.value shouldBe NotificationChoice.Saved
            verify(exactly = 1) { setDailyNotificationEnabled(true) }
        }

        should("onNotificationsAccepted only reports RequestPermission when permission is not granted") {
            every { isNotificationPermissionGranted() } returns false
            val viewModel = newViewModel()

            viewModel.onNotificationsAccepted()

            viewModel.notificationChoice.value shouldBe NotificationChoice.RequestPermission
            verify(exactly = 0) { setDailyNotificationEnabled(any()) }
        }

        should("onNotificationPermissionResult enables notifications and reports Saved when granted") {
            val viewModel = newViewModel()

            viewModel.onNotificationPermissionResult(true)

            viewModel.notificationChoice.value shouldBe NotificationChoice.Saved
            verify(exactly = 1) { setDailyNotificationEnabled(true) }
        }

        should("onNotificationPermissionResult disables notifications and reports Saved when not granted") {
            val viewModel = newViewModel()

            viewModel.onNotificationPermissionResult(false)

            viewModel.notificationChoice.value shouldBe NotificationChoice.Saved
            verify(exactly = 1) { setDailyNotificationEnabled(false) }
        }

        should("handling the current result returns to pending") {
            val viewModel = newViewModel()
            viewModel.onNotificationsDeclined()

            viewModel.onNotificationChoiceHandled(NotificationChoice.Saved)

            viewModel.notificationChoice.value shouldBe NotificationChoice.Pending
        }

        should("handling a requested permission returns to pending, and the permission result then reports Saved") {
            every { isNotificationPermissionGranted() } returns false
            val viewModel = newViewModel()
            viewModel.onNotificationsAccepted()

            viewModel.onNotificationChoiceHandled(NotificationChoice.RequestPermission)

            viewModel.notificationChoice.value shouldBe NotificationChoice.Pending
            viewModel.onNotificationPermissionResult(true)
            viewModel.notificationChoice.value shouldBe NotificationChoice.Saved
        }

        should("handling a stale result keeps the current result") {
            every { isNotificationPermissionGranted() } returns false
            val viewModel = newViewModel()
            viewModel.onNotificationsAccepted()

            viewModel.onNotificationChoiceHandled(NotificationChoice.Saved)

            viewModel.notificationChoice.value shouldBe NotificationChoice.RequestPermission
        }
    },
)
