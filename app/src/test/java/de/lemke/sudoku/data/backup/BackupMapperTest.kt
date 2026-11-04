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

package de.lemke.sudoku.data.backup

import de.lemke.sudoku.data.database.toLegacyModeLevel
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.NoteMask
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private val BERLIN = ZoneId.of("Europe/Berlin")
private val NEW_YORK = ZoneId.of("America/New_York")

private fun boardOf(
    solution: String = FOUR_SOLUTION,
    values: String = "1..4..1..3..4..3",
    givens: List<Int> = listOf(0, 3, 6, 9, 12, 15),
    hints: List<Int> = emptyList(),
    notes: Map<Int, String> = emptyMap(),
) = BoardBackup(solution = solution, values = values, givens = givens, hints = hints, notes = notes)

private fun gameOf(
    size: SudokuSize,
    notes: List<Int> = emptyList(),
    id: String = "nine",
): Sudoku {
    val created = LocalDateTime.of(2026, 5, 2, 8, 0, 0, 123_000_000)
    return Sudoku(
        info =
            GameInfo(
                id = SudokuId(id),
                size = size,
                difficulty = Difficulty.HARD,
                mode = GameMode.Level(7),
                created = created,
                updated = created.plusMinutes(10),
                seconds = 612,
                hintsUsed = 2,
                notesMade = 9,
                errorsMade = 1,
                regionalHighlightingUsed = true,
                numberHighlightingUsed = true,
                eraserUsed = true,
                isChecklist = true,
                isReverseChecklist = true,
                checklistNumber = 4,
            ),
        board =
            Board(
                size,
                List(size.cellCount) { index ->
                    val solution = index % size.value + 1
                    Cell(
                        solution = solution,
                        value = solution.takeIf { index % 3 == 0 || index == 1 },
                        origin =
                            when {
                                index % 3 == 0 -> CellOrigin.GIVEN
                                index == 1 -> CellOrigin.HINT
                                else -> CellOrigin.PLAYER
                            },
                        notes = if (index == 2) NoteMask.of(*notes.toIntArray()) else NoteMask.EMPTY,
                    )
                },
            ),
    )
}

