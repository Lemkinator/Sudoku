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

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.ImportSudokuUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest : ShouldSpec(
    {
        lateinit var userSettings: UserSettings
        val importSudoku = mockk<ImportSudokuUseCase>()
        val sendDailyNotification = mockk<SendDailyNotificationUseCase>(relaxUnitFun = true)
        val calculatePlayGamesSync = mockk<CalculatePlayGamesSyncUseCase>()
        val uri = mockk<Uri> { every { this@mockk.toString() } returns "content://files/opened.json" }
        lateinit var savedStateHandle: SavedStateHandle
        lateinit var viewModel: MainViewModel

        fun newViewModel() = MainViewModel(userSettings, importSudoku, sendDailyNotification, calculatePlayGamesSync, savedStateHandle)

        fun sudoku(): Sudoku {
            val size = SudokuSize.FOUR
            return Sudoku.create(
                sudokuId = SudokuId("imported"),
                size = size,
                difficulty = Difficulty.VERY_EASY,
                modeLevel = Sudoku.MODE_NORMAL,
                fields =
                    MutableList(size.cellCount) { i ->
                        val solution = (i % size.value) + 1
                        Field(position = Position.create(i, size), solution = solution, value = solution, given = true)
                    },
            )
        }

        beforeEach {
            clearMocks(importSudoku, sendDailyNotification, calculatePlayGamesSync)
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            savedStateHandle = SavedStateHandle()
            viewModel = newViewModel()
        }

        should("importedSudoku starts idle") {
            viewModel.importedSudoku.value shouldBe ImportedSudoku.Idle
        }

        should("onFileOpened exposes Importing until the import finishes") {
            val imported = CompletableDeferred<Sudoku?>()
            coEvery { importSudoku(uri) } coAnswers { imported.await() }
            viewModel.onFileOpened(uri)
            viewModel.importedSudoku.value shouldBe ImportedSudoku.Importing
            imported.complete(sudoku())
            viewModel.importedSudoku.value shouldBe ImportedSudoku.Imported(SudokuId("imported"))
        }

        should("onFileOpened exposes Failed when the import returns no sudoku") {
            coEvery { importSudoku(uri) } returns null
            viewModel.onFileOpened(uri)
            viewModel.importedSudoku.value shouldBe ImportedSudoku.Failed
        }

        should("onImportedSudokuHandled returns to Idle for the current result") {
            coEvery { importSudoku(uri) } returns sudoku()
            viewModel.onFileOpened(uri)
            viewModel.onImportedSudokuHandled(ImportedSudoku.Imported(SudokuId("imported")))
            viewModel.importedSudoku.value shouldBe ImportedSudoku.Idle
        }

        should("onImportedSudokuHandled keeps a newer result") {
            coEvery { importSudoku(uri) } returns sudoku()
            viewModel.onFileOpened(uri)
            viewModel.onImportedSudokuHandled(ImportedSudoku.Failed)
            viewModel.importedSudoku.value shouldBe ImportedSudoku.Imported(SudokuId("imported"))
        }

        should("onFileOpened imports a file only once when the activity is recreated") {
            coEvery { importSudoku(uri) } returns sudoku()
            viewModel.onFileOpened(uri)
            viewModel.onImportedSudokuHandled(ImportedSudoku.Imported(SudokuId("imported")))
            viewModel.onFileOpened(uri)
            viewModel.importedSudoku.value shouldBe ImportedSudoku.Idle
            coVerify(exactly = 1) { importSudoku(uri) }
        }

        should("onFileOpened does not import the file again after process death restores the saved state") {
            coEvery { importSudoku(uri) } returns sudoku()
            viewModel.onFileOpened(uri)
            savedStateHandle = SavedStateHandle(mapOf("openedFile" to savedStateHandle.get<String>("openedFile")))
            val restored = newViewModel()
            restored.onFileOpened(uri)
            restored.importedSudoku.value shouldBe ImportedSudoku.Idle
            coVerify(exactly = 1) { importSudoku(uri) }
        }

        should("onFileOpened imports a different file opened later") {
            val otherUri = mockk<Uri> { every { this@mockk.toString() } returns "content://files/other.json" }
            coEvery { importSudoku(uri) } returns sudoku()
            coEvery { importSudoku(otherUri) } returns null
            viewModel.onFileOpened(uri)
            viewModel.onFileOpened(otherUri)
            viewModel.importedSudoku.value shouldBe ImportedSudoku.Failed
            coVerify(exactly = 1) { importSudoku(otherUri) }
        }

        should("onScreenReady enables the daily notification when userSettings.dailySudokuNotificationEnabled is true") {
            userSettings.dailySudokuNotificationEnabled = true
            val sync = mockk<PlayGamesSync>()
            coEvery { calculatePlayGamesSync() } returns sync
            viewModel.onScreenReady()
            verify(exactly = 1) { sendDailyNotification.setDailySudokuNotification(enable = true) }
        }

        should("onScreenReady disables the daily notification when userSettings.dailySudokuNotificationEnabled is false") {
            userSettings.dailySudokuNotificationEnabled = false
            val sync = mockk<PlayGamesSync>()
            coEvery { calculatePlayGamesSync() } returns sync
            viewModel.onScreenReady()
            verify(exactly = 1) { sendDailyNotification.setDailySudokuNotification(enable = false) }
        }

        should("onScreenReady returns calculatePlayGamesSync's result") {
            val sync = mockk<PlayGamesSync>()
            coEvery { calculatePlayGamesSync() } returns sync
            viewModel.onScreenReady() shouldBe sync
        }
    },
)
