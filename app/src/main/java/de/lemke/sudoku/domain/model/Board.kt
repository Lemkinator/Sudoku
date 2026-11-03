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

private const val PERCENT_SCALE = 100

data class Board(val size: SudokuSize, val cells: List<Cell>) {
    init {
        require(cells.size == size.cellCount) { "Expected ${size.cellCount} cells but got ${cells.size}" }
        val digits = 1..size.value
        cells.forEachIndexed { index, cell ->
            require(cell.solution in digits) { "Cell $index has solution ${cell.solution}" }
            require(cell.value == null || cell.value in digits) { "Cell $index has value ${cell.value}" }
            require(cell.notes.bits ushr size.value == 0) { "Cell $index has notes beyond ${size.value}" }
        }
    }

    operator fun get(index: Int): Cell = cells[index]

    fun position(index: Int): Position = Position.create(index, size)

    fun rowOf(index: Int): List<Int> = rowIndices(position(index).row)

    fun columnOf(index: Int): List<Int> = columnIndices(position(index).column)

    fun blockOf(index: Int): List<Int> = blockIndices(position(index).block)

    fun peers(index: Int): Set<Int> = (rowOf(index) + columnOf(index) + blockOf(index)).toSet() - index

    val completed: Boolean get() = cells.all { it.correct }

    val progress: Int
        get() {
            val playable = cells.count { it.origin != CellOrigin.GIVEN }
            val solved = cells.count { it.origin != CellOrigin.GIVEN && it.correct }
            return if (playable == 0) PERCENT_SCALE else solved * PERCENT_SCALE / playable
        }

    val completedDigits: Set<Int>
        get() =
            cells
                .filter { it.correct }
                .groupingBy { checkNotNull(it.value) }
                .eachCount()
                .filterValues { it >= size.value }
                .keys

    fun isRowCompleted(row: Int): Boolean = rowIndices(row).all { cells[it].correct }

    fun isColumnCompleted(column: Int): Boolean = columnIndices(column).all { cells[it].correct }

    fun isBlockCompleted(block: Int): Boolean = blockIndices(block).all { cells[it].correct }

    fun initial(): Board =
        copy(cells = cells.map { if (it.origin == CellOrigin.GIVEN) it.copy(notes = NoteMask.EMPTY) else Cell(it.solution) })

    fun with(
        index: Int,
        cell: Cell,
    ): Board = copy(cells = cells.toMutableList().also { it[index] = cell })

    private fun rowIndices(row: Int): List<Int> = List(size.value) { row * size.value + it }

    private fun columnIndices(column: Int): List<Int> = List(size.value) { it * size.value + column }

    private fun blockIndices(block: Int): List<Int> {
        val blockSize = size.blockSize
        val firstRow = block / blockSize * blockSize
        val firstColumn = block % blockSize * blockSize
        return List(size.value) { (firstRow + it / blockSize) * size.value + firstColumn + it % blockSize }
    }

    companion object {
        fun of(
            size: SudokuSize,
            solution: List<Int>,
            givens: Set<Int>,
        ): Board =
            Board(
                size,
                solution.mapIndexed { index, digit ->
                    if (index in givens) Cell(digit, digit, CellOrigin.GIVEN) else Cell(digit)
                },
            )
    }
}
