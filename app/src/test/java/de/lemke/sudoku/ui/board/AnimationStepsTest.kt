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

package de.lemke.sudoku.ui.board

import de.lemke.sudoku.domain.model.CompletedRegions
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

private fun regions(
    row: Boolean = false,
    column: Boolean = false,
    block: Boolean = false,
    board: Boolean = false,
) = CompletedRegions(row = row, column = column, block = block, board = board)

class AnimationStepsTest : ShouldSpec(
    {
        should("move outward from a completed row in both directions") {
            animationSteps(SudokuSize.NINE, 40, regions(row = true)) shouldBe
                listOf(listOf(40, 41), listOf(39, 42), listOf(38, 43), listOf(37, 44), listOf(36))
        }

        should("pair the first cell of a row with its successor and then step singly") {
            animationSteps(SudokuSize.NINE, 36, regions(row = true)) shouldBe
                listOf(listOf(36, 37), listOf(38), listOf(39), listOf(40), listOf(41), listOf(42), listOf(43), listOf(44))
        }

        should("move outward from a completed column") {
            animationSteps(SudokuSize.NINE, 40, regions(column = true)) shouldBe
                listOf(listOf(40, 49), listOf(31, 58), listOf(22, 67), listOf(13, 76), listOf(4))
        }

        should("move outward from a completed block") {
            animationSteps(SudokuSize.NINE, 40, regions(block = true)) shouldBe
                listOf(listOf(40, 41), listOf(39, 48), listOf(32, 49), listOf(31, 50), listOf(30))
        }

        should("order the union of several regions by index") {
            animationSteps(SudokuSize.NINE, 40, regions(row = true, column = true)) shouldBe
                listOf(
                    listOf(40, 41),
                    listOf(39, 42),
                    listOf(38, 43),
                    listOf(37, 44),
                    listOf(36, 49),
                    listOf(31, 58),
                    listOf(22, 67),
                    listOf(13, 76),
                    listOf(4),
                )
        }

        should("list a cell shared by two regions once") {
            animationSteps(SudokuSize.FOUR, 5, regions(row = true, block = true)) shouldBe
                listOf(listOf(5, 6), listOf(4, 7), listOf(1), listOf(0))
        }

        should("move outward over the whole 4x4 board") {
            animationSteps(SudokuSize.FOUR, 5, regions(board = true)) shouldBe
                listOf(
                    listOf(5, 6),
                    listOf(4, 7),
                    listOf(3, 8),
                    listOf(2, 9),
                    listOf(1, 10),
                    listOf(0, 11),
                    listOf(12),
                    listOf(13),
                    listOf(14),
                    listOf(15),
                )
        }

        should("cover the whole 9x9 board in 41 steps") {
            val steps = animationSteps(SudokuSize.NINE, 40, regions(board = true))
            listOf(steps.size, steps.first(), steps.last()) shouldBe listOf(41, listOf(40, 41), listOf(0))
        }

        should("have no steps when no region is completed") {
            animationSteps(SudokuSize.NINE, 40, CompletedRegions.NONE) shouldBe emptyList()
        }
    },
)
