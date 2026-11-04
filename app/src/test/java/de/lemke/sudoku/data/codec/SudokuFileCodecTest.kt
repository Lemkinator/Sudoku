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
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.NoteMask
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private val SOLUTION_4 = listOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 3, 4, 1, 4, 1, 2, 3)

private const val BOARD_HEX = "08011004" + "1a08" + "0123230112303012" + "2210" + "21000424000021000023000024000023" + "2a02" + "0106"

private const val GOLDEN_FILE =
    "0801" + "120267" + "31" + "1802" + "2003" + "2803" +
        "30809c9f8a9a34" + "3888ea8f8b9a34" + "4078" + "4803" + "5005" + "5801" + "6005" + "6802" + "7224" + BOARD_HEX

private val goldenBoard =
    Board
        .of(SudokuSize.FOUR, SOLUTION_4, setOf(0, 3, 6, 9, 12, 15))
        .with(1, Cell(2, notes = NoteMask.of(2, 3)))
        .with(2, Cell(3, value = 4))

private val golden =
    Sudoku(
        info =
            GameInfo(
                id = SudokuId("g1"),
                size = SudokuSize.FOUR,
                difficulty = Difficulty.HARD,
                mode = GameMode.Level(3),
                created = LocalDateTime.of(2026, 11, 3, 12, 0, 0),
                updated = LocalDateTime.of(2026, 11, 3, 12, 30, 45),
                seconds = 120,
                hintsUsed = 3,
                notesMade = 5,
                errorsMade = 1,
                regionalHighlightingUsed = true,
                eraserUsed = true,
                checklistNumber = 2,
            ),
        board = goldenBoard,
    )

private fun file(vararg fields: Pair<Int, Any>): ByteArray =
    ProtoWriter()
        .apply {
            fields.forEach { (number, value) ->
                when (value) {
                    is Long -> varint(number, value)
                    is String -> string(number, value)
                    is ByteArray -> bytes(number, value)
                }
            }
        }.toByteArray()

@OptIn(ExperimentalStdlibApi::class)
private val goldenBoardBytes = BOARD_HEX.hexToByteArray()

