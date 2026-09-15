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
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateFieldsUseCaseTest : ShouldSpec(
    {
        val useCase = GenerateFieldsUseCase(UnconfinedTestDispatcher())

        should("generate size*size fields for a 4x4 grid") {
            val fields = useCase(4, Difficulty.MEDIUM)
            fields shouldHaveSize 16
        }

        should("generate size*size fields for a 9x9 grid") {
            val fields = useCase(9, Difficulty.MEDIUM)
            fields shouldHaveSize 81
        }

        should("generate size*size fields for a 16x16 grid") {
            val fields = useCase(16, Difficulty.MEDIUM)
            fields shouldHaveSize 256
        }

        should("give every field a position matching its list index") {
            val fields = useCase(9, Difficulty.MEDIUM)
            fields.forEachIndexed { index, field -> field.position.index shouldBe index }
        }

        should("mark given fields with a value equal to the solution") {
            val fields = useCase(9, Difficulty.EASY)
            fields.filter { it.given }.forEach { it.value shouldBe it.solution }
        }

        should("leave non-given fields without a value") {
            val fields = useCase(9, Difficulty.EASY)
            fields.filter { !it.given }.forEach { it.value shouldBe null }
        }

        should("remove exactly numbersToRemove fields for the requested difficulty") {
            val difficulty = Difficulty.HARD
            val fields = useCase(9, difficulty)
            val removedCount = fields.count { !it.given }
            removedCount shouldBe difficulty.numbersToRemove(9)
        }

        should("keep every solution value within the grid's valid digit range") {
            val fields = useCase(9, Difficulty.EXPERT)
            fields.forEach { it.solution.shouldBeInRange(1..9) }
        }

        should("fall back to the 9x9 schema for an unsupported size") {
            val fields = useCase(6, Difficulty.MEDIUM)
            fields shouldHaveSize 36
            fields.forEach { it.solution.shouldBeInRange(1..9) }
        }
    },
)
