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
import android.net.Uri
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
import de.lemke.sudoku.domain.ExportDataUseCase
import de.lemke.sudoku.domain.ImportDataUseCase
import de.lemke.sudoku.domain.IsNotificationPermissionGrantedUseCase
import de.lemke.sudoku.domain.SetDailyNotificationEnabledUseCase
import de.lemke.sudoku.domain.model.DataImportResult
import de.lemke.sudoku.domain.model.ExportProgress
import de.lemke.sudoku.domain.model.ImportProgress
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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

/** An export or import of all sudokus. The screen shows the progress of a [Running] one and a [Result], then reports it handled. */
sealed interface DataTransfer {
    sealed interface Running : DataTransfer

    sealed interface Result : DataTransfer

    data object Idle : DataTransfer

    data class Exporting(
        val progress: ExportProgress,
    ) : Running

    data class Importing(
        val progress: ImportProgress,
    ) : Running

    data object Exported : Result

    data class Imported(
        val result: DataImportResult,
    ) : Result
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userSettings: UserSettings,
    private val setDailyNotificationEnabled: SetDailyNotificationEnabledUseCase,
    private val isNotificationPermissionGranted: IsNotificationPermissionGrantedUseCase,
    private val deleteInvalidSudokus: DeleteInvalidSudokusUseCase,
    private val exportData: ExportDataUseCase,
    private val importData: ImportDataUseCase,
) : ViewModel() {
    val invalidSudokuDeletion: StateFlow<InvalidSudokuDeletion>
        field = MutableStateFlow<InvalidSudokuDeletion>(InvalidSudokuDeletion.Idle)

    val dataTransfer: StateFlow<DataTransfer>
        field = MutableStateFlow<DataTransfer>(DataTransfer.Idle)

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
        invalidSudokuDeletion.update { if (it == result) InvalidSudokuDeletion.Idle else it }
    }

    fun onExportDestinationPicked(uri: Uri) =
        startDataTransfer(DataTransfer.Exporting(ExportProgress.Reading)) {
            exportData(uri) { dataTransfer.value = DataTransfer.Exporting(it) }
            DataTransfer.Exported
        }

    fun onImportFilePicked(uri: Uri) =
        startDataTransfer(DataTransfer.Importing(ImportProgress.Reading)) {
            DataTransfer.Imported(importData(uri) { dataTransfer.value = DataTransfer.Importing(it) })
        }

    fun onDataTransferHandled(result: DataTransfer.Result) {
        dataTransfer.update { if (it == result) DataTransfer.Idle else it }
    }

    private fun startDataTransfer(
        running: DataTransfer.Running,
        work: suspend () -> DataTransfer.Result,
    ) {
        if (dataTransfer.value is DataTransfer.Running) return
        dataTransfer.value = running
        viewModelScope.launch { dataTransfer.value = work() }
    }

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

    private companion object {
        val MIN_INVALID_SUDOKU_DELETION_DURATION = 500.milliseconds
    }
}
