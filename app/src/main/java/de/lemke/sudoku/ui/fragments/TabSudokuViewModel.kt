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

package de.lemke.sudoku.ui.fragments

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetRecentlyUpdatedNormalSudokuUseCase
import de.lemke.sudoku.domain.IsDailySudokuCompletedUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import javax.inject.Inject

@HiltViewModel
class TabSudokuViewModel @Inject constructor(
    private val userSettings: UserSettings,
    private val generateSudoku: GenerateSudokuUseCase,
    private val saveSudoku: SaveSudokuUseCase,
    private val getRecentSudoku: GetRecentlyUpdatedNormalSudokuUseCase,
    private val isDailySudokuCompleted: IsDailySudokuCompletedUseCase,
) : ViewModel() {
    var difficultySliderValue: Int
        get() = userSettings.difficultySliderValue
        set(value) {
            userSettings.difficultySliderValue = value
        }

    var sizeSliderValue: Int
        get() = userSettings.sizeSliderValue
        set(value) {
            userSettings.sizeSliderValue = value
        }

    suspend fun createNewSudoku(
        size: Int,
        difficulty: Difficulty,
    ): Sudoku {
        val sudoku = generateSudoku(size, difficulty)
        saveSudoku(sudoku)
        return sudoku
    }

    suspend fun getContinuableSudoku(): Sudoku? {
        val sudoku = getRecentSudoku() ?: return null
        return if (!sudoku.completed && !sudoku.errorLimitReached(userSettings.errorLimit)) sudoku else null
    }

    suspend fun checkDailySudokuCompleted(): Boolean = isDailySudokuCompleted()
}
