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
import app.cash.turbine.test
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

        should("onNotificationsDeclined disables notifications and emits AdvanceOnboarding") {
            val viewModel = newViewModel()
            viewModel.events.test {
                viewModel.onNotificationsDeclined()
                awaitItem() shouldBe IntroEvent.AdvanceOnboarding
            }
            verify(exactly = 1) { setDailyNotificationEnabled(false) }
        }

        should("onNotificationsAccepted enables notifications and emits AdvanceOnboarding when permission is granted") {
            every { isNotificationPermissionGranted() } returns true
            val viewModel = newViewModel()
            viewModel.events.test {
                viewModel.onNotificationsAccepted()
                awaitItem() shouldBe IntroEvent.AdvanceOnboarding
            }
            verify(exactly = 1) { setDailyNotificationEnabled(true) }
        }

        should("onNotificationsAccepted only emits RequestNotificationPermission when permission is not granted") {
            every { isNotificationPermissionGranted() } returns false
            val viewModel = newViewModel()
            viewModel.events.test {
                viewModel.onNotificationsAccepted()
                awaitItem() shouldBe IntroEvent.RequestNotificationPermission
            }
            verify(exactly = 0) { setDailyNotificationEnabled(any()) }
        }

        should("onNotificationPermissionResult enables notifications and advances when granted") {
            val viewModel = newViewModel()
            viewModel.events.test {
                viewModel.onNotificationPermissionResult(true)
                awaitItem() shouldBe IntroEvent.AdvanceOnboarding
            }
            verify(exactly = 1) { setDailyNotificationEnabled(true) }
        }

        should("onNotificationPermissionResult disables notifications and advances when not granted") {
            val viewModel = newViewModel()
            viewModel.events.test {
                viewModel.onNotificationPermissionResult(false)
                awaitItem() shouldBe IntroEvent.AdvanceOnboarding
            }
            verify(exactly = 1) { setDailyNotificationEnabled(false) }
        }
    },
)
