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
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class SudokuListItemTest : ShouldSpec(
    {
        val id = SudokuId("list-item")
        val fields = MutableList(4) { index -> Field(Position.create(index, 2), solution = index + 1) }

        fun sudoku(
            errorsMade: Int = 0,
            seconds: Int = 0,
            hintsUsed: Int = 0,
        ): Sudoku =
            Sudoku.create(
                sudokuId = id,
                size = 2,
                difficulty = Difficulty.VERY_EASY,
                modeLevel = Sudoku.MODE_NORMAL,
                hintsUsed = hintsUsed,
                errorsMade = errorsMade,
                created = LocalDateTime.of(2026, 1, 15, 9, 0),
                updated = LocalDateTime.of(2026, 1, 15, 9, 0),
                seconds = seconds,
                fields = fields,
            )

        should("equal an item over another instance with the same fields, with the same hash code") {
            val item = SudokuItem(sudoku(errorsMade = 2, seconds = 75, hintsUsed = 1), "15.01.26")
            val same = SudokuItem(sudoku(errorsMade = 2, seconds = 75, hintsUsed = 1), "15.01.26")

            item shouldBeEqual same
            item.hashCode() shouldBe same.hashCode()
        }

        should("not equal an item whose sudoku has the same id but other errors, seconds or hints") {
            val item = SudokuItem(sudoku(), "15.01.26")

            item shouldNotBeEqual SudokuItem(sudoku(errorsMade = 2), "15.01.26")
            item shouldNotBeEqual SudokuItem(sudoku(seconds = 75), "15.01.26")
            item shouldNotBeEqual SudokuItem(sudoku(hintsUsed = 1), "15.01.26")
        }

        should("not equal an item over the same sudoku with another label") {
            SudokuItem(sudoku(), "15.01.26") shouldNotBeEqual SudokuItem(sudoku(), "16.01.26")
        }

        should("print its sudoku and label") {
            val sudoku = sudoku()

            SudokuItem(sudoku, "7").toString() shouldBe "SudokuItem(sudoku=$sudoku, label=7)"
        }

        should("not equal a separator with the same label") {
            val item: SudokuListItem = SudokuItem(sudoku(), "15.01.26")

            item shouldNotBeEqual SeparatorItem("15.01.26")
        }
    },
)
