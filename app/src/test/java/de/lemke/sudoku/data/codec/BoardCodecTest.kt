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
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.random.Random

private val SOLUTION_4 = listOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 3, 4, 1, 4, 1, 2, 3)
private val GIVENS_4 = setOf(0, 3, 6, 9, 12, 15)

private const val SOLUTION_HEX = "0123230112303012"
private const val CELLS_HEX = "21000424000021000023000024000023"
private const val GOLDEN_4X4 = "08011004" + "1a08" + SOLUTION_HEX + "2210" + CELLS_HEX + "2a02" + "0106"

private val golden =
    Board
        .of(SudokuSize.FOUR, SOLUTION_4, GIVENS_4)
        .with(1, Cell(2, notes = NoteMask.of(2, 3)))
        .with(2, Cell(3, value = 4))

private fun fresh(size: SudokuSize): Board = Board.of(size, List(size.cellCount) { it % size.value + 1 }, emptySet())

@OptIn(ExperimentalStdlibApi::class)
private fun message(
    format: Long? = 1,
    size: Long? = 4,
    solution: String? = SOLUTION_HEX,
    cells: String? = CELLS_HEX,
    notes: String? = "0106",
): ByteArray =
    ProtoWriter()
        .apply {
            format?.let { varint(1, it) }
            size?.let { varint(2, it) }
            solution?.let { bytes(3, it.hexToByteArray()) }
            cells?.let { bytes(4, it.hexToByteArray()) }
            notes?.let { bytes(5, it.hexToByteArray()) }
        }.toByteArray()

/** The golden board followed by an unknown field 6 that fills the blob to [total] bytes. */
@OptIn(ExperimentalStdlibApi::class)
private fun goldenPaddedTo(total: Int): ByteArray {
    val golden = GOLDEN_4X4.hexToByteArray()
    val tagAndThreeByteLength = 4
    return ProtoWriter()
        .apply {
            raw(golden)
            bytes(6, ByteArray(total - golden.size - tagAndThreeByteLength))
        }.toByteArray()
}

