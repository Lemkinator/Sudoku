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

package de.lemke.sudoku

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.domain.model.tutorialSudoku
import java.time.LocalDateTime

/** A readable sudoku over a valid solved pattern; every third field is given, the rest stays empty unless [completed]. */
fun testSudoku(
    size: SudokuSize = SudokuSize.FOUR,
    modeLevel: Int = Sudoku.MODE_NORMAL,
    difficulty: Difficulty = Difficulty.EASY,
    sudokuId: SudokuId = SudokuId.generate(),
    created: LocalDateTime = LocalDateTime.now(),
    updated: LocalDateTime = created,
    completed: Boolean = false,
): Sudoku {
    val blockSize = size.blockSize
    return Sudoku.create(
        sudokuId = sudokuId,
        size = size,
        difficulty = difficulty,
        modeLevel = modeLevel,
        created = created,
        updated = updated,
        fields =
            MutableList(size.cellCount) { index ->
                val position = Position.create(index, size)
                val row = position.row
                val col = position.column
                val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size.value + 1
                val given = index % 3 == 0
                Field(
                    position = position,
                    solution = solution,
                    value = if (given || completed) solution else null,
                    given = given,
                )
            },
    )
}

fun testLevelSudoku(
    size: SudokuSize,
    level: Int = 1,
    sudokuId: SudokuId = SudokuId.generate(),
): Sudoku =
    when (size) {
        SudokuSize.NINE -> {
            tutorialSudoku(sudokuId = sudokuId, modeLevel = level)
        }

        SudokuSize.FOUR, SudokuSize.SIXTEEN -> {
            testSudoku(
                size = size,
                modeLevel = level,
                difficulty = Difficulty.VERY_EASY,
                sudokuId = sudokuId,
            )
        }
    }
