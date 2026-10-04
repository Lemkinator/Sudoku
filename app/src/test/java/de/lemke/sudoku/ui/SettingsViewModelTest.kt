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

import android.app.NotificationChannel
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_NONE
import de.lemke.commonutils.data.FakeSharedPreferences
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
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * [SettingsViewModel.systemNotificationsEnabled] branches on `SDK_INT >= TIRAMISU` to decide whether to also
 * consult [androidx.core.content.ContextCompat.checkSelfPermission]. On a plain JVM unit test (no Robolectric,
 * per this task's pure-JVM Kotest style) `Build.VERSION.SDK_INT` resolves to the android.jar stub's default of
 * `0`, so that branch is permanently unreachable here and always falls through to `else -> true` once the first
 * two checks pass — matching sibling repos' precedent (e.g. GetApplicationInfoUseCaseTest's comment on needing
 * Robolectric's `@Config(sdk = ...)` to drive SDK_INT). Exercising the `checkSelfPermission` branch is out of
 * scope for this pure-JVM ViewModel test; see the task report.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest : ShouldSpec(
    {
        val context = mockk<Context>(relaxed = true)
        val notificationManager = mockk<NotificationManagerCompat>()
        lateinit var userSettings: UserSettings
        val setDailyNotificationEnabled = mockk<SetDailyNotificationEnabledUseCase>(relaxUnitFun = true)
        val isNotificationPermissionGranted = mockk<IsNotificationPermissionGrantedUseCase>()
        val deleteInvalidSudokus = mockk<DeleteInvalidSudokusUseCase>()
        val exportData = mockk<ExportDataUseCase>()
        val importData = mockk<ImportDataUseCase>()
        val exportUri = mockk<Uri>()
        val importUri = mockk<Uri>()
        lateinit var viewModel: SettingsViewModel

        fun newViewModel() =
            SettingsViewModel(
                context,
                userSettings,
                setDailyNotificationEnabled,
                isNotificationPermissionGranted,
                deleteInvalidSudokus,
                exportData,
                importData,
            )

        fun exportSteps(): Channel<ExportProgress> =
            Channel<ExportProgress>().also { steps ->
                coEvery { exportData(exportUri, any()) } coAnswers {
                    val onProgress = secondArg<(ExportProgress) -> Unit>()
                    for (step in steps) onProgress(step)
                }
            }

        fun importSteps(result: DataImportResult): Channel<ImportProgress> =
            Channel<ImportProgress>().also { steps ->
                coEvery { importData(importUri, any()) } coAnswers {
                    val onProgress = secondArg<(ImportProgress) -> Unit>()
                    for (step in steps) onProgress(step)
                    result
                }
            }

        beforeEach {
            clearMocks(
                context,
                notificationManager,
                setDailyNotificationEnabled,
                isNotificationPermissionGranted,
                deleteInvalidSudokus,
                exportData,
                importData,
            )
            mockkStatic(NotificationManagerCompat::class)
            every { context.getString(R.string.daily_sudoku_notification_channel_id) } returns "channelId"
            every { NotificationManagerCompat.from(context) } returns notificationManager
            every { notificationManager.areNotificationsEnabled() } returns true
            every { notificationManager.getNotificationChannel("channelId") } returns null
            every { isNotificationPermissionGranted() } returns true
            coEvery { deleteInvalidSudokus() } returns Unit
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            viewModel = newViewModel()
        }

        afterEach { unmockkAll() }

        should("dailySudokuNotificationHour reads through to userSettings") {
            userSettings.dailySudokuNotificationHour = 7
            viewModel.dailySudokuNotificationHour shouldBe 7
        }

        should("dailySudokuNotificationMinute reads through to userSettings") {
            userSettings.dailySudokuNotificationMinute = 45
            viewModel.dailySudokuNotificationMinute shouldBe 45
        }

        context("isDailyNotificationChecked") {
            should("is false and does not consult system state when dailySudokuNotificationEnabled is false") {
                userSettings.dailySudokuNotificationEnabled = false
                viewModel.isDailyNotificationChecked.shouldBeFalse()
                verify(exactly = 0) { notificationManager.areNotificationsEnabled() }
            }

            should("is false when system notifications are disabled") {
                userSettings.dailySudokuNotificationEnabled = true
                every { notificationManager.areNotificationsEnabled() } returns false
                viewModel.isDailyNotificationChecked.shouldBeFalse()
            }

            should("is false when the notification channel's importance is IMPORTANCE_NONE") {
                userSettings.dailySudokuNotificationEnabled = true
                val channel = mockk<NotificationChannel>()
                every { channel.importance } returns IMPORTANCE_NONE
                every { notificationManager.getNotificationChannel("channelId") } returns channel
                viewModel.isDailyNotificationChecked.shouldBeFalse()
            }

            should("is true when enabled, notifications are on, and the channel is not silenced") {
                userSettings.dailySudokuNotificationEnabled = true
                viewModel.isDailyNotificationChecked.shouldBeTrue()
            }
        }

        context("onDailyNotificationToggleRequested") {
            should("returns Applied and disables the notification when enabled is false") {
                val result = viewModel.onDailyNotificationToggleRequested(false)
                result shouldBe DailyNotificationToggleResult.Applied
                verify(exactly = 1) { setDailyNotificationEnabled(false) }
            }

            should("returns NeedsPermission when enabled is true and permission is not granted") {
                every { isNotificationPermissionGranted() } returns false
                val result = viewModel.onDailyNotificationToggleRequested(true)
                result shouldBe DailyNotificationToggleResult.NeedsPermission
                verify(exactly = 0) { setDailyNotificationEnabled(any()) }
            }

            should("returns SystemNotificationsDisabled when permission is granted but system notifications are off") {
                every { notificationManager.areNotificationsEnabled() } returns false
                val result = viewModel.onDailyNotificationToggleRequested(true)
                result shouldBe DailyNotificationToggleResult.SystemNotificationsDisabled
                verify(exactly = 0) { setDailyNotificationEnabled(any()) }
            }

            should("returns Applied and enables the notification when permission and system notifications allow it") {
                val result = viewModel.onDailyNotificationToggleRequested(true)
                result shouldBe DailyNotificationToggleResult.Applied
                verify(exactly = 1) { setDailyNotificationEnabled(true) }
            }
        }

        should("onNotificationPermissionResult enables the notification when granted") {
            viewModel.onNotificationPermissionResult(true)
            verify(exactly = 1) { setDailyNotificationEnabled(true) }
        }

        should("onNotificationPermissionResult disables the notification when not granted") {
            viewModel.onNotificationPermissionResult(false)
            verify(exactly = 1) { setDailyNotificationEnabled(false) }
        }

        should("onDailyNotificationTimeSelected writes hour and minute to userSettings and enables the notification") {
            viewModel.onDailyNotificationTimeSelected(14, 30)
            userSettings.dailySudokuNotificationHour shouldBe 14
            userSettings.dailySudokuNotificationMinute shouldBe 30
            verify(exactly = 1) { setDailyNotificationEnabled(true) }
        }

        context("invalidSudokuDeletion") {
            should("starts Idle") {
                newViewModel().invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Idle
            }

            should("runs the deletion and stays Running until 500 ms have passed, then is Finished") {
                runTest {
                    val settingsViewModel = newViewModel()

                    settingsViewModel.onDeleteInvalidSudokusConfirmed()
                    runCurrent()

                    coVerify(exactly = 1) { deleteInvalidSudokus() }
                    settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Running
                    advanceTimeBy(499)
                    runCurrent()
                    settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Running
                    advanceTimeBy(1)
                    runCurrent()
                    settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Finished
                }
            }

            should("deletes once when confirmed a second time while Running") {
                runTest {
                    val settingsViewModel = newViewModel()

                    settingsViewModel.onDeleteInvalidSudokusConfirmed()
                    settingsViewModel.onDeleteInvalidSudokusConfirmed()
                    advanceTimeBy(500)
                    runCurrent()

                    coVerify(exactly = 1) { deleteInvalidSudokus() }
                    settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Finished
                }
            }

            should("resets to Idle once the Finished deletion is handled") {
                runTest {
                    val settingsViewModel = newViewModel()
                    settingsViewModel.onDeleteInvalidSudokusConfirmed()
                    advanceTimeBy(500)
                    runCurrent()

                    settingsViewModel.onInvalidSudokuDeletionHandled(InvalidSudokuDeletion.Finished)

                    settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Idle
                }
            }

            should("keeps a new deletion Running when a stale handled call arrives") {
                runTest {
                    val settingsViewModel = newViewModel()
                    settingsViewModel.onDeleteInvalidSudokusConfirmed()
                    advanceTimeBy(500)
                    runCurrent()
                    settingsViewModel.onInvalidSudokuDeletionHandled(InvalidSudokuDeletion.Finished)
                    settingsViewModel.onDeleteInvalidSudokusConfirmed()
                    runCurrent()

                    settingsViewModel.onInvalidSudokuDeletionHandled(InvalidSudokuDeletion.Finished)

                    settingsViewModel.invalidSudokuDeletion.value shouldBe InvalidSudokuDeletion.Running
                    coVerify(exactly = 2) { deleteInvalidSudokus() }
                }
            }
        }

        context("dataTransfer") {
            should("starts Idle") {
                newViewModel().dataTransfer.value shouldBe DataTransfer.Idle
            }

            should("forwards each export progress step and ends Exported") {
                runTest {
                    val steps = exportSteps()
                    val settingsViewModel = newViewModel()

                    settingsViewModel.onExportDestinationPicked(exportUri)
                    runCurrent()
                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Exporting(ExportProgress.Reading)
                    steps.send(ExportProgress.Converting(done = 1, total = 2))
                    runCurrent()
                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Exporting(ExportProgress.Converting(done = 1, total = 2))
                    steps.send(ExportProgress.Writing)
                    runCurrent()
                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Exporting(ExportProgress.Writing)
                    steps.close()
                    runCurrent()

                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Exported
                }
            }

            should("forwards each import progress step and ends Imported with the use case's result") {
                runTest {
                    val steps = importSteps(DataImportResult.Imported(skippedCount = 1))
                    val settingsViewModel = newViewModel()

                    settingsViewModel.onImportFilePicked(importUri)
                    runCurrent()
                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Importing(ImportProgress.Reading)
                    steps.send(ImportProgress.Parsing(done = 2, total = 3))
                    runCurrent()
                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Importing(ImportProgress.Parsing(done = 2, total = 3))
                    steps.send(ImportProgress.Saving(done = 1, total = 2))
                    runCurrent()
                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Importing(ImportProgress.Saving(done = 1, total = 2))
                    steps.close()
                    runCurrent()

                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Imported(DataImportResult.Imported(skippedCount = 1))
                }
            }

            should("ends Imported with InvalidFile when the use case rejects the file") {
                runTest {
                    importSteps(DataImportResult.InvalidFile).close()
                    val settingsViewModel = newViewModel()

                    settingsViewModel.onImportFilePicked(importUri)
                    runCurrent()

                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Imported(DataImportResult.InvalidFile)
                }
            }

            should("refuses a second export or an import while an export runs") {
                runTest {
                    val steps = exportSteps()
                    importSteps(DataImportResult.Imported(skippedCount = 0)).close()
                    val settingsViewModel = newViewModel()

                    settingsViewModel.onExportDestinationPicked(exportUri)
                    runCurrent()
                    settingsViewModel.onExportDestinationPicked(exportUri)
                    settingsViewModel.onImportFilePicked(importUri)
                    runCurrent()

                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Exporting(ExportProgress.Reading)
                    coVerify(exactly = 1) { exportData(exportUri, any()) }
                    coVerify(exactly = 0) { importData(any(), any()) }
                    steps.close()
                }
            }

            should("refuses an export or a second import while an import runs") {
                runTest {
                    exportSteps().close()
                    val steps = importSteps(DataImportResult.Imported(skippedCount = 0))
                    val settingsViewModel = newViewModel()

                    settingsViewModel.onImportFilePicked(importUri)
                    runCurrent()
                    settingsViewModel.onImportFilePicked(importUri)
                    settingsViewModel.onExportDestinationPicked(exportUri)
                    runCurrent()

                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Importing(ImportProgress.Reading)
                    coVerify(exactly = 1) { importData(importUri, any()) }
                    coVerify(exactly = 0) { exportData(any(), any()) }
                    steps.close()
                }
            }

            should("resets to Idle once the result is handled") {
                runTest {
                    exportSteps().close()
                    val settingsViewModel = newViewModel()
                    settingsViewModel.onExportDestinationPicked(exportUri)
                    runCurrent()

                    settingsViewModel.onDataTransferHandled(DataTransfer.Exported)

                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Idle
                }
            }

            should("keeps the current result when a different result is reported handled") {
                runTest {
                    importSteps(DataImportResult.Imported(skippedCount = 0)).close()
                    val settingsViewModel = newViewModel()
                    settingsViewModel.onImportFilePicked(importUri)
                    runCurrent()

                    settingsViewModel.onDataTransferHandled(DataTransfer.Exported)

                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Imported(DataImportResult.Imported(skippedCount = 0))
                }
            }

            should("keeps a new transfer running when a stale handled call arrives") {
                runTest {
                    exportSteps().close()
                    val steps = importSteps(DataImportResult.Imported(skippedCount = 0))
                    val settingsViewModel = newViewModel()
                    settingsViewModel.onExportDestinationPicked(exportUri)
                    runCurrent()
                    settingsViewModel.onDataTransferHandled(DataTransfer.Exported)
                    settingsViewModel.onImportFilePicked(importUri)
                    runCurrent()

                    settingsViewModel.onDataTransferHandled(DataTransfer.Exported)

                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Importing(ImportProgress.Reading)
                    steps.close()
                }
            }

            should("starts a new transfer while an unhandled result is pending") {
                runTest {
                    exportSteps().close()
                    importSteps(DataImportResult.InvalidJson).close()
                    val settingsViewModel = newViewModel()
                    settingsViewModel.onExportDestinationPicked(exportUri)
                    runCurrent()

                    settingsViewModel.onImportFilePicked(importUri)
                    runCurrent()

                    settingsViewModel.dataTransfer.value shouldBe DataTransfer.Imported(DataImportResult.InvalidJson)
                }
            }
        }
    },
)
