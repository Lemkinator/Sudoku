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

package de.lemke.sudoku.ui.board

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class CellAnimationTest : ShouldSpec(
    {
        should("start steps of a row wave 40 ms apart on a 9x9 board") { CellAnimation.WAVE.stepMillis(3) shouldBe 40L }

        should("start steps of a board wave 6 ms apart on a 9x9 board") { CellAnimation.BOARD_WAVE.stepMillis(3) shouldBe 6L }

        should("start steps of a pulse 180 ms apart") { CellAnimation.PULSE.stepMillis(3) shouldBe 180L }

        should("last two legs after the last step starts") { CellAnimation.WAVE.totalMillis(steps = 5, blockSize = 3) shouldBe 660L }

        should("last two legs for a single step") { CellAnimation.PULSE.totalMillis(steps = 1, blockSize = 3) shouldBe 900L }

        should("last two legs for no step") { CellAnimation.PULSE.totalMillis(steps = 0, blockSize = 3) shouldBe 900L }

        should("rest before the start, peak after one leg and rest after two") {
            listOf(-5f, 0f, 125f, 250f, 375f, 500f, 600f).map(CellAnimation.WAVE::peakAt) shouldBe
                listOf(0f, 0f, 0.5f, 1f, 0.5f, 0f, 0f)
        }
    },
)
