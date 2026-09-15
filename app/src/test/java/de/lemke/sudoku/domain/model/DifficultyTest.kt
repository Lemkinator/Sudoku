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
import io.kotest.matchers.shouldBe

class DifficultyTest : ShouldSpec(
    {
        should("expose value as the enum's ordinal") {
            Difficulty.VERY_EASY.value shouldBe 0
            Difficulty.EASY.value shouldBe 1
            Difficulty.MEDIUM.value shouldBe 2
            Difficulty.HARD.value shouldBe 3
            Difficulty.EXPERT.value shouldBe 4
        }

        should("map a valid ordinal back to its difficulty via fromInt") {
            Difficulty.entries.forEach { difficulty ->
                Difficulty.fromInt(difficulty.ordinal) shouldBe difficulty
            }
        }

        should("fall back to MEDIUM when fromInt is given null") {
            Difficulty.fromInt(null) shouldBe Difficulty.MEDIUM
        }

        should("fall back to MEDIUM when fromInt is given a negative ordinal") {
            Difficulty.fromInt(-1) shouldBe Difficulty.MEDIUM
        }

        should("fall back to MEDIUM when fromInt is given an ordinal beyond the last entry") {
            Difficulty.fromInt(Difficulty.entries.size) shouldBe Difficulty.MEDIUM
        }

        should("look up numbersToRemove per known size for every difficulty") {
            Difficulty.VERY_EASY.numbersToRemove(4) shouldBe 4 * 4 - 10
            Difficulty.EXPERT.numbersToRemove(9) shouldBe 9 * 9 - 23
            Difficulty.HARD.numbersToRemove(16) shouldBe 16 * 16 - 136
        }

        should("fall back to the size-9 table for an unlisted size") {
            Difficulty.MEDIUM.numbersToRemove(25) shouldBe 25 * 25 - 35
        }

        should("expose max as the index of the last difficulty entry") {
            Difficulty.max shouldBe 4
        }
    },
)
