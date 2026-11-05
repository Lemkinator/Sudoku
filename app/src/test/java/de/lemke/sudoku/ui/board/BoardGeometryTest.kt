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

import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

private const val TOLERANCE = 1e-3

private fun Box.shouldBeCloseTo(expected: Box) {
    left.toDouble() shouldBe (expected.left.toDouble() plusOrMinus TOLERANCE)
    top.toDouble() shouldBe (expected.top.toDouble() plusOrMinus TOLERANCE)
    right.toDouble() shouldBe (expected.right.toDouble() plusOrMinus TOLERANCE)
    bottom.toDouble() shouldBe (expected.bottom.toDouble() plusOrMinus TOLERANCE)
}

private fun geometry(
    size: SudokuSize,
    side: Float,
    cornerRadius: Float,
) = BoardGeometry(size, BoardMetrics(side, cornerRadius, thinLine = 1f, boxLine = 2f, border = 2f))

class BoardGeometryTest : ShouldSpec(
    {
        val g9 = geometry(SudokuSize.NINE, 900f, 78f)
        val g1000 = geometry(SudokuSize.NINE, 1000f, 78f)
        val g16 = geometry(SudokuSize.SIXTEEN, 1152f, 78f)
        val g4 = geometry(SudokuSize.FOUR, 200f, 78f)
        val square4 = geometry(SudokuSize.FOUR, 4f, 0f)

        should("9x9 edges of a 1000 px board") { (0..3).map(g1000::edge) shouldBe listOf(0f, 111f, 222f, 333f) }

        should("end the last edge on the board side") { g1000.edge(9) shouldBe 1000f }

        should("tile the board with cell boxes") {
            g1000.cellBox(1) shouldBe Box(111f, 0f, 222f, 111f)
            g1000.cellBox(10) shouldBe Box(111f, 111f, 222f, 222f)
        }

        should("9x9 corner cell content") { g9.contentBox(0).shouldBeCloseTo(Box(28.845f, 28.845f, 94f, 94f)) }

        should("9x9 top-right corner cell content") { g9.contentBox(8).shouldBeCloseTo(Box(806f, 28.845f, 871.155f, 94f)) }

        should("9x9 bottom-left corner cell content") { g9.contentBox(72).shouldBeCloseTo(Box(28.845f, 806f, 94f, 871.155f)) }

        should("9x9 corner cell insets sit on both outer sides") {
            g9.cornerInsets(0).shouldBeCloseTo(Box(22.845f, 22.845f, 0f, 0f))
            g9.cornerInsets(80).shouldBeCloseTo(Box(0f, 0f, 22.845f, 22.845f))
        }

        should("9x9 cell 1 has no inset") { g9.cornerInsets(1) shouldBe Box(0f, 0f, 0f, 0f) }

        should("9x9 corner cells are the four corners") { g9.cornerCells shouldBe listOf(0, 8, 72, 80) }

        should("16x16 arc neighbor gets a top inset") { g16.contentBox(1).top.toDouble() shouldBe (4.551 plusOrMinus TOLERANCE) }

        should("16x16 arc neighbor insets only its outer side") {
            g16.cornerInsets(1).shouldBeCloseTo(Box(0f, 0.231f, 0f, 0f))
            g16.cornerInsets(16).shouldBeCloseTo(Box(0.231f, 0f, 0f, 0f))
        }

        should("16x16 cell 17 has padding only") { g16.contentBox(17).shouldBeCloseTo(Box(76.32f, 76.32f, 139.68f, 139.68f)) }

        should("16x16 arc cells are the corners and their neighbors") {
            g16.cornerCells.toSet() shouldBe setOf(0, 1, 16, 14, 15, 31, 224, 240, 241, 239, 254, 255)
        }

        should("4x4 arc neighbors on every board side get an inset") {
            g4.cornerInsets(1).shouldBeCloseTo(Box(0f, 5.199f, 0f, 0f))
            g4.cornerInsets(2).shouldBeCloseTo(Box(0f, 5.199f, 0f, 0f))
            g4.cornerInsets(4).shouldBeCloseTo(Box(5.199f, 0f, 0f, 0f))
            g4.cornerInsets(11).shouldBeCloseTo(Box(0f, 0f, 5.199f, 0f))
            g4.cornerInsets(14).shouldBeCloseTo(Box(0f, 0f, 0f, 5.199f))
        }

        should("4x4 cells away from the corners have no inset") {
            g4.cornerCells.toSet() shouldBe setOf(0, 1, 2, 3, 4, 7, 8, 11, 12, 13, 14, 15)
        }

        should("4x4 corner cell content") { g4.contentBox(0).shouldBeCloseTo(Box(25.845f, 25.845f, 47f, 47f)) }

        should("4x4 arc neighbor content") { g4.contentBox(1).shouldBeCloseTo(Box(53f, 8.199f, 97f, 47f)) }

        should("a board without rounded corners has padding only") {
            square4.cornerCells shouldBe emptyList()
            square4.contentBox(0).shouldBeCloseTo(Box(0.06f, 0.06f, 0.94f, 0.94f))
        }

        should("a tap in the cut corner hits nothing") { g9.indexAt(5f, 5f) shouldBe null }

        should("a tap in the cut bottom-right corner hits nothing") { g9.indexAt(895f, 895f) shouldBe null }

        should("a tap inside the arc hits the corner cell") { g9.indexAt(850f, 50f) shouldBe 8 }

        should("the center hits cell 40") { g9.indexAt(450f, 450f) shouldBe 40 }

        should("a tap on the top edge between the corners hits the first row") { g9.indexAt(450f, 0f) shouldBe 4 }

        should("a tap on the left edge hits the first column") { g9.indexAt(0f, 450f) shouldBe 36 }

        should("a tap on a shared cell edge hits the cell after it") { g9.indexAt(100f, 100f) shouldBe 10 }

        should("a tap just inside the far edge hits the last cell of the row") { g9.indexAt(899.5f, 450f) shouldBe 44 }

        should("a tap outside the board hits nothing") {
            listOf(g9.indexAt(-1f, 450f), g9.indexAt(450f, -1f), g9.indexAt(900f, 450f), g9.indexAt(450f, 900f)) shouldBe
                listOf(null, null, null, null)
        }

        should("a tap on the corner of a square board hits the corner cell") { square4.indexAt(0f, 0f) shouldBe 0 }
    },
)