@OptIn(ExperimentalStdlibApi::class)
class BoardCodecTest : ShouldSpec(
    {
        should("encode a 4x4 board to the golden bytes") { BoardCodec.encode(golden).toHexString() shouldBe GOLDEN_4X4 }

        should("decode the golden bytes to the board") { BoardCodec.decode(GOLDEN_4X4.hexToByteArray()) shouldBe golden }

        should("keep an unknown field 6 through a round trip") {
            val withExtra = GOLDEN_4X4 + "3007"
            BoardCodec.encode(BoardCodec.decode(withExtra.hexToByteArray()).shouldNotBeNull()).toHexString() shouldBe withExtra
        }

        should("move an unknown field read first behind field 5") {
            val board = BoardCodec.decode(("3007" + GOLDEN_4X4).hexToByteArray()).shouldNotBeNull()
            BoardCodec.encode(board).toHexString() shouldBe GOLDEN_4X4 + "3007"
        }

        should("keep unknown fields of every wire type in read order") {
            val extra = "3007" + "3a03616263" + "450a0b0c0d"
            val board = BoardCodec.decode((GOLDEN_4X4 + extra).hexToByteArray()).shouldNotBeNull()
            BoardCodec.encode(board).toHexString() shouldBe GOLDEN_4X4 + extra
        }

        should("decode a blob of exactly the size limit and keep its unknown field") {
            val blob = goldenPaddedTo(BoardCodec.MAX_BYTES)
            blob.size shouldBe 65_536
            BoardCodec.encode(BoardCodec.decode(blob).shouldNotBeNull()) shouldBe blob
        }

        should("leave out the unknown fields that would push a saved board beyond the size limit, so it stays readable") {
            val size = SudokuSize.SIXTEEN
            val crowded = fresh(size)
            val blob =
                ProtoWriter()
                    .apply {
                        raw(BoardCodec.encode(crowded))
                        bytes(6, ByteArray(BoardCodec.MAX_BYTES - BoardCodec.encode(crowded).size - 4))
                    }.toByteArray()
            blob.size shouldBe BoardCodec.MAX_BYTES
            val loaded = BoardCodec.decode(blob).shouldNotBeNull()
            loaded.preserved.bytes.shouldNotBeEmpty()
            val noted = Board(size, loaded.cells.map { it.copy(notes = NoteMask((1 shl size.value) - 1)) }, loaded.preserved)

            val saved = BoardCodec.encode(noted)

            saved.size shouldBeLessThanOrEqual BoardCodec.MAX_BYTES
            BoardCodec.decode(saved) shouldBe Board(size, noted.cells)
        }

        should("read a blob one byte beyond the size limit as unreadable") {
            val blob = goldenPaddedTo(BoardCodec.MAX_BYTES + 1)
            blob.size shouldBe 65_537
            BoardCodec.decode(blob).shouldBeNull()
        }

        should("size fresh boards") {
            SudokuSize.entries.map { BoardCodec.encode(fresh(it)).size } shouldBe listOf(32, 130, 394)
        }

        should("omit the notes field when no cell has notes") {
            BoardCodec.encode(fresh(SudokuSize.FOUR)).toHexString() shouldBe
                "08011004" + "1a08" + "0123012301230123" + "2210" + "00000000000000000000000000000000"
        }

        should("decode a message without a notes field") {
            BoardCodec.decode(message(notes = null)).shouldNotBeNull()[1].notes shouldBe NoteMask.EMPTY
        }

        should("round trip 200 random boards of every size, digit and origin") {
            val random = Random(42)
            repeat(200) {
                val size = SudokuSize.entries[random.nextInt(SudokuSize.entries.size)]
                val board =
                    Board(
                        size,
                        List(size.cellCount) {
                            val solution = random.nextInt(1, size.value + 1)
                            val origin = CellOrigin.entries[random.nextInt(CellOrigin.entries.size)]
                            val value = if (origin == CellOrigin.PLAYER) random.nextInt(0, size.value + 1).takeIf { it != 0 } else solution
                            Cell(solution, value, origin, NoteMask(random.nextInt(0, 1 shl size.value)))
                        },
                    )
                BoardCodec.decode(BoardCodec.encode(board)) shouldBe board
            }
        }

        should("read a format 2 message as unreadable") { BoardCodec.decode(message(format = 2)).shouldBeNull() }

        should("read a message without a format as unreadable") { BoardCodec.decode(message(format = null)).shouldBeNull() }

        should("read JSON text as unreadable") { BoardCodec.decode("{\"id\":1}".toByteArray()).shouldBeNull() }

        should("read an unknown size as unreadable") {
            BoardCodec.decode(message(size = 5)).shouldBeNull()
            BoardCodec.decode(message(size = null)).shouldBeNull()
        }

        should("read a size beyond Int as unreadable") { BoardCodec.decode(message(size = (1L shl 32) + 4)).shouldBeNull() }

        should("read a size sent as bytes as unreadable") {
            val wrongType =
                ProtoWriter()
                    .apply {
                        varint(1, 1)
                        bytes(2, byteArrayOf(4))
                    }.toByteArray()
            BoardCodec.decode(wrongType).shouldBeNull()
        }

        should("read a message without solution or cells as unreadable") {
            BoardCodec.decode(message(solution = null)).shouldBeNull()
            BoardCodec.decode(message(cells = null)).shouldBeNull()
        }

        should("read a solution of the wrong length as unreadable") {
            BoardCodec.decode(message(solution = "01232301123030")).shouldBeNull()
        }

        should("read cells of the wrong length as unreadable") {
            BoardCodec.decode(message(cells = "210004240000210000230000240000")).shouldBeNull()
        }

        should("read a solution digit beyond the size as unreadable") {
            BoardCodec.decode(message(solution = "0123230112303042")).shouldBeNull()
        }

        should("read a cell value beyond the size as unreadable") {
            BoardCodec.decode(message(cells = "21000524000021000023000024000023")).shouldBeNull()
        }

        should("read origin 3 as unreadable") {
            BoardCodec.decode(message(cells = "61000424000021000023000024000023")).shouldBeNull()
        }

        should("read a given cell with a wrong value as unreadable") {
            BoardCodec.decode(message(cells = "22000424000021000023000024000023")).shouldBeNull()
        }

        should("read a given cell without a value as unreadable") {
            BoardCodec.decode(message(cells = "20000424000021000023000024000023")).shouldBeNull()
        }

        should("read a hint cell with a wrong value as unreadable") {
            BoardCodec.decode(message(cells = "21000424000021000023000024000042")).shouldBeNull()
        }

        should("read a hint cell holding its solution as a hint") {
            BoardCodec.decode(message(cells = "21000424000021000023000024000043")).shouldNotBeNull()[15] shouldBe
                Cell(3, 3, CellOrigin.HINT)
        }

        should("decode an empty notes field as a board without notes") {
            BoardCodec.decode(message(notes = "")).shouldNotBeNull()[1].notes shouldBe NoteMask.EMPTY
        }

        should("read notes with an odd count of numbers as unreadable") {
            BoardCodec.decode(message(notes = "010601")).shouldBeNull()
        }

        should("read notes for a cell beyond the board as unreadable") { BoardCodec.decode(message(notes = "1006")).shouldBeNull() }

        should("read notes for a negative cell index as unreadable") {
            BoardCodec.decode(message(notes = "ffffffffffffffffff0106")).shouldBeNull()
        }

        should("read a note mask beyond the size as unreadable") { BoardCodec.decode(message(notes = "0110")).shouldBeNull() }

        should("read truncated notes as unreadable") { BoardCodec.decode(message(notes = "01ac")).shouldBeNull() }

        should("read notes sent as a varint as unreadable") {
            val wrongType = message(notes = null) + ProtoWriter().apply { varint(5, 1) }.toByteArray()
            BoardCodec.decode(wrongType).shouldBeNull()
        }

        should(
            "read a field that does not parse as unreadable",
        ) { BoardCodec.decode(GOLDEN_4X4.hexToByteArray() + 0x80.toByte()).shouldBeNull() }
    },
)
