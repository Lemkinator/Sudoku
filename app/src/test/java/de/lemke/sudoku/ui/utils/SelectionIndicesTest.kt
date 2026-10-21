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

package de.lemke.sudoku.ui.utils

import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class SelectionIndicesTest : ShouldSpec(
    {
        should("place the buttons of a 4x4 board after its 16 fields") {
            val indices = SelectionIndices(SudokuSize.FOUR)
            indices.fields shouldBe 0..15
            indices.numberButtons shouldBe 16..19
            indices.deleteButton shouldBe 20
            indices.hintButton shouldBe 21
            indices.buttons shouldBe 16..21
        }

        should("place the buttons of a 9x9 board after its 81 fields") {
            val indices = SelectionIndices(SudokuSize.NINE)
            indices.fields shouldBe 0..80
            indices.numberButtons shouldBe 81..89
            indices.deleteButton shouldBe 90
            indices.hintButton shouldBe 91
            indices.buttons shouldBe 81..91
        }

        should("place the buttons of a 16x16 board after its 256 fields") {
            val indices = SelectionIndices(SudokuSize.SIXTEEN)
            indices.fields shouldBe 0..255
            indices.numberButtons shouldBe 256..271
            indices.deleteButton shouldBe 272
            indices.hintButton shouldBe 273
            indices.buttons shouldBe 256..273
        }

        should("map every index of a 4x4 board to its control") {
            val indices = SelectionIndices(SudokuSize.FOUR)
            indices.targetOf(0) shouldBe SelectionTarget.FIELD
            indices.targetOf(15) shouldBe SelectionTarget.FIELD
            indices.targetOf(16) shouldBe SelectionTarget.NUMBER_BUTTON
            indices.targetOf(19) shouldBe SelectionTarget.NUMBER_BUTTON
            indices.targetOf(20) shouldBe SelectionTarget.DELETE_BUTTON
            indices.targetOf(21) shouldBe SelectionTarget.HINT_BUTTON
        }

        should("reject an index outside the board") {
            val indices = SelectionIndices(SudokuSize.FOUR)
            shouldThrow<IllegalArgumentException> { indices.targetOf(-1) }
            shouldThrow<IllegalArgumentException> { indices.targetOf(22) }
        }
    },
)
