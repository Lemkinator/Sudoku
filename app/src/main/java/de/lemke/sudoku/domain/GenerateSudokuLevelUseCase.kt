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

import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class GenerateSudokuLevelUseCase @Inject constructor(
    private val generateFields: GenerateFieldsUseCase,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(
        size: SudokuSize,
        level: Int,
    ): Sudoku =
        withContext(defaultDispatcher) {
            val difficulty =
                when (level) {
                    in 1..30 -> Difficulty.VERY_EASY
                    in 31..100 -> Difficulty.EASY
                    in 101..200 -> Difficulty.MEDIUM
                    in 201..500 -> Difficulty.HARD
                    else -> Difficulty.EXPERT
                }
            val now = LocalDateTime.now()
            return@withContext Sudoku(
                info =
                    GameInfo(
                        id = SudokuId.generate(),
                        size = size,
                        difficulty = difficulty,
                        mode = GameMode.Level(level),
                        created = now,
                        updated = now,
                    ),
                board = generateFields(size, difficulty),
            )
        }
}
