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
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Builds view-models over a fake repository, the real settings and a clock a test advances. Needs Robolectric. */
@OptIn(ExperimentalCoroutinesApi::class)
class SudokuViewModelRig {
    val clock = AdjustableClock(Instant.parse("2026-01-15T12:00:00Z"))
    val repository = FakeSudokusRepository()
    val userSettings = UserSettings(freshTestPreferences(), CoroutineScope(UnconfinedTestDispatcher()))

    fun playing(sudoku: Sudoku): SudokuViewModel = ready(sudoku).also { it.onGameStarted(SudokuGame.Ready(sudoku)) }

    fun ready(sudoku: Sudoku): SudokuViewModel {
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
        ).also { idleMainLooper() }
    }
}
