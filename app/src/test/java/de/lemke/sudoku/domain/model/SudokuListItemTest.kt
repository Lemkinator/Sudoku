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

import de.lemke.sudoku.domain.model.SudokuListItem.SeparatorItem
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class SudokuListItemTest : ShouldSpec(
    {
        val id = SudokuId("list-item")
        val fields = MutableList(4) { index -> Field(Position.create(index, 2), solution = index + 1) }

        fun sudoku(errorsMade: Int = 0): Sudoku =
            Sudoku.create(
                sudokuId = id,
                size = 2,
                difficulty = Difficulty.VERY_EASY,
                modeLevel = Sudoku.MODE_NORMAL,
                errorsMade = errorsMade,
                created = LocalDateTime.of(2026, 1, 15, 9, 0),
                updated = LocalDateTime.of(2026, 1, 15, 9, 0),
                fields = fields,
            )

        should("equal an item over another instance with the same content, with the same hash code") {
            val item = SudokuItem(sudoku(), "15.01.26")
            val same = SudokuItem(sudoku(), "15.01.26")

            (item == same) shouldBe true
            item.hashCode() shouldBe same.hashCode()
        }

        should("not equal an item whose sudoku has the same id but other stats") {
            (SudokuItem(sudoku(errorsMade = 0), "15.01.26") == SudokuItem(sudoku(errorsMade = 2), "15.01.26")) shouldBe false
        }

        should("not equal an item over the same sudoku with another label") {
            (SudokuItem(sudoku(), "15.01.26") == SudokuItem(sudoku(), "16.01.26")) shouldBe false
        }

        should("not equal a separator with the same label") {
            val item: SudokuListItem = SudokuItem(sudoku(), "15.01.26")

            (item == SeparatorItem("15.01.26")) shouldBe false
        }
    },
)
