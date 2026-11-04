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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class SudokuExtensionsTest : ShouldSpec(
    {
        val originalLocale = Locale.getDefault()
        beforeSpec { Locale.setDefault(Locale.US) }
        afterSpec { Locale.setDefault(originalLocale) }

        should("map null to a null sudoku string") {
            (null as Int?).toSudokuString().shouldBeNull()
        }

        should("map standard digits 1..9 to their digit string and char") {
            1.toSudokuString() shouldBe "1"
            9.toSudokuString() shouldBe "9"
            1.toSudokuDigitChar() shouldBe '1'
            9.toSudokuDigitChar() shouldBe '9'
        }

        should("map large digits 10..16 to letters A..G") {
            10.toSudokuString() shouldBe "A"
            16.toSudokuString() shouldBe "G"
            10.toSudokuDigitChar() shouldBe 'A'
            16.toSudokuDigitChar() shouldBe 'G'
        }

        should("map out-of-range values to null") {
            0.toSudokuString().shouldBeNull()
            17.toSudokuString().shouldBeNull()
            (-1).toSudokuString().shouldBeNull()
        }

        should("map digit chars back to their value") {
            '1'.toSudokuInt() shouldBe 1
            '9'.toSudokuInt() shouldBe 9
            'A'.toSudokuInt() shouldBe 10
            'G'.toSudokuInt() shouldBe 16
        }

        should("map chars outside the sudoku digits to null") {
            '0'.toSudokuInt().shouldBeNull()
            'H'.toSudokuInt().shouldBeNull()
            'a'.toSudokuInt().shouldBeNull()
            '.'.toSudokuInt().shouldBeNull()
        }

        should("format monthAndYear as full month name and year") {
            LocalDateTime.of(2024, 3, 15, 10, 30).monthAndYear shouldBe "March 2024"
        }

        should("format dateFormatShort using the short localized date style") {
            LocalDateTime.of(2024, 3, 15, 10, 30).dateFormatShort shouldBe "3/15/24"
        }

        should("format LocalDate formatFull using the full localized date style") {
            LocalDate.of(2024, 3, 15).formatFull shouldBe "Friday, March 15, 2024"
        }

        should("format LocalDateTime formatFull using the full date and medium time style") {
            val formatted = LocalDateTime.of(2024, 3, 15, 10, 30).formatFull

            formatted.contains("Friday, March 15, 2024") shouldBe true
            formatted.contains("10:30") shouldBe true
        }
    },
)
