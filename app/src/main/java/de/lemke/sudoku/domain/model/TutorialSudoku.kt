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

private const val TUTORIAL_SIZE = 9
private const val TUTORIAL_CELL_COUNT = TUTORIAL_SIZE * TUTORIAL_SIZE

// Row-major, one digit per cell.
private const val TUTORIAL_SOLUTION =
    "314259687" + "825467193" + "796813254" + "152384976" + "963571842" + "478926531" + "287635419" + "641798325" + "539142768"

// Same length/order as TUTORIAL_SOLUTION: '1' = given, '0' = left blank for the player to fill in.
private const val TUTORIAL_GIVEN_MASK =
    "111101111" + "111111111" + "111001011" + "101101100" + "111101011" + "110101111" + "010001001" + "001001101" + "001111111"

/**
 * A fixed, hand-authored 9×9 "very easy" puzzle used as [IntroActivity][de.lemke.sudoku.ui.IntroActivity]'s
 * onboarding preview board. Also reused by test fixtures ([de.lemke.sudoku.testLevelSudoku]) needing a real,
 * pre-verified board to render, rather than a second hardcoded copy.
 */
fun tutorialSudoku(
    sudokuId: SudokuId = SudokuId.generate(),
    modeLevel: Int = Sudoku.MODE_NORMAL,
): Sudoku =
    Sudoku.create(
        sudokuId = sudokuId,
        size = TUTORIAL_SIZE,
        difficulty = Difficulty.VERY_EASY,
        modeLevel = modeLevel,
        fields =
            MutableList(TUTORIAL_CELL_COUNT) { index ->
                val solution = TUTORIAL_SOLUTION[index] - '0'
                val given = TUTORIAL_GIVEN_MASK[index] == '1'
                Field(
                    position = Position.create(index, TUTORIAL_SIZE),
                    value = if (given) solution else null,
                    solution = solution,
                    given = given,
                )
            },
    )
