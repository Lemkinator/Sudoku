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
import androidx.lifecycle.SavedStateHandle
import de.lemke.commonutils.freshTestPreferences
import de.lemke.sudoku.R
import de.lemke.sudoku.data.FakeDailyNotifications
import de.lemke.sudoku.data.FakeSudokuDocuments
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.data.database.sudokuToExport
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.ImportSudokuUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.testSudoku
import io.kjson.stringifyJSON
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class MainViewModelTest {
    private val userSettings = UserSettings(freshTestPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
    private val dailyNotifications = FakeDailyNotifications()
    private val sudokusRepository = FakeSudokusRepository()
    private val sudokuDocuments = FakeSudokuDocuments()
    private val dispatcher = UnconfinedTestDispatcher()
    private val uri = Uri.parse("content://files/opened.json")
    private var savedStateHandle = SavedStateHandle()
    private val viewModel = newViewModel()

    private val savedIds: List<String> get() = sudokusRepository.saves.map { it.rows.sudoku.id }

    private fun newViewModel(): MainViewModel {
        val getAllSudokus = GetAllSudokusUseCase(sudokusRepository, dispatcher)
        return MainViewModel(
            userSettings,
            ImportSudokuUseCase(sudokuDocuments, SaveSudokuUseCase(sudokusRepository, dispatcher), dispatcher),
            SendDailyNotificationUseCase(dailyNotifications, userSettings),
            CalculatePlayGamesSyncUseCase(getAllSudokus, dispatcher),
            savedStateHandle,
        )
    }

    private fun addImportFile() =
        sudokuDocuments.addDocument(uri, sudokuToExport(testSudoku(sudokuId = SudokuId("imported"))).stringifyJSON())

    private fun gateSaves(gate: CompletableDeferred<Unit>) {
        sudokusRepository.beforeSave = { gate.await() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `importedSudoku starts idle`() {
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Idle
    }

    @Test
    fun `onFileOpened exposes Importing until the import finishes`() {
        addImportFile()
        val imported = CompletableDeferred<Unit>()
        gateSaves(imported)
        viewModel.onFileOpened(uri)
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Importing
        imported.complete(Unit)
        idle()
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Imported(SudokuId("imported"))
    }

    @Test
    fun `onFileOpened exposes Failed when the import returns no sudoku`() {
        viewModel.onFileOpened(uri)
        idle()
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Failed
    }

    @Test
    fun `onImportedSudokuHandled returns to Idle for the current result`() {
        addImportFile()
        viewModel.onFileOpened(uri)
        idle()
        viewModel.onImportedSudokuHandled(ImportedSudoku.Imported(SudokuId("imported")))
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Idle
    }

    @Test
    fun `onImportedSudokuHandled keeps a newer result`() {
        addImportFile()
        viewModel.onFileOpened(uri)
        idle()
        viewModel.onImportedSudokuHandled(ImportedSudoku.Failed)
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Imported(SudokuId("imported"))
    }

    @Test
    fun `onFileOpened imports a file only once when the activity is recreated`() {
        addImportFile()
        viewModel.onFileOpened(uri)
        idle()
        viewModel.onImportedSudokuHandled(ImportedSudoku.Imported(SudokuId("imported")))
        viewModel.onFileOpened(uri)
        idle()
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Idle
        savedIds shouldBe listOf("imported")
    }

    @Test
    fun `onFileOpened does not import the file again after process death restores the saved state`() {
        addImportFile()
        viewModel.onFileOpened(uri)
        idle()
        savedStateHandle = SavedStateHandle(mapOf("openedFile" to savedStateHandle.get<String>("openedFile")))
        val restored = newViewModel()
        restored.onFileOpened(uri)
        idle()
        restored.importedSudoku.value shouldBe ImportedSudoku.Idle
        savedIds shouldBe listOf("imported")
    }

    @Test
    fun `onFileOpened imports a file only once when the activity is recreated during the import`() {
        addImportFile()
        val imported = CompletableDeferred<Unit>()
        gateSaves(imported)
        viewModel.onFileOpened(uri)
        viewModel.onFileOpened(uri)
        imported.complete(Unit)
        idle()
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Imported(SudokuId("imported"))
        savedIds shouldBe listOf("imported")
    }

    @Test
    fun `onFileOpened imports the file again after process death during the import`() {
        addImportFile()
        gateSaves(CompletableDeferred())
        viewModel.onFileOpened(uri)
        savedStateHandle = SavedStateHandle(savedStateHandle.keys().associateWith { savedStateHandle.get<Any>(it) })
        sudokusRepository.beforeSave = {}
        val restored = newViewModel()
        restored.onFileOpened(uri)
        idle()
        restored.importedSudoku.value shouldBe ImportedSudoku.Imported(SudokuId("imported"))
        savedIds shouldBe listOf("imported")
    }

    @Test
    fun `onFileOpened imports a different file opened later`() {
        addImportFile()
        val otherUri = Uri.parse("content://files/other.json")
        viewModel.onFileOpened(uri)
        idle()
        viewModel.onFileOpened(otherUri)
        idle()
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Failed
        savedIds shouldBe listOf("imported")
    }

    @Test
    fun `onScreenReady enables the daily notification when userSettings dailySudokuNotificationEnabled is true`() =
        runTest {
            userSettings.dailySudokuNotificationEnabled = true
            viewModel.onScreenReady()
            dailyNotifications.scheduled shouldBe FakeDailyNotifications.AlarmTime(hour = 9, minute = 0)
        }

    @Test
    fun `onScreenReady disables the daily notification when userSettings dailySudokuNotificationEnabled is false`() =
        runTest {
            dailyNotifications.schedule(9, 0)
            userSettings.dailySudokuNotificationEnabled = false
            viewModel.onScreenReady()
            dailyNotifications.scheduled.shouldBeNull()
        }

    @Test
    fun `onScreenReady returns calculatePlayGamesSync's result`() =
        runTest {
            sudokusRepository.store(
                testSudoku(completed = true),
                testSudoku(modeLevel = 1, completed = true),
                testSudoku(modeLevel = Sudoku.MODE_DAILY, completed = true),
                testSudoku(),
            )
            viewModel.onScreenReady() shouldBe
                PlayGamesSync(
                    leaderboardScores =
                        listOf(
                            R.string.leaderboard_total_wins to 3L,
                            R.string.leaderboard_daily_sudokus to 1L,
                            R.string.leaderboard_level_44 to 1L,
                            R.string.leaderboard_level_99 to 0L,
                            R.string.leaderboard_level_1616 to 0L,
                        ),
                )
        }
}
