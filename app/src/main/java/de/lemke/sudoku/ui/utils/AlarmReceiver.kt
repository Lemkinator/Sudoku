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

package de.lemke.sudoku.ui.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.IsDailySudokuCompletedUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject
    lateinit var sendDailyNotification: SendDailyNotificationUseCase

    @Inject
    lateinit var isDailySudokuCompleted: IsDailySudokuCompletedUseCase

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    /**
     * sends notification when receives alarm
     * and then reschedule the reminder again
     * */
    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("GlobalCoroutineUsage")
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        GlobalScope.launch {
            val notificationEnabled = getUserSettings().dailySudokuNotificationEnabled
            if (
                !intent.action.equals(Intent.ACTION_BOOT_COMPLETED) &&
                !intent.action.equals(Intent.ACTION_MY_PACKAGE_REPLACED) &&
                notificationEnabled &&
                !isDailySudokuCompleted()
            ) {
                sendDailyNotification()
            }
            sendDailyNotification.setDailySudokuNotification(enable = notificationEnabled)
        }
    }
}
