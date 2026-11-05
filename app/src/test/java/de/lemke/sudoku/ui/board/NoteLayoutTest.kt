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

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlin.math.abs

private const val TOLERANCE = 1e-3f
private val content = Box(6f, 6f, 94f, 94f)

private fun Float.shouldBeNear(expected: Float) =
    withClue("expected $expected but was $this") { (abs(this - expected) <= TOLERANCE) shouldBe true }

private fun Box.shouldBeNear(expected: Box) {
    left.shouldBeNear(expected.left)
    top.shouldBeNear(expected.top)
    right.shouldBeNear(expected.right)
    bottom.shouldBeNear(expected.bottom)
}

class NoteLayoutTest : ShouldSpec(
    {
        should("pick the grid side by count") {
            listOf(0, 1, 2, 4, 5, 9, 10, 16).map(::noteGridSide) shouldBe listOf(0, 1, 2, 2, 3, 3, 4, 4)
        }

        should("size the value text at 0.62 of the cell") { valueTextSize(100f).shouldBeNear(62f) }

        should("cap one note below the value") { noteTextSize(content, 1, 62f).shouldBeNear(37.2f) }

        should("size four notes by their slot") {
            noteTextSize(content, 4, 62f).shouldBeNear(30.8f)
            noteSlots(content, 4)[3].shouldBeNear(Box(50f, 50f, 94f, 94f))
        }

        should("size nine notes by their slot") {
            noteTextSize(content, 9, 62f).shouldBeNear(20.533f)
            noteSlots(content, 9)[4].shouldBeNear(Box(35.333f, 35.333f, 64.667f, 64.667f))
        }

        should("fill the first three slots of the 2x2 grid for three notes") {
            val slots = noteSlots(content, 3)
            slots.size shouldBe 3
            slots[0].shouldBeNear(Box(6f, 6f, 50f, 50f))
            slots[1].shouldBeNear(Box(50f, 6f, 94f, 50f))
            slots[2].shouldBeNear(Box(6f, 50f, 50f, 94f))
        }

        should("size sixteen notes in a 16x16 cell") {
            noteTextSize(Box(0f, 0f, 63.36f, 63.36f), 16, 44.64f).shouldBeNear(11.088f)
        }

        should("keep a single note on 4x4 below the value") {
            noteTextSize(Box(13.5f, 13.5f, 211.5f, 211.5f), 1, 139.5f).shouldBeNear(83.7f)
        }

        should("center the grid in a content box that is wider than tall") {
            noteSlots(Box(0f, 0f, 100f, 50f), 4)[0].shouldBeNear(Box(25f, 0f, 50f, 25f))
        }

        should("have no slots and no text for zero notes") {
            noteSlots(content, 0) shouldBe emptyList()
            noteTextSize(content, 0, 62f).shouldBeNear(0f)
        }
    },
)
