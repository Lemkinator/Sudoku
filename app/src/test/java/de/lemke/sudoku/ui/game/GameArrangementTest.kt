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

package de.lemke.sudoku.ui.game

import de.lemke.sudoku.ui.board.Box
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class GameArrangementTest : ShouldSpec(
    {
        should("stack the board above the controls on a portrait phone and center the group") {
            arrangeGame(1080, 2000, 450, controlsAtEnd = false) shouldBe
                GameArrangement(Box(0f, 235f, 1080f, 1315f), Box(0f, 1315f, 1080f, 1765f))
        }

        should("put the controls beside the board in landscape and center the group") {
            arrangeGame(2000, 1000, 300, controlsAtEnd = true) shouldBe
                GameArrangement(Box(350f, 0f, 1350f, 1000f), Box(1350f, 0f, 1650f, 1000f))
        }

        should("shrink the board to nothing before the controls lose any extent") {
            arrangeGame(400, 300, 450, controlsAtEnd = false) shouldBe
                GameArrangement(Box(200f, 0f, 200f, 0f), Box(0f, 0f, 400f, 450f))
        }

        should("center a board that is narrower than the cross axis") {
            arrangeGame(1000, 400, 300, controlsAtEnd = true) shouldBe
                GameArrangement(Box(150f, 0f, 550f, 400f), Box(550f, 0f, 850f, 400f))
        }

        should("center a portrait board that leaves room on the sides") {
            arrangeGame(1200, 1000, 300, controlsAtEnd = false) shouldBe
                GameArrangement(Box(250f, 0f, 950f, 700f), Box(0f, 700f, 1200f, 1000f))
        }

        should("keep a zero sized window at the origin") {
            arrangeGame(0, 0, 0, controlsAtEnd = true) shouldBe GameArrangement(Box(0f, 0f, 0f, 0f), Box(0f, 0f, 0f, 0f))
        }
    },
)
