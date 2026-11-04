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

package de.lemke.sudoku.domain.model

import java.time.LocalDateTime

private val TUTORIAL_SIZE = SudokuSize.NINE

// Row-major, one digit per cell.
private const val TUTORIAL_SOLUTION =
    "314259687" + "825467193" + "796813254" + "152384976" + "963571842" + "478926531" + "287635419" + "641798325" + "539142768"

private const val TUTORIAL_GIVEN_MASK =
    "111101111" + "111111111" + "111001011" + "101101100" + "111101011" + "110101111" + "010001001" + "001001101" + "001111111"

/**
 * A fixed, hand-authored 9×9 "very easy" puzzle used as [IntroActivity][de.lemke.sudoku.ui.IntroActivity]'s
 * onboarding preview board. Also reused by test fixtures ([de.lemke.sudoku.testLevelSudoku]) needing a real,
 * pre-verified board to render, rather than a second hardcoded copy.
 */
fun tutorialSudoku(
    sudokuId: SudokuId = SudokuId.generate(),
    mode: GameMode = GameMode.Normal,
): Sudoku {
    val now = LocalDateTime.now()
    val digits = TUTORIAL_SOLUTION.map { it - '0' }
    val givens = TUTORIAL_GIVEN_MASK.indices.filter { TUTORIAL_GIVEN_MASK[it] == '1' }.toSet()
    return Sudoku(
        info = GameInfo(id = sudokuId, size = TUTORIAL_SIZE, difficulty = Difficulty.VERY_EASY, mode = mode, created = now, updated = now),
        board = Board.of(TUTORIAL_SIZE, digits, givens),
    )
}
