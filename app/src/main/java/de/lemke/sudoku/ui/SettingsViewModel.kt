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

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_NONE
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.DeleteInvalidSudokusUseCase
import de.lemke.sudoku.domain.IsNotificationPermissionGrantedUseCase
import de.lemke.sudoku.domain.SetDailyNotificationEnabledUseCase
import javax.inject.Inject
import kotlinx.coroutines.launch

sealed interface DailyNotificationToggleResult {
    data object Applied : DailyNotificationToggleResult

    data object NeedsPermission : DailyNotificationToggleResult

    data object SystemNotificationsDisabled : DailyNotificationToggleResult
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userSettings: UserSettings,
    private val setDailyNotificationEnabled: SetDailyNotificationEnabledUseCase,
    private val isNotificationPermissionGranted: IsNotificationPermissionGrantedUseCase,
    private val deleteInvalidSudokus: DeleteInvalidSudokusUseCase,
) : ViewModel() {
    var errorLimit: Int
        get() = userSettings.errorLimit
        set(value) {
            userSettings.errorLimit = value
        }

    var keepScreenOn: Boolean
        get() = userSettings.keepScreenOn
        set(value) {
            userSettings.keepScreenOn = value
        }

    var highlightRegional: Boolean
        get() = userSettings.highlightRegional
        set(value) {
            userSettings.highlightRegional = value
        }

    var highlightNumber: Boolean
        get() = userSettings.highlightNumber
        set(value) {
            userSettings.highlightNumber = value
        }

    var animationsEnabled: Boolean
        get() = userSettings.animationsEnabled
        set(value) {
            userSettings.animationsEnabled = value
        }

    val dailySudokuNotificationHour: Int get() = userSettings.dailySudokuNotificationHour
    val dailySudokuNotificationMinute: Int get() = userSettings.dailySudokuNotificationMinute

    val isDailyNotificationChecked: Boolean
        get() = userSettings.dailySudokuNotificationEnabled && systemNotificationsEnabled()

    fun onDailyNotificationToggleRequested(enabled: Boolean): DailyNotificationToggleResult =
        when {
            !enabled -> {
                setDailySudokuNotification(false)
                DailyNotificationToggleResult.Applied
            }

            !isNotificationPermissionGranted() -> {
                DailyNotificationToggleResult.NeedsPermission
            }

            !systemNotificationsEnabled() -> {
                DailyNotificationToggleResult.SystemNotificationsDisabled
            }

            else -> {
                setDailySudokuNotification(true)
                DailyNotificationToggleResult.Applied
            }
        }

    fun onNotificationPermissionResult(isGranted: Boolean) = setDailySudokuNotification(isGranted)

    fun onDailyNotificationTimeSelected(
        hourOfDay: Int,
        minute: Int,
    ) {
        userSettings.dailySudokuNotificationHour = hourOfDay
        userSettings.dailySudokuNotificationMinute = minute
        setDailySudokuNotification(true)
    }

    suspend fun onDeleteInvalidSudokusConfirmed() = deleteInvalidSudokus()

    private fun setDailySudokuNotification(enabled: Boolean) {
        viewModelScope.launch { setDailyNotificationEnabled(enabled) }
    }

    private fun systemNotificationsEnabled(): Boolean {
        val channelId = context.getString(R.string.daily_sudoku_notification_channel_id)
        val notificationManager = NotificationManagerCompat.from(context)
        return when {
            !notificationManager.areNotificationsEnabled() -> false
            notificationManager.getNotificationChannel(channelId)?.importance == IMPORTANCE_NONE -> false
            SDK_INT >= TIRAMISU -> ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PERMISSION_GRANTED
            else -> true
        }
    }
}
