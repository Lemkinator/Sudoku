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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class SudokuSizeTest : ShouldSpec(
    {
        should("define exactly the 4x4, 9x9 and 16x16 boards in ascending order") {
            SudokuSize.entries shouldBe listOf(SudokuSize.FOUR, SudokuSize.NINE, SudokuSize.SIXTEEN)
        }

        should("define the 4x4 board's dimensions, hint limit and filter flag") {
            SudokuSize.FOUR.value shouldBe 4
            SudokuSize.FOUR.blockSize shouldBe 2
            SudokuSize.FOUR.hintLimit shouldBe 1
            SudokuSize.FOUR.filterFlag shouldBe SudokuFilterFlags.SIZE_4X4
            SudokuSize.FOUR.cellCount shouldBe 16
        }

        should("define the 9x9 board's dimensions, hint limit and filter flag") {
            SudokuSize.NINE.value shouldBe 9
            SudokuSize.NINE.blockSize shouldBe 3
            SudokuSize.NINE.hintLimit shouldBe 3
            SudokuSize.NINE.filterFlag shouldBe SudokuFilterFlags.SIZE_9X9
            SudokuSize.NINE.cellCount shouldBe 81
        }

        should("define the 16x16 board's dimensions, hint limit and filter flag") {
            SudokuSize.SIXTEEN.value shouldBe 16
            SudokuSize.SIXTEEN.blockSize shouldBe 4
            SudokuSize.SIXTEEN.hintLimit shouldBe 8
            SudokuSize.SIXTEEN.filterFlag shouldBe SudokuFilterFlags.SIZE_16X16
            SudokuSize.SIXTEEN.cellCount shouldBe 256
        }

        should("look up every size by its stored value") {
            SudokuSize.fromValueOrNull(4) shouldBe SudokuSize.FOUR
            SudokuSize.fromValueOrNull(9) shouldBe SudokuSize.NINE
            SudokuSize.fromValueOrNull(16) shouldBe SudokuSize.SIXTEEN
            SudokuSize.fromValue(16) shouldBe SudokuSize.SIXTEEN
        }

        should("find no size for an unsupported value") {
            listOf(-2, 0, 1, 6, 25).forEach { SudokuSize.fromValueOrNull(it).shouldBeNull() }
        }

        should("reject an unsupported value in fromValue") {
            val error = shouldThrow<IllegalArgumentException> { SudokuSize.fromValue(6) }
            error.message shouldBe "Unsupported sudoku size: 6"
        }
    },
)
