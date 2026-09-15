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

class PositionTest : ShouldSpec(
    {
        should("derive row, column and block for known indices of each size") {
            data class Expected(val size: Int, val index: Int, val row: Int, val column: Int, val block: Int)
            listOf(
                Expected(size = 4, index = 0, row = 0, column = 0, block = 0),
                Expected(size = 4, index = 8, row = 2, column = 0, block = 2),
                Expected(size = 4, index = 15, row = 3, column = 3, block = 3),
                Expected(size = 9, index = 0, row = 0, column = 0, block = 0),
                Expected(size = 9, index = 40, row = 4, column = 4, block = 4),
                Expected(size = 9, index = 80, row = 8, column = 8, block = 8),
                Expected(size = 16, index = 0, row = 0, column = 0, block = 0),
                Expected(size = 16, index = 128, row = 8, column = 0, block = 8),
                Expected(size = 16, index = 255, row = 15, column = 15, block = 15),
            ).forEach { e ->
                val position = Position.create(e.index, e.size)

                position.row shouldBe e.row
                position.column shouldBe e.column
                position.block shouldBe e.block
                position.index shouldBe e.index
                position.size shouldBe e.size
            }
        }

        should("derive index and block for known row/column combinations of each size") {
            data class Expected(val size: Int, val row: Int, val column: Int, val index: Int, val block: Int)
            listOf(
                Expected(size = 4, row = 0, column = 0, index = 0, block = 0),
                Expected(size = 4, row = 2, column = 2, index = 10, block = 3),
                Expected(size = 4, row = 3, column = 3, index = 15, block = 3),
                Expected(size = 4, row = 0, column = 3, index = 3, block = 1),
                Expected(size = 4, row = 3, column = 0, index = 12, block = 2),
                Expected(size = 9, row = 0, column = 0, index = 0, block = 0),
                Expected(size = 9, row = 4, column = 4, index = 40, block = 4),
                Expected(size = 9, row = 8, column = 8, index = 80, block = 8),
                Expected(size = 9, row = 0, column = 8, index = 8, block = 2),
                Expected(size = 9, row = 8, column = 0, index = 72, block = 6),
                Expected(size = 16, row = 0, column = 0, index = 0, block = 0),
                Expected(size = 16, row = 8, column = 8, index = 136, block = 10),
                Expected(size = 16, row = 15, column = 15, index = 255, block = 15),
                Expected(size = 16, row = 0, column = 15, index = 15, block = 3),
                Expected(size = 16, row = 15, column = 0, index = 240, block = 12),
            ).forEach { e ->
                val position = Position.create(e.size, e.row, e.column)

                position.index shouldBe e.index
                position.block shouldBe e.block
                position.row shouldBe e.row
                position.column shouldBe e.column
            }
        }

        should("produce the same position from create(index, size) and create(size, row, column)") {
            listOf(4, 9, 16).forEach { size ->
                (0 until size * size).forEach { index ->
                    val byIndex = Position.create(index, size)
                    val byRowColumn = Position.create(size, byIndex.row, byIndex.column)

                    byRowColumn shouldBe byIndex
                    byRowColumn.block shouldBe byIndex.block
                }
            }
        }

        should("return null from next() at the last index") {
            val last = Position.create(4 * 4 - 1, 4)

            last.next() shouldBe null
        }

        should("return the following position from next() when not at the last index") {
            val position = Position.create(5, 9)

            val next = position.next()

            next shouldBe Position.create(6, 9)
        }

        should("consider positions with the same size and index equal regardless of row/column") {
            val a = Position(size = 9, index = 5, row = 0, column = 5, block = 1)
            val b = Position(size = 9, index = 5, row = 8, column = 8, block = 8)

            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
        }

        should("consider positions with different size or index not equal") {
            val position = Position.create(5, 9)

            (position == Position.create(6, 9)) shouldBe false
            (position == Position.create(5, 16)) shouldBe false
        }

        should("not equal an instance of a different class") {
            val position = Position.create(5, 9)

            position.equals("not a position") shouldBe false
        }

        should("not equal null") {
            val position = Position.create(5, 9)
            val other: Any? = null

            position.equals(other) shouldBe false
        }

        should("equal itself") {
            val position = Position.create(5, 9)

            (position == position) shouldBe true
        }
    },
)
