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

class NoteMaskTest : ShouldSpec(
    {
        should("list its digits ascending") { NoteMask.of(9, 1, 4).digits shouldBe listOf(1, 4, 9) }

        should("restore the mask when toggling a digit twice") { NoteMask.of(2).toggled(3).toggled(3) shouldBe NoteMask.of(2) }

        should("add a digit that is missing when toggling") { NoteMask.of(2).toggled(3) shouldBe NoteMask.of(2, 3) }

        should("store digit 16 in bit 15") { NoteMask.of(16).bits shouldBe 0x8000 }

        should("keep the mask when removing a missing digit") { NoteMask.of(1) - 5 shouldBe NoteMask.of(1) }

        should("remove a present digit") { NoteMask.of(1, 5) - 5 shouldBe NoteMask.of(1) }

        should("count its digits") { NoteMask.of(3, 7, 16).count shouldBe 3 }

        should("contain only its digits") {
            val mask = NoteMask.of(3)
            listOf(2 in mask, 3 in mask, 0 in mask, 33 in mask) shouldBe listOf(false, true, false, false)
        }

        should("report emptiness") {
            NoteMask.EMPTY.isEmpty() shouldBe true
            NoteMask.of(1).isEmpty() shouldBe false
        }
    },
)
