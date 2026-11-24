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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.commonutils.backup.BackupScreenDeps
import de.lemke.sudoku.data.DailyNotifications
import de.lemke.sudoku.data.UserSettings
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
    private val userSettings: UserSettings,
    private val dailyNotifications: DailyNotifications,
    private val setDailyNotificationEnabled: SetDailyNotificationEnabledUseCase,
    backupScreen: BackupScreenDeps,
) : ViewModel() {
    val sectionLabels = backupScreen.sectionLabels
    val backup = backupScreen.stateHolder(viewModelScope)

    val dailySudokuNotificationHour: Int get() = userSettings.dailySudokuNotificationHour
    val dailySudokuNotificationMinute: Int get() = userSettings.dailySudokuNotificationMinute

    val isDailyNotificationChecked: Boolean
        get() = userSettings.dailySudokuNotificationEnabled && !dailyNotifications.isBlocked()

    fun onDailyNotificationToggleRequested(enabled: Boolean): DailyNotificationToggleResult =
        when {
            !enabled -> {
                setDailySudokuNotification(false)
                DailyNotificationToggleResult.Applied
            }

            !dailyNotifications.isPermissionGranted() -> {
                DailyNotificationToggleResult.NeedsPermission
            }

            dailyNotifications.isBlocked() -> {
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

    private fun setDailySudokuNotification(enabled: Boolean) {
        viewModelScope.launch { setDailyNotificationEnabled(enabled) }
    }
}
