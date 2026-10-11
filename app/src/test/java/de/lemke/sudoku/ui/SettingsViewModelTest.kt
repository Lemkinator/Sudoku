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

import android.app.Application
import android.net.Uri
import android.os.Looper
import de.lemke.commonutils.freshTestPreferences
import de.lemke.sudoku.data.FakeDailyNotifications
import de.lemke.sudoku.data.FakeDailyNotifications.AlarmTime
import de.lemke.sudoku.data.FakeSudokuDocuments
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.data.database.SudokuExport
import de.lemke.sudoku.data.database.sudokuToExport
import de.lemke.sudoku.data.database.sudokuWithFieldsToDb
import de.lemke.sudoku.domain.DeleteInvalidSudokusUseCase
import de.lemke.sudoku.domain.ExportDataUseCase
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.ImportDataUseCase
import de.lemke.sudoku.domain.IsNotificationPermissionGrantedUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.SetDailyNotificationEnabledUseCase
import de.lemke.sudoku.domain.model.DataExportResult
import de.lemke.sudoku.domain.model.DataImportResult
import de.lemke.sudoku.domain.model.ExportProgress
import de.lemke.sudoku.domain.model.ImportProgress
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.testSudoku
import io.kjson.stringifyJSON
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SettingsViewModelTest {
    private val userSettings = UserSettings(freshTestPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
    private val dailyNotifications = FakeDailyNotifications()
    private val sudokusRepository = FakeSudokusRepository()
    private val sudokuDocuments = FakeSudokuDocuments()
    private val exportUri = Uri.parse("content://files/export.json")
    private val importUri = Uri.parse("content://files/import.json")
    private val viewModel by lazy { newViewModel() }

    private fun newViewModel(dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()): SettingsViewModel {
        val sendDailyNotification = SendDailyNotificationUseCase(dailyNotifications, userSettings)
        return SettingsViewModel(
            userSettings,
            dailyNotifications,
            SetDailyNotificationEnabledUseCase(userSettings, sendDailyNotification),
            IsNotificationPermissionGrantedUseCase(dailyNotifications),
            DeleteInvalidSudokusUseCase(sudokusRepository, dispatcher),
            ExportDataUseCase(sudokuDocuments, GetAllSudokusUseCase(sudokusRepository, dispatcher), dispatcher),
            ImportDataUseCase(sudokuDocuments, sudokusRepository, dispatcher),
        )
    }

    private fun storeInvalidSudoku(): SudokuId =
        testSudoku().also { sudokusRepository.insertRows(sudokuWithFieldsToDb(it).copy(fields = emptyList())) }.id

    private fun addImportFile(vararg exports: SudokuExport) = sudokuDocuments.addDocument(importUri, exports.toList().stringifyJSON())

    private fun idleFor(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test
    fun `dailySudokuNotificationHour reads through to userSettings`() {
        userSettings.dailySudokuNotificationHour = 7
        viewModel.dailySudokuNotificationHour shouldBe 7
    }

    @Test
    fun `dailySudokuNotificationMinute reads through to userSettings`() {
        userSettings.dailySudokuNotificationMinute = 45
        viewModel.dailySudokuNotificationMinute shouldBe 45
    }

    @Test
    fun `isDailyNotificationChecked is false and does not consult system state when dailySudokuNotificationEnabled is false`() {
        userSettings.dailySudokuNotificationEnabled = false
        viewModel.isDailyNotificationChecked.shouldBeFalse()
    }

    @Test
    fun `isDailyNotificationChecked is false when system notifications are disabled`() {
        userSettings.dailySudokuNotificationEnabled = true
        dailyNotifications.blocked = true
        viewModel.isDailyNotificationChecked.shouldBeFalse()
    }

    @Test
    fun `isDailyNotificationChecked is false when the notification channel's importance is IMPORTANCE_NONE`() {
        userSettings.dailySudokuNotificationEnabled = true
        dailyNotifications.blocked = true
        viewModel.isDailyNotificationChecked.shouldBeFalse()
    }

    @Test
    fun `isDailyNotificationChecked is true when enabled, notifications are on, and the channel is not silenced`() {
        userSettings.dailySudokuNotificationEnabled = true
        viewModel.isDailyNotificationChecked.shouldBeTrue()
    }

    @Test
    fun `returns Applied and disables the notification when enabled is false`() {
        dailyNotifications.schedule(9, 0)
        val result = viewModel.onDailyNotificationToggleRequested(false)
        result shouldBe DailyNotificationToggleResult.Applied
        userSettings.dailySudokuNotificationEnabled.shouldBeFalse()
        dailyNotifications.scheduled.shouldBeNull()
    }

    @Test
    fun `returns NeedsPermission when enabled is true and permission is not granted`() {
        userSettings.dailySudokuNotificationEnabled = false
        dailyNotifications.permissionGranted = false
        val result = viewModel.onDailyNotificationToggleRequested(true)
        result shouldBe DailyNotificationToggleResult.NeedsPermission
        userSettings.dailySudokuNotificationEnabled.shouldBeFalse()
        dailyNotifications.scheduled.shouldBeNull()
    }

    @Test
    fun `returns SystemNotificationsDisabled when permission is granted but system notifications are off`() {
        userSettings.dailySudokuNotificationEnabled = false
        dailyNotifications.blocked = true
        val result = viewModel.onDailyNotificationToggleRequested(true)
        result shouldBe DailyNotificationToggleResult.SystemNotificationsDisabled
        userSettings.dailySudokuNotificationEnabled.shouldBeFalse()
        dailyNotifications.scheduled.shouldBeNull()
    }

    @Test
    fun `returns Applied and enables the notification when permission and system notifications allow it`() {
        userSettings.dailySudokuNotificationEnabled = false
        val result = viewModel.onDailyNotificationToggleRequested(true)
        result shouldBe DailyNotificationToggleResult.Applied
        userSettings.dailySudokuNotificationEnabled.shouldBeTrue()
        dailyNotifications.scheduled shouldBe AlarmTime(hour = 9, minute = 0)
    }

    @Test
    fun `onNotificationPermissionResult enables the notification when granted`() {
        userSettings.dailySudokuNotificationEnabled = false
        viewModel.onNotificationPermissionResult(true)
        userSettings.dailySudokuNotificationEnabled.shouldBeTrue()
        dailyNotifications.scheduled shouldBe AlarmTime(hour = 9, minute = 0)
    }

    @Test
    fun `onNotificationPermissionResult disables the notification when not granted`() {
        dailyNotifications.schedule(9, 0)
        viewModel.onNotificationPermissionResult(false)
        userSettings.dailySudokuNotificationEnabled.shouldBeFalse()
        dailyNotifications.scheduled.shouldBeNull()
    }

    @Test
    fun `onDailyNotificationTimeSelected writes hour and minute to userSettings and enables the notification`() {
        userSettings.dailySudokuNotificationEnabled = false
        viewModel.onDailyNotificationTimeSelected(14, 30)
        userSettings.dailySudokuNotificationHour shouldBe 14
        userSettings.dailySudokuNotificationMinute shouldBe 30
        userSettings.dailySudokuNotificationEnabled.shouldBeTrue()
        dailyNotifications.scheduled shouldBe AlarmTime(hour = 14, minute = 30)
    }

    @Test
    fun `invalidSudokuDeletion starts Idle`() {
        newViewModel().invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Idle
    }

    @Test
    fun `invalidSudokuDeletion runs the deletion and stays Running until 500 ms have passed, then is Finished`() {
        val invalid = storeInvalidSudoku()
        val settingsViewModel = newViewModel()

        settingsViewModel.onDeleteInvalidSudokusConfirmed()

        sudokusRepository.deletedIds shouldBe listOf(invalid)
        settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Running
        idleFor(499)
        settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Running
        idleFor(1)
        settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Finished
    }

    @Test
    fun `invalidSudokuDeletion deletes once when confirmed a second time while Running`() {
        val invalid = storeInvalidSudoku()
        val settingsViewModel = newViewModel()

        settingsViewModel.onDeleteInvalidSudokusConfirmed()
        storeInvalidSudoku()
        settingsViewModel.onDeleteInvalidSudokusConfirmed()
        idleFor(500)

        sudokusRepository.deletedIds shouldBe listOf(invalid)
        settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Finished
    }

    @Test
    fun `invalidSudokuDeletion resets to Idle once the Finished deletion is handled`() {
        val settingsViewModel = newViewModel()
        settingsViewModel.onDeleteInvalidSudokusConfirmed()
        idleFor(500)

        settingsViewModel.onInvalidSudokuDeletionHandled(InvalidSudokuDeletion.Finished)

        settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Idle
    }

    @Test
    fun `invalidSudokuDeletion keeps a new deletion Running when a stale handled call arrives`() {
        val first = storeInvalidSudoku()
        val settingsViewModel = newViewModel()
        settingsViewModel.onDeleteInvalidSudokusConfirmed()
        idleFor(500)
        settingsViewModel.onInvalidSudokuDeletionHandled(InvalidSudokuDeletion.Finished)
        val second = storeInvalidSudoku()
        settingsViewModel.onDeleteInvalidSudokusConfirmed()

        settingsViewModel.onInvalidSudokuDeletionHandled(InvalidSudokuDeletion.Finished)

        settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Running
        sudokusRepository.deletedIds shouldBe listOf(first, second)
    }

    @Test
    fun `dataTransfer starts Idle`() {
        newViewModel().dataTransfer.value shouldBe DataTransfer.Idle
    }

    @Test
    fun `dataTransfer forwards each export progress step and ends Exported`() =
        runTest {
            sudokusRepository.store(testSudoku(), testSudoku())
            val settingsViewModel = newViewModel(StandardTestDispatcher(testScheduler))
            val transfers = mutableListOf<DataTransfer>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { settingsViewModel.dataTransfer.toList(transfers) }

            settingsViewModel.onExportDestinationPicked(exportUri)
            settingsViewModel.dataTransfer.value shouldBe DataTransfer.Exporting(ExportProgress.Reading)
            runCurrent()

            transfers shouldBe
                listOf(
                    DataTransfer.Idle,
                    DataTransfer.Exporting(ExportProgress.Reading),
                    DataTransfer.Exporting(ExportProgress.Converting(done = 0, total = 2)),
                    DataTransfer.Exporting(ExportProgress.Converting(done = 1, total = 2)),
                    DataTransfer.Exporting(ExportProgress.Converting(done = 2, total = 2)),
                    DataTransfer.Exporting(ExportProgress.Writing),
                    DataTransfer.Exported(DataExportResult.Written),
                )
            settingsViewModel.dataTransfer.value shouldBe DataTransfer.Exported(DataExportResult.Written)
        }

    @Test
    fun `dataTransfer ends Exported with WriteFailed when the use case cannot write the destination`() =
        runTest {
            sudokuDocuments.refuseWrites(exportUri)
            val settingsViewModel = newViewModel(StandardTestDispatcher(testScheduler))

            settingsViewModel.onExportDestinationPicked(exportUri)
            runCurrent()

            settingsViewModel.dataTransfer.value shouldBe DataTransfer.Exported(DataExportResult.WriteFailed)
        }

    @Test
    fun `dataTransfer forwards each import progress step and ends Imported with the use case's result`() =
        runTest {
            addImportFile(sudokuToExport(testSudoku()), sudokuToExport(testSudoku()).copy(size = 5), sudokuToExport(testSudoku()))
            val settingsViewModel = newViewModel(StandardTestDispatcher(testScheduler))
            val transfers = mutableListOf<DataTransfer>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { settingsViewModel.dataTransfer.toList(transfers) }

            settingsViewModel.onImportFilePicked(importUri)
            settingsViewModel.dataTransfer.value shouldBe DataTransfer.Importing(ImportProgress.Reading)
            runCurrent()

            transfers shouldBe
                listOf(
                    DataTransfer.Idle,
                    DataTransfer.Importing(ImportProgress.Reading),
                    DataTransfer.Importing(ImportProgress.Parsing(done = 0, total = 3)),
                    DataTransfer.Importing(ImportProgress.Parsing(done = 1, total = 3)),
                    DataTransfer.Importing(ImportProgress.Parsing(done = 2, total = 3)),
                    DataTransfer.Importing(ImportProgress.Parsing(done = 3, total = 3)),
                    DataTransfer.Importing(ImportProgress.Saving(done = 0, total = 2)),
                    DataTransfer.Importing(ImportProgress.Saving(done = 1, total = 2)),
                    DataTransfer.Importing(ImportProgress.Saving(done = 2, total = 2)),
                    DataTransfer.Imported(DataImportResult.Imported(skippedCount = 1)),
                )
            settingsViewModel.dataTransfer.value shouldBe DataTransfer.Imported(DataImportResult.Imported(skippedCount = 1))
        }

    @Test
    fun `dataTransfer ends Imported with InvalidFile when the use case rejects the file`() =
        runTest {
            val settingsViewModel = newViewModel(StandardTestDispatcher(testScheduler))

            settingsViewModel.onImportFilePicked(importUri)
            runCurrent()

            settingsViewModel.dataTransfer.value shouldBe DataTransfer.Imported(DataImportResult.InvalidFile)
        }

    @Test
    fun `dataTransfer refuses a second export or an import while an export runs`() =
        runTest {
            val reading = CompletableDeferred<Unit>()
            var exportReads = 0
            sudokusRepository.beforeGetAllSudokus = {
                exportReads++
                reading.await()
            }
            addImportFile(sudokuToExport(testSudoku()))
            val settingsViewModel = newViewModel(StandardTestDispatcher(testScheduler))

            settingsViewModel.onExportDestinationPicked(exportUri)
            runCurrent()
            settingsViewModel.onExportDestinationPicked(exportUri)
            settingsViewModel.onImportFilePicked(importUri)
            runCurrent()

            settingsViewModel.dataTransfer.value shouldBe DataTransfer.Exporting(ExportProgress.Reading)
            exportReads shouldBe 1
            sudokusRepository.saves.shouldBeEmpty()
            reading.complete(Unit)
        }

    @Test
    fun `dataTransfer refuses an export or a second import while an import runs`() =
        runTest {
            addImportFile(sudokuToExport(testSudoku()))
            val settingsViewModel = newViewModel(StandardTestDispatcher(testScheduler))

            settingsViewModel.onImportFilePicked(importUri)
            settingsViewModel.onImportFilePicked(importUri)
            settingsViewModel.onExportDestinationPicked(exportUri)

            settingsViewModel.dataTransfer.value shouldBe DataTransfer.Importing(ImportProgress.Reading)
            runCurrent()
            sudokusRepository.saves.size shouldBe 1
            sudokuDocuments.written.shouldBeEmpty()
        }

    @Test
    fun `dataTransfer resets to Idle once the result is handled`() =
        runTest {
            val settingsViewModel = newViewModel(StandardTestDispatcher(testScheduler))
            settingsViewModel.onExportDestinationPicked(exportUri)
            runCurrent()

            settingsViewModel.onDataTransferHandled(DataTransfer.Exported(DataExportResult.Written))

            settingsViewModel.dataTransfer.value shouldBe DataTransfer.Idle
        }

    @Test
    fun `dataTransfer keeps the current result when a different result is reported handled`() =
        runTest {
            addImportFile(sudokuToExport(testSudoku()))
            val settingsViewModel = newViewModel(StandardTestDispatcher(testScheduler))
            settingsViewModel.onImportFilePicked(importUri)
            runCurrent()

            settingsViewModel.onDataTransferHandled(DataTransfer.Exported(DataExportResult.Written))

            settingsViewModel.dataTransfer.value shouldBe DataTransfer.Imported(DataImportResult.Imported(skippedCount = 0))
        }

    @Test
    fun `dataTransfer keeps a new transfer running when a stale handled call arrives`() =
        runTest {
            addImportFile(sudokuToExport(testSudoku()))
            val settingsViewModel = newViewModel(StandardTestDispatcher(testScheduler))
            settingsViewModel.onExportDestinationPicked(exportUri)
            runCurrent()
            settingsViewModel.onDataTransferHandled(DataTransfer.Exported(DataExportResult.Written))
            settingsViewModel.onImportFilePicked(importUri)

            settingsViewModel.onDataTransferHandled(DataTransfer.Exported(DataExportResult.Written))

            settingsViewModel.dataTransfer.value shouldBe DataTransfer.Importing(ImportProgress.Reading)
        }

    @Test
    fun `dataTransfer starts a new transfer while an unhandled result is pending`() =
        runTest {
            sudokuDocuments.addDocument(importUri, "{}")
            val settingsViewModel = newViewModel(StandardTestDispatcher(testScheduler))
            settingsViewModel.onExportDestinationPicked(exportUri)
            runCurrent()

            settingsViewModel.onImportFilePicked(importUri)
            runCurrent()

            settingsViewModel.dataTransfer.value shouldBe DataTransfer.Imported(DataImportResult.InvalidJson)
        }
}