class BackupMapperTest : ShouldSpec(
    {
        should("round trip a 9x9 game with notes through its entry") {
            val sudoku = gameOf(SudokuSize.NINE, notes = listOf(1, 4, 9))

            val entry = sudokuToBackup(sudoku, BERLIN).shouldNotBeNull()
            val restored = sudokuFromBackup(entry, BERLIN).shouldNotBeNull()

            entry.board.notes shouldBe mapOf(2 to "149")
            restored.info shouldBe
                sudoku.info.copy(created = LocalDateTime.of(2026, 5, 2, 8, 0), updated = LocalDateTime.of(2026, 5, 2, 8, 10))
            restored.board shouldBe sudoku.board
        }

        should("round trip an entry with hints and notes through JSON that omits defaults") {
            val entry = sudokuToBackup(gameOf(SudokuSize.NINE, notes = listOf(2, 7)), BERLIN).shouldNotBeNull()

            val json = Json.encodeToString(SudokuBackupEntry.serializer(), entry)

            json.contains("\"hints\":[1]") shouldBe true
            json.contains("\"notes\":{\"2\":\"27\"}") shouldBe true
            Json.decodeFromString(SudokuBackupEntry.serializer(), json) shouldBe entry
        }

        should("leave out the empty hints and notes when defaults are not encoded") {
            val entry = backupEntry()

            val json = Json.encodeToString(SudokuBackupEntry.serializer(), entry)

            json.contains("\"hints\"") shouldBe false
            json.contains("\"notes\"") shouldBe false
            Json.decodeFromString(SudokuBackupEntry.serializer(), json) shouldBe entry
        }

        should("read a board whose json lacks the hints and the notes") {
            val entry = backupEntry()
            val engineJson =
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
            val written = engineJson.encodeToJsonElement(SudokuBackupEntry.serializer(), entry).jsonObject
            val board = written.getValue("board").jsonObject
            val withoutOptional = JsonObject(written + ("board" to JsonObject(board - "hints" - "notes")))

            board.keys shouldBe setOf("solution", "values", "givens", "hints", "notes")
            engineJson.decodeFromJsonElement(SudokuBackupEntry.serializer(), written) shouldBe entry
            engineJson.decodeFromJsonElement(SudokuBackupEntry.serializer(), withoutOptional) shouldBe entry
        }

        should("keep the games of a large library well below the size limit of a section") {
            val allNotes = (1..9).toList()
            val entries =
                List(4_400) { index ->
                    val size = if (index % 11 == 0) SudokuSize.SIXTEEN else SudokuSize.NINE
                    val game = gameOf(size, notes = if (index % 2 == 0) allNotes else emptyList(), id = "game-$index")
                    sudokuToBackup(game, BERLIN).shouldNotBeNull()
                }
            val engineJson = Json { encodeDefaults = true }

            val bytes = engineJson.encodeToString(ListSerializer(SudokuBackupEntry.serializer()), entries).encodeToByteArray().size

            (bytes < 8 * 1024 * 1024) shouldBe true
        }

        should("write a 16x16 game with the letters A to G for the digits 10 to 16") {
            val sudoku = gameOf(SudokuSize.SIXTEEN)

            val entry = sudokuToBackup(sudoku, BERLIN).shouldNotBeNull()

            entry.board.solution shouldBe "123456789ABCDEFG".repeat(16)
            sudokuFromBackup(entry, BERLIN).shouldNotBeNull().board[15].solution shouldBe 16
        }

        should("write created and updated with the offset of the zone") {
            val entry = sudokuToBackup(gameOf(SudokuSize.FOUR), BERLIN).shouldNotBeNull()

            entry.created shouldBe OffsetDateTime.parse("2026-05-02T08:00:00+02:00")
            entry.updated shouldBe OffsetDateTime.parse("2026-05-02T08:10:00+02:00")
            Json.encodeToString(SudokuBackupEntry.serializer(), entry).contains("\"created\":\"2026-05-02T08:00:00+02:00\"") shouldBe true
        }

        should("convert updated to the zone of the device at the same instant") {
            val entry = backupEntry(updated = "2026-05-02T08:00:00+02:00")

            sudokuFromBackup(entry, NEW_YORK).shouldNotBeNull().info.updated shouldBe LocalDateTime.of(2026, 5, 2, 2, 0)
        }

        should("keep the written local time of created in any zone") {
            val entry = backupEntry(created = "2026-05-02T08:00:00+02:00")

            sudokuFromBackup(entry, NEW_YORK).shouldNotBeNull().info.created shouldBe LocalDateTime.of(2026, 5, 2, 8, 0)
        }

        should("map the progress of a game and give a broken one none") {
            progressOf(backupEntry(solvedCells = 7), BERLIN) shouldBe 70
            progressOf(backupEntry(solvedCells = 7).copy(size = 5), BERLIN) shouldBe 0
        }

        should("key a game by id, day, or size and level") {
            backupEntry(id = "a").key() shouldBe SudokuBackupKey.Normal("a")
            backupEntry(modeLevel = GameMode.Daily.toLegacyModeLevel(), created = "2026-05-02T23:30:00-04:00").key() shouldBe
                SudokuBackupKey.Daily(LocalDate.of(2026, 5, 2))
            backupEntry(modeLevel = 3).key() shouldBe SudokuBackupKey.Level(SudokuSize.FOUR, 3)
        }

        should("refuse a stamp without an offset when decoding") {
            val json =
                Json
                    .encodeToString(
                        SudokuBackupEntry.serializer(),
                        backupEntry(),
                    ).replace("2026-05-02T08:00:00+02:00", "2026-05-02T08:00:00")

            shouldThrow<SerializationException> { Json.decodeFromString(SudokuBackupEntry.serializer(), json) }
        }

        should("refuse a board without givens when decoding") {
            shouldThrow<SerializationException> {
                Json.decodeFromString(BoardBackup.serializer(), """{"solution":"1234341223414123","values":"1..4..1..3..4..3"}""")
            }
        }

        should("refuse an entry without a board when decoding") {
            shouldThrow<SerializationException> { Json.decodeFromString(SudokuBackupEntry.serializer(), """{"id":"x"}""") }
        }

        context("an entry is broken") {
            val broken =
                mapOf(
                    "size is unknown" to backupEntry().copy(size = 5),
                    "difficulty name is unknown" to backupEntry().copy(difficulty = "IMPOSSIBLE"),
                    "difficulty is the ordinal" to backupEntry().copy(difficulty = "2"),
                    "mode level is below daily" to backupEntry().copy(modeLevel = -2),
                    "seconds are negative" to backupEntry().copy(seconds = -1),
                    "solution holds 15 chars for 4x4" to backupEntry().run { copy(board = board.copy(solution = FOUR_SOLUTION.drop(1))) },
                    "values hold 17 chars for 4x4" to backupEntry().run { copy(board = board.copy(values = board.values + ".")) },
                    "solution holds 80 chars for 9x9" to backupEntry().copy(size = 9, board = boardOf(solution = "1".repeat(80))),
                    "solution holds a digit beyond the size" to
                        backupEntry().run { copy(board = board.copy(solution = "5" + FOUR_SOLUTION.drop(1))) },
                    "solution holds a char that is no digit" to
                        backupEntry().run { copy(board = board.copy(solution = "x" + FOUR_SOLUTION.drop(1))) },
                    "values hold a digit beyond the size" to backupEntry().run { copy(board = board.copy(values = "1..4..1..3..4..5")) },
                    "a given is listed twice" to backupEntry().run { copy(board = board.copy(givens = board.givens + 0)) },
                    "a cell is given and hint" to backupEntry().run { copy(board = board.copy(hints = listOf(0))) },
                    "a given lies below the board" to backupEntry().run { copy(board = board.copy(givens = board.givens + -1)) },
                    "a note belongs to a cell below the board" to backupEntry().run { copy(board = board.copy(notes = mapOf(-1 to "1"))) },
                    "a given lies outside the board" to backupEntry().run { copy(board = board.copy(givens = board.givens + 16)) },
                    "a hint lies before the board" to backupEntry().run { copy(board = board.copy(hints = listOf(-1))) },
                    "notes belong to a cell outside the board" to backupEntry().run { copy(board = board.copy(notes = mapOf(16 to "1"))) },
                    "a note is no digit of the size" to backupEntry().run { copy(board = board.copy(notes = mapOf(1 to "15"))) },
                )
            broken.forEach { (name, entry) ->
                should("be unreadable when the $name") { sudokuFromBackup(entry, BERLIN).shouldBeNull() }
            }
        }

        should("keep the notes of a cell sorted and without duplicates") {
            val entry = backupEntry().run { copy(board = board.copy(notes = mapOf(1 to "4132", 2 to "3311"))) }

            sudokuFromBackup(entry, BERLIN)
                .shouldNotBeNull()
                .board.cells
                .map { it.notes.digits }
                .slice(1..2) shouldBe
                listOf(listOf(1, 2, 3, 4), listOf(1, 3))
        }

        should("read a hint cell that holds its solution") {
            val entry = backupEntry().run { copy(board = board.copy(values = board.values.replaceRange(10, 11, "4"), hints = listOf(10))) }

            sudokuFromBackup(entry, BERLIN).shouldNotBeNull().board[10].origin shouldBe CellOrigin.HINT
        }

        should("round trip a game whose given and hint cells hold another digit or none") {
            val base = gameOf(SudokuSize.FOUR)
            val sudoku =
                base.copy(
                    board =
                        base.board
                            .with(0, base.board[0].copy(value = 4))
                            .with(1, base.board[1].copy(value = null))
                            .with(3, base.board[3].copy(value = null)),
                )

            val restored = sudokuFromBackup(sudokuToBackup(sudoku, BERLIN).shouldNotBeNull(), BERLIN).shouldNotBeNull()

            restored.board shouldBe sudoku.board
        }

        should("write no entry for a game with negative seconds") {
            val sudoku = gameOf(SudokuSize.FOUR).let { it.copy(info = it.info.copy(seconds = -5)) }

            sudokuToBackup(sudoku, BERLIN).shouldBeNull()
        }
    },
)
