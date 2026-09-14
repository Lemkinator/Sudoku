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

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class SudokuTest : ShouldSpec(
    {
        should("report 100% progress instead of throwing when every field is given") {
            val sudoku =
                Sudoku.create(
                    size = 4,
                    difficulty = Difficulty.VERY_EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    fields =
                        mutableListOf(
                            Field(position = Position.create(0, 4), solution = 1, value = 1, given = true),
                            Field(position = Position.create(1, 4), solution = 2, value = 2, given = true),
                        ),
                )

            sudoku.progress shouldBe 100
        }

        should("compute progress as the percentage of non-given fields solved correctly") {
            val sudoku =
                Sudoku.create(
                    size = 4,
                    difficulty = Difficulty.VERY_EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    fields =
                        mutableListOf(
                            Field(position = Position.create(0, 4), solution = 1, value = 1, given = true),
                            Field(position = Position.create(1, 4), solution = 2, value = 2, given = false),
                            Field(position = Position.create(2, 4), solution = 3, value = null, given = false),
                        ),
                )

            sudoku.progress shouldBe 50
        }

        should("get and set a field by index") {
            val sudoku = fourByFourSudoku()

            val field = sudoku[5]
            sudoku[5] = field.copy(value = 3)

            sudoku[5].value shouldBe 3
            sudoku[5].position shouldBe Position.create(5, 4)
        }

        should("get and set a field by position") {
            val sudoku = fourByFourSudoku()
            val position = Position.create(6, 4)

            sudoku[position] = sudoku[position].copy(value = 2)

            sudoku[position].value shouldBe 2
            sudoku[position].position shouldBe position
        }

        should("get and set a field by row and column") {
            val sudoku = fourByFourSudoku()

            sudoku[1, 2] = sudoku[1, 2].copy(value = 4)

            sudoku[1, 2].value shouldBe 4
            sudoku[1, 2].position shouldBe Position.create(size = 4, row = 1, column = 2)
        }

        should("consider two sudokus with the same id equal even with differing other fields") {
            val id = SudokuId.generate()
            val first = fourByFourSudoku(sudokuId = id, difficulty = Difficulty.EASY)
            val second = fourByFourSudoku(sudokuId = id, difficulty = Difficulty.HARD)

            (first == second) shouldBe true
            first.hashCode() shouldBe second.hashCode()
        }

        should("consider a sudoku equal to itself") {
            val sudoku = fourByFourSudoku()

            (sudoku == sudoku) shouldBe true
        }

        should("consider sudokus with different ids not equal") {
            val first = fourByFourSudoku(sudokuId = SudokuId.generate())
            val second = fourByFourSudoku(sudokuId = SudokuId.generate())

            (first == second) shouldBe false
        }

        should("never equal an instance of a different class") {
            val sudoku = fourByFourSudoku()

            (sudoku.equals("not a sudoku")) shouldBe false
        }

        should("generate unique ids") {
            val first = SudokuId.generate()
            val second = SudokuId.generate()

            (first == second) shouldBe false
        }
    },
)

private fun fourByFourSudoku(
    sudokuId: SudokuId = SudokuId.generate(),
    difficulty: Difficulty = Difficulty.VERY_EASY,
): Sudoku =
    Sudoku.create(
        sudokuId = sudokuId,
        size = 4,
        difficulty = difficulty,
        modeLevel = Sudoku.MODE_NORMAL,
        fields = MutableList(16) { index -> Field(position = Position.create(index, 4), solution = (index % 4) + 1) },
    )
