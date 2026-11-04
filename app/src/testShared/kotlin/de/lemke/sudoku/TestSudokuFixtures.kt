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

import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.domain.model.tutorialSudoku
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** A readable sudoku over a valid solved pattern; every third cell is given, the rest stays empty unless [completed]. */
fun testSudoku(
    size: SudokuSize = SudokuSize.FOUR,
    mode: GameMode = GameMode.Normal,
    difficulty: Difficulty = Difficulty.EASY,
    sudokuId: SudokuId = SudokuId.generate(),
    created: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
    updated: LocalDateTime = created,
    completed: Boolean = false,
): Sudoku {
    val blockSize = size.blockSize
    val solution =
        List(size.cellCount) { index ->
            val row = index / size.value
            val column = index % size.value
            (blockSize * (row % blockSize) + row / blockSize + column) % size.value + 1
        }
    val board = Board.of(size, solution, givens = solution.indices.filter { it % 3 == 0 }.toSet())
    return Sudoku(
        info = GameInfo(id = sudokuId, size = size, difficulty = difficulty, mode = mode, created = created, updated = updated),
        board =
            if (completed) {
                Board(size, board.cells.map { it.copy(value = it.solution) })
            } else {
                board
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
            tutorialSudoku(sudokuId = sudokuId, mode = GameMode.Level(level))
        }

        SudokuSize.FOUR, SudokuSize.SIXTEEN -> {
            testSudoku(
                size = size,
                mode = GameMode.Level(level),
                difficulty = Difficulty.VERY_EASY,
                sudokuId = sudokuId,
            )
        }
    }
