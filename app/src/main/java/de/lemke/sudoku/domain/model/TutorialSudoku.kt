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

// Row-major, one digit per cell.
private const val TUTORIAL_SOLUTION =
    "314259687" + "825467193" + "796813254" + "152384976" + "963571842" + "478926531" + "287635419" + "641798325" + "539142768"

// Indices (into TUTORIAL_SOLUTION) left blank; every other cell is given.
private val TUTORIAL_BLANKS =
    setOf(4, 21, 22, 24, 28, 31, 34, 35, 40, 42, 47, 49, 54, 56, 57, 58, 60, 61, 63, 64, 66, 67, 70, 72, 73)

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
        size = 9,
        difficulty = Difficulty.VERY_EASY,
        modeLevel = modeLevel,
        fields =
            MutableList(81) { index ->
                val solution = TUTORIAL_SOLUTION[index] - '0'
                val given = index !in TUTORIAL_BLANKS
                Field(position = Position.create(index, 9), value = if (given) solution else null, solution = solution, given = given)
            },
    )
