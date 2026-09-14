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
        should("derive row, column and block consistently for the first, middle and last index of each size") {
            listOf(4, 9, 16).forEach { size ->
                val blockSize = Math.sqrt(size.toDouble()).toInt()
                listOf(0, size * size / 2, size * size - 1).forEach { index ->
                    val position = Position.create(index, size)

                    position.row shouldBe index / size
                    position.column shouldBe index % size
                    position.block shouldBe (index / size) / blockSize * blockSize + (index % size) / blockSize
                    position.index shouldBe index
                    position.size shouldBe size
                }
            }
        }

        should("derive index and block consistently from row/column for the first, middle and last row-column of each size") {
            listOf(4, 9, 16).forEach { size ->
                val blockSize = Math.sqrt(size.toDouble()).toInt()
                listOf(0, size / 2, size - 1).forEach { row ->
                    listOf(0, size / 2, size - 1).forEach { column ->
                        val position = Position.create(size, row, column)

                        position.index shouldBe row * size + column
                        position.block shouldBe row / blockSize * blockSize + column / blockSize
                        position.row shouldBe row
                        position.column shouldBe column
                    }
                }
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

            (position.equals("not a position")) shouldBe false
        }

        should("equal itself") {
            val position = Position.create(5, 9)

            (position == position) shouldBe true
        }
    },
)
