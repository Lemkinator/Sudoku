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

package de.lemke.sudoku.domain

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateFieldsUseCaseTest : ShouldSpec(
    {
        val useCase = GenerateFieldsUseCase(UnconfinedTestDispatcher(), CreatorSolvedBoardGenerator())
        val patternUseCase = GenerateFieldsUseCase(UnconfinedTestDispatcher(), PatternSolvedBoardGenerator())

        listOf(
            Triple(SudokuSize.FOUR, 16, (1..4).toSet()),
            Triple(SudokuSize.NINE, 81, (1..9).toSet()),
            Triple(SudokuSize.SIXTEEN, 256, (1..16).toSet()),
        ).forEach { (size, fieldCount, digits) ->
            should("generate a $size grid with its field count and digits") {
                val fields = patternUseCase(size, Difficulty.MEDIUM)
                fields shouldHaveSize fieldCount
                fields.map { it.solution }.toSet() shouldBe digits
            }
        }

        should("take a 16x16 grid's solutions from the solved board and clear numbersToRemove of them") {
            val fields = patternUseCase(SudokuSize.SIXTEEN, Difficulty.MEDIUM)
            val rows = fields.map { it.solution }.chunked(16)
            rows[0] shouldBe (1..16).toList()
            rows[1] shouldBe (5..16).toList() + (1..4).toList()
            val digits = (1..16).toSet()
            rows.forEach { row -> row.toSet() shouldBe digits }
            (0 until 16).forEach { col -> rows.map { it[col] }.toSet() shouldBe digits }
            (0 until 16).forEach { block ->
                val blockRow = block / 4 * 4
                val blockCol = block % 4 * 4
                (blockRow until blockRow + 4).flatMap { row -> rows[row].subList(blockCol, blockCol + 4) }.toSet() shouldBe digits
            }
            fields.count { !it.given } shouldBe 100
        }

        should("give every field a position matching its list index") {
            val fields = useCase(SudokuSize.NINE, Difficulty.MEDIUM)
            fields.forEachIndexed { index, field -> field.position.index shouldBe index }
        }

        should("mark given fields with a value equal to the solution") {
            val fields = useCase(SudokuSize.NINE, Difficulty.EASY)
            fields.filter { it.given }.forEach { it.value shouldBe it.solution }
        }

        should("leave non-given fields without a value") {
            val fields = useCase(SudokuSize.NINE, Difficulty.EASY)
            fields.filter { !it.given }.forEach { it.value shouldBe null }
        }

        should("remove exactly 51 fields from a 9x9 HARD grid") {
            val fields = useCase(SudokuSize.NINE, Difficulty.HARD)
            val removedCount = fields.count { !it.given }
            removedCount shouldBe 51
        }

        should("keep every solution value within the grid's valid digit range") {
            val fields = useCase(SudokuSize.NINE, Difficulty.EXPERT)
            fields.forEach { it.solution.shouldBeInRange(1..9) }
        }
    },
)
