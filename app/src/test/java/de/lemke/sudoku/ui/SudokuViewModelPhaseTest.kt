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
import androidx.lifecycle.SavedStateHandle
import de.lemke.commonutils.freshTestPreferences
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.AdjustableClock
import de.lemke.sudoku.data.FakeSudokuShareFiles
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.GenerateFieldsUseCase
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.GetSudokuUseCase
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.QueueSudokuSaveUseCase
import de.lemke.sudoku.domain.ShareSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
import de.lemke.sudoku.testSudoku
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import io.kotest.matchers.shouldBe
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SudokuViewModelPhaseTest {
    private val clock = AdjustableClock(Instant.parse("2026-01-15T12:00:00Z"))
    private val repository = FakeSudokusRepository()
    private val userSettings = UserSettings(freshTestPreferences(), CoroutineScope(UnconfinedTestDispatcher()))

    private fun playing(sudoku: Sudoku): SudokuViewModel {
        repository.store(sudoku)
        val dispatcher = Dispatchers.Main
        val generateFields = GenerateFieldsUseCase(dispatcher, PatternSolvedBoardGenerator())
        return SudokuViewModel(
            SavedStateHandle(mapOf(KEY_SUDOKU_ID to sudoku.id.value)),
            GetSudokuUseCase(repository, dispatcher),
            GenerateSudokuUseCase(generateFields, dispatcher),
            GenerateSudokuLevelUseCase(generateFields, dispatcher),
            GetMaxSudokuLevelUseCase(repository, dispatcher),
            QueueSudokuSaveUseCase(repository, CoroutineScope(SupervisorJob()), dispatcher),
            ShareSudokuUseCase(FakeSudokuShareFiles(File("game.sudoku")), dispatcher),
            CalculatePlayGamesSyncUseCase(GetAllSudokusUseCase(repository, dispatcher), dispatcher),
            userSettings,
            clock,
        ).also {
            idleMainLooper()
            it.onGameStarted(SudokuGame.Ready(sudoku))
        }
    }

    private fun daily(created: LocalDateTime) = testSudoku(modeLevel = MODE_DAILY, created = created)

    private val today = LocalDateTime.of(2026, 1, 15, 0, 0)

    @Test
    fun `a normal sudoku in progress is playing with the limit of the settings`() {
        userSettings.errorLimit = 5

        playing(testSudoku()).game.value.shouldBePlaying(SudokuKind.Normal, 5, Phase.Playing)
    }

    @Test
    fun `a normal sudoku at the limit of the settings is lost`() {
        userSettings.errorLimit = 2
        val sudoku = testSudoku().apply { errorsMade = 2 }

        playing(sudoku).game.value.shouldBePlaying(SudokuKind.Normal, 2, Phase.Lost)
    }

    @Test
    fun `a normal sudoku without an error limit is never lost`() {
        userSettings.errorLimit = 0
        val sudoku = testSudoku().apply { errorsMade = 99 }

        playing(sudoku).game.value.shouldBePlaying(SudokuKind.Normal, 0, Phase.Playing)
    }

    @Test
    fun `a completed sudoku is won`() {
        playing(testSudoku(completed = true)).game.value.shouldBePlaying(SudokuKind.Normal, 3, Phase.Won)
    }

    @Test
    fun `a level sudoku has its level and the level error limit`() {
        val sudoku = testSudoku(modeLevel = 4).apply { errorsMade = 3 }

        playing(sudoku).game.value.shouldBePlaying(SudokuKind.Level(4), 3, Phase.Lost)
    }

    @Test
    fun `a daily sudoku of the clock's date is playing with its date`() {
        playing(daily(today)).game.value.shouldBePlaying(SudokuKind.Daily(LocalDate.of(2026, 1, 15)), 3, Phase.Playing)
    }

    @Test
    fun `a daily sudoku of an earlier date is expired even after it reached its error limit`() {
        val sudoku = daily(today.minusDays(1)).apply { errorsMade = 3 }

        playing(sudoku).game.value.shouldBePlaying(SudokuKind.Daily(LocalDate.of(2026, 1, 14)), 3, Phase.Expired)
    }

    @Test
    fun `a completed daily sudoku of an earlier date is won`() {
        val sudoku = testSudoku(modeLevel = MODE_DAILY, created = today.minusDays(1), completed = true)

        playing(sudoku).game.value.shouldBePlaying(SudokuKind.Daily(LocalDate.of(2026, 1, 14)), 3, Phase.Won)
    }

    @Test
    fun `onErrorMade moves a sudoku that reached its limit to lost`() {
        val sudoku = testSudoku()
        val viewModel = playing(sudoku)
        sudoku.errorsMade = 3

        viewModel.onErrorMade()

        viewModel.game.value.shouldBePlaying(SudokuKind.Normal, 3, Phase.Lost)
    }

    @Test
    fun `today's daily sudoku stays playing with a running timer after the clock passes midnight`() {
        val sudoku = daily(today)
        val viewModel = playing(sudoku)
        clock.advanceTo(Instant.parse("2026-01-16T00:00:01Z"))

        viewModel.onResumed()
        sudoku.errorsMade = 1
        viewModel.onErrorMade()

        viewModel.game.value.shouldBePlaying(SudokuKind.Daily(LocalDate.of(2026, 1, 15)), 3, Phase.Playing)
        sudoku.resumed shouldBe true
        sudoku.stopTimer()
    }

    @Test
    fun `onCompleted moves a sudoku completed after the start to won`() {
        val sudoku = testSudoku()
        val viewModel = playing(sudoku)
        sudoku.fields.forEach { it.value = it.solution }

        viewModel.onCompleted()

        viewModel.game.value.shouldBePlaying(SudokuKind.Normal, 3, Phase.Won)
    }

    private fun SudokuGame.shouldBePlaying(
        kind: SudokuKind,
        errorLimit: Int,
        phase: Phase,
    ) {
        val playing = this as SudokuGame.Playing
        (playing.kind to playing.errorLimit to playing.phase) shouldBe (kind to errorLimit to phase)
    }
}
