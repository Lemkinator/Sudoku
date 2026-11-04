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
import androidx.lifecycle.SavedStateHandle
import de.lemke.commonutils.freshTestPreferences
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.R
import de.lemke.sudoku.data.FakeDailyNotifications
import de.lemke.sudoku.data.FakeSudokuDocuments
import de.lemke.sudoku.data.PendingPlayGamesWins
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.codec.SudokuFileCodec
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.ClearPlayGamesWinsUseCase
import de.lemke.sudoku.domain.GetPendingPlayGamesSyncUseCase
import de.lemke.sudoku.domain.ImportSudokuUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.PendingPlayGamesSync
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.testSudoku
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class MainViewModelTest {
    private val userSettings = UserSettings(freshTestPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
    private val pendingWins = PendingPlayGamesWins(userSettings)
    private val dailyNotifications = FakeDailyNotifications()
    private val sudokusRepository = FakeSudokusRepository()
    private val sudokuDocuments = FakeSudokuDocuments()
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = Clock.systemDefaultZone()
    private val uri = Uri.parse("content://files/opened.sudoku")
    private var savedStateHandle = SavedStateHandle()
    private val viewModel = newViewModel()

    private val savedIds: List<String> get() = sudokusRepository.saves.map { it.info.id.value }

    private fun newViewModel(): MainViewModel =
        MainViewModel(
            userSettings,
            ImportSudokuUseCase(sudokuDocuments, SaveSudokuUseCase(sudokusRepository, dispatcher), clock, dispatcher),
            SendDailyNotificationUseCase(dailyNotifications, userSettings),
            CalculatePlayGamesSyncUseCase(sudokusRepository, dispatcher),
            GetPendingPlayGamesSyncUseCase(
                pendingWins,
                sudokusRepository,
                CalculatePlayGamesSyncUseCase(sudokusRepository, dispatcher),
                dispatcher,
            ),
            ClearPlayGamesWinsUseCase(pendingWins),
            savedStateHandle,
        )

    private fun addImportFile() =
        sudokuDocuments.addDocument(uri, SudokuFileCodec.encode(testSudoku(sudokuId = SudokuId("imported")), clock.zone))

    private fun gateSaves(gate: CompletableDeferred<Unit>) {
        sudokusRepository.beforeSave = { gate.await() }
    }

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
        idleMainLooper()
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Imported(SudokuId("imported"))
    }

    @Test
    fun `onFileOpened exposes Failed when the import returns no sudoku`() {
        viewModel.onFileOpened(uri)
        idleMainLooper()
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Failed
    }

    @Test
    fun `onImportedSudokuHandled returns to Idle for the current result`() {
        addImportFile()
        viewModel.onFileOpened(uri)
        idleMainLooper()
        viewModel.onImportedSudokuHandled(ImportedSudoku.Imported(SudokuId("imported")))
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Idle
    }

    @Test
    fun `onImportedSudokuHandled keeps a newer result`() {
        addImportFile()
        viewModel.onFileOpened(uri)
        idleMainLooper()
        viewModel.onImportedSudokuHandled(ImportedSudoku.Failed)
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Imported(SudokuId("imported"))
    }

    @Test
    fun `onFileOpened imports a file only once when the activity is recreated`() {
        addImportFile()
        viewModel.onFileOpened(uri)
        idleMainLooper()
        viewModel.onImportedSudokuHandled(ImportedSudoku.Imported(SudokuId("imported")))
        viewModel.onFileOpened(uri)
        idleMainLooper()
        viewModel.importedSudoku.value shouldBe ImportedSudoku.Idle
        savedIds shouldBe listOf("imported")
    }

    @Test
    fun `onFileOpened does not import the file again after process death restores the saved state`() {
        addImportFile()
        viewModel.onFileOpened(uri)
        idleMainLooper()
        savedStateHandle = SavedStateHandle(mapOf("openedFile" to savedStateHandle.get<String>("openedFile")))
        val restored = newViewModel()
        restored.onFileOpened(uri)
        idleMainLooper()
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
        idleMainLooper()
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
        idleMainLooper()
        restored.importedSudoku.value shouldBe ImportedSudoku.Imported(SudokuId("imported"))
        savedIds shouldBe listOf("imported")
    }

    @Test
    fun `onFileOpened imports a different file opened later`() {
        addImportFile()
        val otherUri = Uri.parse("content://files/other.sudoku")
        viewModel.onFileOpened(uri)
        idleMainLooper()
        viewModel.onFileOpened(otherUri)
        idleMainLooper()
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
                testSudoku(mode = GameMode.Level(1), completed = true),
                testSudoku(mode = GameMode.Daily(LocalDate.now()), completed = true),
                testSudoku(),
            )
            viewModel.onScreenReady() shouldBe
                PendingPlayGamesSync(
                    wins = emptySet(),
                    sync =
                        PlayGamesSync(
                            leaderboardScores =
                                listOf(
                                    R.string.leaderboard_total_wins to 3L,
                                    R.string.leaderboard_daily_sudokus to 1L,
                                    R.string.leaderboard_level_44 to 1L,
                                    R.string.leaderboard_level_99 to 0L,
                                    R.string.leaderboard_level_1616 to 0L,
                                ),
                        ),
                )
        }

    @Test
    fun `onScreenReady returns the sync of a queued win until it is applied`() =
        runTest {
            val won = testSudoku(sudokuId = SudokuId("won"), completed = true)
            sudokusRepository.store(won)
            pendingWins.add(won.info.id)

            val pending = viewModel.onScreenReady()

            pending.wins shouldBe setOf(SudokuId("won"))
            pending.sync.achievementUnlocks.first() shouldBe R.string.achievement_first_win
            viewModel.onScreenReady() shouldBe pending

            viewModel.onPlayGamesSyncApplied(pending)

            pendingWins.all() shouldBe emptySet()
            viewModel.onScreenReady().wins shouldBe emptySet()
        }
}
