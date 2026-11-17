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

package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.FollowUp
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import javax.inject.Inject

/** What follows a won sudoku: the follow-up on offer, the sudoku it starts and the Play Games sync of the wins. */
class WinAftermath @Inject constructor(
    private val getMaxSudokuLevel: GetMaxSudokuLevelUseCase,
    private val generateSudoku: GenerateSudokuUseCase,
    private val generateSudokuLevel: GenerateSudokuLevelUseCase,
    private val calculatePlayGamesSync: CalculatePlayGamesSyncUseCase,
) {
    /** The follow-up that [completed] offers, or null if it offers none. */
    suspend fun followUpOf(completed: Sudoku): FollowUp? =
        when (val mode = completed.info.mode) {
            is GameMode.Level -> FollowUp.NEXT_LEVEL.takeIf { getMaxSudokuLevel(completed.info.size) == mode.number }
            GameMode.Normal -> FollowUp.NEW_GAME
            is GameMode.Daily -> null
        }

    /** The unsaved sudoku that [followUp] starts after [completed]. */
    suspend fun start(
        completed: Sudoku,
        followUp: FollowUp,
    ): Sudoku =
        when (followUp) {
            FollowUp.NEW_GAME -> generateSudoku(completed.info.size, completed.info.difficulty)
            FollowUp.NEXT_LEVEL -> generateSudokuLevel(completed.info.size, (completed.info.mode as GameMode.Level).number + 1)
        }

    suspend fun playGamesSync(): PlayGamesSync = calculatePlayGamesSync()
}
