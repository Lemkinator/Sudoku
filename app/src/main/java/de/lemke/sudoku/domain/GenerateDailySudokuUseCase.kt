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

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerateDailySudokuUseCase @Inject constructor(
    private val generateFields: GenerateFieldsUseCase,
) {
    suspend operator fun invoke(size: Int = 9): Sudoku =
        withContext(Dispatchers.Default) {
            val randomDifficulty =
                listOf(
                    Difficulty.VERY_EASY,
                    Difficulty.VERY_EASY,
                    Difficulty.VERY_EASY,
                    Difficulty.EASY,
                    Difficulty.EASY,
                    Difficulty.EASY,
                    Difficulty.EASY,
                    Difficulty.MEDIUM,
                    Difficulty.MEDIUM,
                    Difficulty.HARD,
                ).random()
            return@withContext Sudoku.create(
                sudokuId = SudokuId.generate(),
                size = size,
                difficulty = randomDifficulty,
                fields = generateFields(size, randomDifficulty),
                modeLevel = Sudoku.MODE_DAILY,
            )
        }
}
