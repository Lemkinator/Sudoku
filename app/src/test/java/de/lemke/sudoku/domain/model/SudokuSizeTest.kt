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
        should("define 4x4, 9x9 and 16x16 boards with hint limits 1, 3 and 8") {
            SudokuSize.entries.associate { it.value to it.hintLimit } shouldBe mapOf(4 to 1, 9 to 3, 16 to 8)
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
