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
import de.lemke.commonutils.backup.BackupDocuments
import de.lemke.commonutils.backup.BackupSectionLabel
import de.lemke.commonutils.backup.BackupStateHolder
import de.lemke.commonutils.backup.core.BackupEngine
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.sudoku.data.DailyNotifications
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.DeleteInvalidSudokusUseCase
import de.lemke.sudoku.domain.IsNotificationPermissionGrantedUseCase
import de.lemke.sudoku.domain.SetDailyNotificationEnabledUseCase
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface DailyNotificationToggleResult {
    data object Applied : DailyNotificationToggleResult

    data object NeedsPermission : DailyNotificationToggleResult

    data object SystemNotificationsDisabled : DailyNotificationToggleResult
}

/** The deletion of invalid sudokus. The screen closes its dialog on a [Result] and then reports it handled. */
sealed interface InvalidSudokuDeletion {
    sealed interface Result : InvalidSudokuDeletion

    data object Idle : InvalidSudokuDeletion

    data object Running : InvalidSudokuDeletion

    data object Finished : Result
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userSettings: UserSettings,
    private val dailyNotifications: DailyNotifications,
    private val setDailyNotificationEnabled: SetDailyNotificationEnabledUseCase,
    private val isNotificationPermissionGranted: IsNotificationPermissionGrantedUseCase,
    private val deleteInvalidSudokus: DeleteInvalidSudokusUseCase,
    val sectionLabels: Set<BackupSectionLabel>,
    backupEngine: BackupEngine,
    backupDocuments: BackupDocuments,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val backup = BackupStateHolder(viewModelScope, backupEngine, backupDocuments, defaultDispatcher)

    val invalidSudokuDeletion: StateFlow<InvalidSudokuDeletion>
        field = MutableStateFlow<InvalidSudokuDeletion>(InvalidSudokuDeletion.Idle)

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

            !isNotificationPermissionGranted() -> {
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

    fun onDeleteInvalidSudokusConfirmed() {
        if (invalidSudokuDeletion.value == InvalidSudokuDeletion.Running) return
        invalidSudokuDeletion.value = InvalidSudokuDeletion.Running
        viewModelScope.launch {
            deleteInvalidSudokus()
            delay(MIN_INVALID_SUDOKU_DELETION_DURATION)
            invalidSudokuDeletion.value = InvalidSudokuDeletion.Finished
        }
    }

    fun onInvalidSudokuDeletionHandled(result: InvalidSudokuDeletion.Result) {
        invalidSudokuDeletion.compareAndSet(result, InvalidSudokuDeletion.Idle)
    }

    private fun setDailySudokuNotification(enabled: Boolean) {
        viewModelScope.launch { setDailyNotificationEnabled(enabled) }
    }

    private companion object {
        val MIN_INVALID_SUDOKU_DELETION_DURATION = 500.milliseconds
    }
}
