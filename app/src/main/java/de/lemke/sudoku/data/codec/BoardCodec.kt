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

package de.lemke.sudoku.data.codec

import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.NoteMask
import de.lemke.sudoku.domain.model.PreservedFields
import de.lemke.sudoku.domain.model.SudokuSize

private const val FIELD_FORMAT = 1
private const val FIELD_SIZE = 2
private const val FIELD_SOLUTION = 3
private const val FIELD_CELLS = 4
private const val FIELD_NOTES = 5
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 0x0f
private const val CELL_VALUE_MASK = 0x1f
private const val CELL_ORIGIN_SHIFT = 5
private const val CELL_ORIGIN_MASK = 0x07

/** The board blob stored in the `game` table: protobuf wire format with fixed field numbers. */
object BoardCodec {
    const val FORMAT = 1

    fun encode(board: Board): ByteArray =
        ProtoWriter()
            .apply {
                varint(FIELD_FORMAT, FORMAT.toLong())
                varint(FIELD_SIZE, board.size.value.toLong())
                bytes(FIELD_SOLUTION, board.cells.map { it.solution - 1 }.toNibbles())
                bytes(FIELD_CELLS, ByteArray(board.cells.size) { board[it].toByte() })
                val notes = board.cells.withIndex().filterNot { it.value.notes.isEmpty() }
                if (notes.isNotEmpty()) {
                    bytes(
                        FIELD_NOTES,
                        ProtoWriter()
                            .apply {
                                notes.forEach {
                                    packedVarint(it.index.toLong())
                                    packedVarint(
                                        it.value.notes.bits
                                            .toLong(),
                                    )
                                }
                            }.toByteArray(),
                    )
                }
                raw(board.preserved.bytes)
            }.toByteArray()

    /** The board of [bytes], or null if they are no board of this format. */
    fun decode(bytes: ByteArray): Board? {
        val fields = ProtoReader.fields(bytes)
        val (known, unknown) = fields.orEmpty().partition { it.number <= FIELD_NOTES }
        val byNumber = known.associateBy { it.number }
        val preserved = PreservedFields(ProtoWriter().apply { unknown.forEach { raw(it.raw) } }.toByteArray())
        return if (byNumber.varint(FIELD_FORMAT) == FORMAT.toLong()) byNumber.toBoardOrNull(preserved) else null
    }

    private fun Cell.toByte(): Byte = ((value ?: 0) or (origin.code shl CELL_ORIGIN_SHIFT)).toByte()

    private fun List<Int>.toNibbles(): ByteArray {
        val padded = if (size % 2 == 0) this else this + 0
        return ByteArray(padded.size / 2) { ((padded[it * 2] shl NIBBLE_BITS) or padded[it * 2 + 1]).toByte() }
    }

    private fun Map<Int, ProtoField>.varint(number: Int): Long? = (this[number] as? VarintField)?.value

    private fun Map<Int, ProtoField>.bytes(number: Int): ByteArray? = (this[number] as? BytesField)?.value

    private fun Map<Int, ProtoField>.toBoardOrNull(preserved: PreservedFields): Board? {
        val size = varint(FIELD_SIZE)?.let { stored -> SudokuSize.entries.firstOrNull { it.value.toLong() == stored } }
        val solution = bytes(FIELD_SOLUTION)
        val cells = bytes(FIELD_CELLS)
        val notes = size?.let { notesOrNull(this[FIELD_NOTES], it) }
        return if (size != null && solution != null && cells != null && notes != null) {
            cellsOrNull(size, solution, cells, notes)?.let { Board(size, it, preserved) }
        } else {
            null
        }
    }

    private fun notesOrNull(
        field: ProtoField?,
        size: SudokuSize,
    ): Map<Int, NoteMask>? =
        when (field) {
            null -> {
                emptyMap()
            }

            is BytesField -> {
                val pairs = ProtoReader.packedVarints(field.value)?.chunked(2)
                if (pairs != null && pairs.areNotesOf(size)) pairs.associate { it[0].toInt() to NoteMask(it[1].toInt()) } else null
            }

            else -> {
                null
            }
        }

    private fun List<List<Long>>.areNotesOf(size: SudokuSize): Boolean {
        for (pair in this) {
            if (pair.size != 2 || pair[0] !in 0L until size.cellCount || pair[1] ushr size.value != 0L) return false
        }
        return true
    }

    private fun cellsOrNull(
        size: SudokuSize,
        solution: ByteArray,
        cells: ByteArray,
        notes: Map<Int, NoteMask>,
    ): List<Cell>? {
        if (solution.size != (size.cellCount + 1) / 2 || cells.size != size.cellCount) return null
        val decoded = List(size.cellCount) { cellOrNull(size, solutionAt(solution, it), cells[it].toInt(), notes[it] ?: NoteMask.EMPTY) }
        return decoded.filterNotNull().takeIf { it.size == decoded.size }
    }

    private fun solutionAt(
        solution: ByteArray,
        index: Int,
    ): Int {
        val pair = solution[index / 2].toInt()
        return (if (index % 2 == 0) pair ushr NIBBLE_BITS else pair) and NIBBLE_MASK
    }

    private fun cellOrNull(
        size: SudokuSize,
        solutionNibble: Int,
        packed: Int,
        notes: NoteMask,
    ): Cell? =
        CellOrigin.fromCodeOrNull((packed ushr CELL_ORIGIN_SHIFT) and CELL_ORIGIN_MASK)?.let { origin ->
            val value = (packed and CELL_VALUE_MASK).takeIf { it != 0 }
            val solution = solutionNibble + 1
            val consistent = origin == CellOrigin.PLAYER || value == solution
            if (solution <= size.value && (value ?: 0) <= size.value && consistent) Cell(solution, value, origin, notes) else null
        }
}