@OptIn(ExperimentalStdlibApi::class)
class SudokuFileCodecTest : ShouldSpec(
    {
        val utc = ZoneOffset.UTC

        fun valid(vararg overrides: Pair<Int, Any>): ByteArray =
            file(*(mapOf<Int, Any>(1 to 1L, 2 to "g1", 14 to goldenBoardBytes) + overrides).toList().toTypedArray())

        should("encode a game to the golden bytes") { SudokuFileCodec.encode(golden, utc).toHexString() shouldBe GOLDEN_FILE }

        should("decode the golden bytes to the game") { SudokuFileCodec.decode(GOLDEN_FILE.hexToByteArray(), utc) shouldBe golden }

        should("start a file with format 1") { SudokuFileCodec.encode(golden, utc).toHexString().take(4) shouldBe "0801" }

        should("omit every zero value of a fresh normal game") {
            val fresh =
                Sudoku(
                    GameInfo(
                        id = SudokuId("a"),
                        size = SudokuSize.FOUR,
                        difficulty = Difficulty.VERY_EASY,
                        mode = GameMode.Normal,
                        created = LocalDateTime.of(1970, 1, 1, 0, 0),
                        updated = LocalDateTime.of(1970, 1, 1, 0, 0),
                    ),
                    Board.of(SudokuSize.FOUR, List(16) { it % 4 + 1 }, emptySet()),
                )

            SudokuFileCodec.encode(fresh, utc).toHexString() shouldBe
                "0801" + "120161" + "7220" + "08011004" + "1a08" + "0123012301230123" + "2210" + "00000000000000000000000000000000"
            SudokuFileCodec.decode(SudokuFileCodec.encode(fresh, utc), utc) shouldBe fresh
        }

        should("round trip a game that uses every flag") {
            val flagged =
                golden.copy(
                    info =
                        golden.info.copy(
                            numberHighlightingUsed = true,
                            isChecklist = true,
                            isReverseChecklist = true,
                            mode = GameMode.Daily,
                            difficulty = Difficulty.EXPERT,
                        ),
                )

            SudokuFileCodec.decode(SudokuFileCodec.encode(flagged, utc), utc) shouldBe flagged
        }

        should("place the stamps on the zone it is read in") {
            val read = SudokuFileCodec.decode(GOLDEN_FILE.hexToByteArray(), ZoneId.of("Europe/Berlin"))

            read?.info?.created shouldBe LocalDateTime.of(2026, 11, 3, 13, 0, 0)
            read?.info?.updated shouldBe LocalDateTime.of(2026, 11, 3, 13, 30, 45)
        }

        should("write stamps as epoch milliseconds of the zone it is written in") {
            val berlin = SudokuFileCodec.encode(golden, ZoneId.of("Europe/Berlin")).toHexString()

            berlin shouldBe GOLDEN_FILE.replace("30809c9f8a9a34", "3080bfc3889a34").replace("3888ea8f8b9a34", "38888db4899a34")
        }

        should("skip a field it does not know") {
            SudokuFileCodec.decode(GOLDEN_FILE.hexToByteArray() + "7801".hexToByteArray(), utc) shouldBe golden
        }

        should("read a file with only the required fields") {
            SudokuFileCodec.decode(valid(), utc)?.info shouldBe
                GameInfo(
                    id = SudokuId("g1"),
                    size = SudokuSize.FOUR,
                    difficulty = Difficulty.VERY_EASY,
                    mode = GameMode.Normal,
                    created = LocalDateTime.of(1970, 1, 1, 0, 0),
                    updated = LocalDateTime.of(1970, 1, 1, 0, 0),
                )
        }

        should("read a message that does not parse as unreadable") {
            SudokuFileCodec.decode(GOLDEN_FILE.hexToByteArray() + 0x80.toByte(), utc).shouldBeNull()
        }

        should("read an empty file as unreadable") { SudokuFileCodec.decode(ByteArray(0), utc).shouldBeNull() }

        should("read the former JSON text as unreadable") { SudokuFileCodec.decode("{\"id\":\"x\"}".toByteArray(), utc).shouldBeNull() }

        should("read format 2 as unreadable") { SudokuFileCodec.decode(valid(1 to 2L), utc).shouldBeNull() }

        should("read a file without an id as unreadable") {
            SudokuFileCodec.decode(file(1 to 1L, 14 to goldenBoardBytes), utc).shouldBeNull()
            SudokuFileCodec.decode(valid(2 to ""), utc).shouldBeNull()
            SudokuFileCodec.decode(valid(2 to 5L), utc).shouldBeNull()
        }

        should("read a file without a board as unreadable") {
            SudokuFileCodec.decode(file(1 to 1L, 2 to "abc"), utc).shouldBeNull()
        }

        should("read a file with a broken board as unreadable") {
            SudokuFileCodec.decode(valid(14 to byteArrayOf(1, 2, 3)), utc).shouldBeNull()
            SudokuFileCodec.decode(valid(14 to 5L), utc).shouldBeNull()
        }

        should("read an unknown mode as unreadable") { SudokuFileCodec.decode(valid(3 to 9L), utc).shouldBeNull() }

        should("read a level game without a level as unreadable") {
            SudokuFileCodec.decode(valid(3 to 2L), utc).shouldBeNull()
            SudokuFileCodec.decode(valid(3 to 2L, 4 to 0L), utc).shouldBeNull()
        }

        should("read an unknown difficulty as unreadable") { SudokuFileCodec.decode(valid(5 to 7L), utc).shouldBeNull() }

        should("read a counter outside the Int range as unreadable") {
            listOf(8, 9, 10, 11, 13, 12).forEach { field ->
                SudokuFileCodec.decode(valid(field to -1L), utc).shouldBeNull()
                SudokuFileCodec.decode(valid(field to (1L shl 32)), utc).shouldBeNull()
            }
        }

        should("read a mode outside the Int range as unreadable") { SudokuFileCodec.decode(valid(3 to (1L shl 32)), utc).shouldBeNull() }
    },
)
