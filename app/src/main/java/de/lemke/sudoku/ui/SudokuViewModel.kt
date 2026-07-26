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

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.GetSudokuUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.ShareSudokuUseCase
import de.lemke.sudoku.domain.UpdatePlayGamesUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import javax.inject.Inject

@HiltViewModel
class SudokuViewModel @Inject constructor(
    private val getSudoku: GetSudokuUseCase,
    private val generateSudoku: GenerateSudokuUseCase,
    private val generateSudokuLevel: GenerateSudokuLevelUseCase,
    private val getMaxSudokuLevel: GetMaxSudokuLevelUseCase,
    private val saveSudoku: SaveSudokuUseCase,
    private val shareSudoku: ShareSudokuUseCase,
    private val updatePlayGames: UpdatePlayGamesUseCase,
) : ViewModel() {
    suspend fun loadSudoku(id: SudokuId): Sudoku? = getSudoku(id)

    suspend fun generateNewSudoku(
        size: Int,
        difficulty: Difficulty,
    ): Sudoku = generateSudoku(size, difficulty)

    suspend fun generateNextLevelSudoku(
        size: Int,
        level: Int,
    ): Sudoku = generateSudokuLevel(size, level)

    suspend fun isMaxSudokuLevel(
        size: Int,
        level: Int,
    ): Boolean = getMaxSudokuLevel(size) == level

    suspend fun saveSudokuProgress(
        sudoku: Sudoku,
        onlyUpdate: Boolean = false,
    ) = saveSudoku(sudoku, onlyUpdate)

    suspend fun exportSudoku(sudoku: Sudoku): Uri = shareSudoku(sudoku)

    suspend fun syncPlayGames(
        activity: Activity,
        sudoku: Sudoku? = null,
    ) = updatePlayGames(activity, sudoku)
}
