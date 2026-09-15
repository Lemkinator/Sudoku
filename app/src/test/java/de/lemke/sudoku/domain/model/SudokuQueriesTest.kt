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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

// A valid, fully solved 4x4 grid (rows, columns and 2x2 blocks each contain 1..4 exactly once).
// An immutable List (not an IntArray, whose elements are settable) rules out cross-test state leakage by construction.
private val solutions = listOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 1, 4, 3, 4, 3, 2, 1)

private fun solvedFields(
    incorrect: Set<Int> = emptySet(),
    unfilled: Set<Int> = emptySet(),
): MutableList<Field> =
    solutions
        .mapIndexed { index, solution ->
            Field(
                position = Position.create(index, 4),
                solution = solution,
                value =
                    when (index) {
                        in unfilled -> null
                        in incorrect -> solution + 1
                        else -> solution
                    },
            )
        }.toMutableList()

private fun querySudoku(
    incorrect: Set<Int> = emptySet(),
    unfilled: Set<Int> = emptySet(),
): Sudoku =
    Sudoku.create(
        size = 4,
        difficulty = Difficulty.EASY,
        modeLevel = Sudoku.MODE_NORMAL,
        fields = solvedFields(incorrect, unfilled),
    )

class SudokuQueriesTest : ShouldSpec(
    {
        should("return the union of row, column and block fields as neighbors of a position") {
            val sudoku = querySudoku()

            val neighbors = sudoku.getNeighbors(Position.create(0, 4))

            // row0 + column0 + block0, concatenated (not deduplicated) -> idx0 itself appears in all three.
            neighbors.map { it.position.index } shouldContainExactlyInAnyOrder listOf(0, 1, 2, 3, 0, 4, 8, 12, 0, 1, 4, 5)
        }

        should("return the same neighbors when queried by index as by position") {
            val sudoku = querySudoku()

            sudoku.getNeighbors(5).map { it.position.index } shouldBe sudoku.getNeighbors(Position.create(5, 4)).map { it.position.index }
        }

        should("report a row completed when every field in it is correct") {
            val sudoku = querySudoku()

            sudoku.isRowCompleted(0) shouldBe true
        }

        should("report a row not completed when a field in it is wrong or unfilled") {
            val wrong = querySudoku(incorrect = setOf(1))
            val unfilled = querySudoku(unfilled = setOf(2))

            wrong.isRowCompleted(0) shouldBe false
            unfilled.isRowCompleted(0) shouldBe false
        }

        should("report a column completed when every field in it is correct") {
            val sudoku = querySudoku()

            sudoku.isColumnCompleted(0) shouldBe true
        }

        should("report a column not completed when a field in it is wrong or unfilled") {
            val wrong = querySudoku(incorrect = setOf(4)) // idx4 is column 0
            val unfilled = querySudoku(unfilled = setOf(8)) // idx8 is column 0

            wrong.isColumnCompleted(0) shouldBe false
            unfilled.isColumnCompleted(0) shouldBe false
        }

        should("report a block completed when every field in it is correct") {
            val sudoku = querySudoku()

            sudoku.isBlockCompleted(0) shouldBe true
        }

        should("report a block not completed when a field in it is wrong or unfilled") {
            val wrong = querySudoku(incorrect = setOf(1)) // idx1 is block 0
            val unfilled = querySudoku(unfilled = setOf(5)) // idx5 is block 0

            wrong.isBlockCompleted(0) shouldBe false
            unfilled.isBlockCompleted(0) shouldBe false
        }

        should("report each number complete only once it appears size times correctly") {
            // Fully solved board: every number 1..4 appears exactly 4 times, all correct.
            val sudoku = querySudoku()

            val completedNumbers = sudoku.getCompletedNumbers()

            completedNumbers shouldBe listOf(1 to true, 2 to true, 3 to true, 4 to true)
        }

        should("report a number as not complete while some of its placements are wrong or unfilled") {
            // idx0's solution is 1; making it wrong and idx6 (also solution 1) unfilled leaves zero correct 1s.
            val sudoku = querySudoku(incorrect = setOf(0), unfilled = setOf(6))

            val completedNumbers = sudoku.getCompletedNumbers()

            completedNumbers.first { it.first == 1 }.second shouldBe false
            completedNumbers.first { it.first == 2 }.second shouldBe true
        }
    },
)
