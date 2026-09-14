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

private fun Sudoku.getRow(row: Int): List<Field> = fields.filter { it.position.row == row }

private fun Sudoku.getColumn(column: Int): List<Field> = fields.filter { it.position.column == column }

private fun Sudoku.getBlock(block: Int): List<Field> = fields.filter { it.position.block == block }

internal fun Sudoku.getNeighbors(position: Position): List<Field> =
    getRow(position.row) + getColumn(position.column) + getBlock(position.block)

fun Sudoku.getNeighbors(index: Int): List<Field> = getNeighbors(Position.create(index, size))

fun Sudoku.isRowCompleted(row: Int): Boolean = getRow(row).all { it.correct }

fun Sudoku.isColumnCompleted(column: Int): Boolean = getColumn(column).all { it.correct }

fun Sudoku.isBlockCompleted(block: Int): Boolean = getBlock(block).all { it.correct }

fun Sudoku.getCompletedNumbers(): List<Pair<Int, Boolean>> {
    val numbers = MutableList(size) { 0 }
    fields.forEach { field ->
        if (field.correct) numbers[field.value!! - 1]++
    }
    return numbers.mapIndexed { index, i -> Pair(index + 1, i >= size) }
}
