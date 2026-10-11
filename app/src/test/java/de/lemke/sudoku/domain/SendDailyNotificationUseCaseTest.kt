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
import de.lemke.sudoku.data.FakeDailyNotifications
import de.lemke.sudoku.data.FakeDailyNotifications.AlarmTime
import de.lemke.sudoku.data.UserSettings
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class SendDailyNotificationUseCaseTest : ShouldSpec(
    {
        lateinit var userSettings: UserSettings
        lateinit var dailyNotifications: FakeDailyNotifications
        lateinit var sendDailyNotification: SendDailyNotificationUseCase

        beforeEach {
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            dailyNotifications = FakeDailyNotifications()
            sendDailyNotification = SendDailyNotificationUseCase(dailyNotifications, userSettings)
        }

        should("post the daily notification") {
            sendDailyNotification()

            dailyNotifications.postedCount shouldBe 1
        }

        should("schedule the daily alarm at the configured time when enabled") {
            userSettings.dailySudokuNotificationHour = 18
            userSettings.dailySudokuNotificationMinute = 30

            sendDailyNotification.setDailySudokuNotification(enable = true)

            dailyNotifications.scheduled shouldBe AlarmTime(hour = 18, minute = 30)
        }

        should("cancel the daily alarm when disabled") {
            sendDailyNotification.setDailySudokuNotification(enable = true)

            sendDailyNotification.setDailySudokuNotification(enable = false)

            dailyNotifications.scheduled.shouldBeNull()
        }
    },
)
