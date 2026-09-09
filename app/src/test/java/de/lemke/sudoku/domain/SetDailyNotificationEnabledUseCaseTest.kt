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

package de.lemke.sudoku.domain

import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.data.UserSettings
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class SetDailyNotificationEnabledUseCaseTest : ShouldSpec(
    {
        lateinit var userSettings: UserSettings
        val sendDailyNotification = mockk<SendDailyNotificationUseCase>(relaxUnitFun = true)
        lateinit var useCase: SetDailyNotificationEnabledUseCase

        beforeEach {
            clearMocks(sendDailyNotification)
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            useCase = SetDailyNotificationEnabledUseCase(userSettings, sendDailyNotification)
        }

        should("writes true to userSettings and enables the daily notification") {
            useCase(true)

            userSettings.dailySudokuNotificationEnabled.shouldBeTrue()
            verify(exactly = 1) { sendDailyNotification.setDailySudokuNotification(enable = true) }
        }

        should("writes false to userSettings and disables the daily notification") {
            useCase(false)

            userSettings.dailySudokuNotificationEnabled.shouldBeFalse()
            verify(exactly = 1) { sendDailyNotification.setDailySudokuNotification(enable = false) }
        }
    },
)
