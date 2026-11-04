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

import de.lemke.sudoku.data.codec.BoardCodec
import de.lemke.sudoku.data.database.GameEntity
import de.lemke.sudoku.data.database.toEntity
import de.lemke.sudoku.data.database.toLegacyModeLevel
import de.lemke.sudoku.data.database.toSudoku
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
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val BERLIN = ZoneId.of("Europe/Berlin")
private val NEW_YORK = ZoneId.of("America/New_York")

private fun base64Of(board: Board): String = Base64.getEncoder().encodeToString(BoardCodec.encode(board))

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

private fun entryOfGame(sudoku: Sudoku): SudokuBackupEntry = entryOf(sudoku.toEntity(BERLIN), BERLIN).shouldNotBeNull()

class BackupMapperTest : ShouldSpec(
    {
        should("round trip a 9x9 game with notes through its entry") {
            val sudoku = gameOf(SudokuSize.NINE, notes = listOf(1, 4, 9))

            val restored = gameFromBackup(entryOfGame(sudoku), BERLIN).shouldNotBeNull().toSudoku(BERLIN).shouldNotBeNull()

            restored.info shouldBe
                sudoku.info.copy(created = LocalDateTime.of(2026, 5, 2, 8, 0), updated = LocalDateTime.of(2026, 5, 2, 8, 10))
            restored.board shouldBe sudoku.board
        }

        should("carry the stored board bytes into the entry without re-encoding them") {
            val sudoku = gameOf(SudokuSize.NINE, notes = listOf(2, 7))
            val stored = sudoku.toEntity(BERLIN)

            entryOf(stored, BERLIN).shouldNotBeNull().board shouldBe Base64.getEncoder().encodeToString(stored.board)
        }

        should("write the board of the golden 4x4 game as Base64 text") {
            val entry = backupEntry(solvedCells = 0)

            entry.board shouldBe "CAEQBBoIASMjARIwMBIiECEAACQAACEAACMAACQAACM="
            Json.encodeToString(SudokuBackupEntry.serializer(), entry) shouldContain
                "\"board\":\"CAEQBBoIASMjARIwMBIiECEAACQAACEAACMAACQAACM=\""
        }

        should("restore the bytes of an entry unchanged, also when they are not the canonical encoding") {
            val withExtraField = BoardCodec.encode(gameOf(SudokuSize.FOUR).board) + byteArrayOf(0x30, 0x07)
            val entry = backupEntry().copy(board = Base64.getEncoder().encodeToString(withExtraField))

            val restored = gameFromBackup(entry, BERLIN).shouldNotBeNull()

            restored.board.toList() shouldBe withExtraField.toList()
            entryOf(restored, BERLIN).shouldNotBeNull().board shouldBe entry.board
        }

        should("round trip an entry through JSON") {
            val entry = entryOfGame(gameOf(SudokuSize.NINE, notes = listOf(2, 7)))

            val json = Json.encodeToString(SudokuBackupEntry.serializer(), entry)

            Json.decodeFromString(SudokuBackupEntry.serializer(), json) shouldBe entry
        }

        should("keep the games of a large library well below the size limit of a section") {
            val allNotes = (1..9).toList()
            val entries =
                List(4_400) { index ->
                    val size = if (index % 11 == 0) SudokuSize.SIXTEEN else SudokuSize.NINE
                    entryOfGame(gameOf(size, notes = if (index % 2 == 0) allNotes else emptyList(), id = "game-$index"))
                }
            val engineJson = Json { encodeDefaults = true }

            val bytes = engineJson.encodeToString(ListSerializer(SudokuBackupEntry.serializer()), entries).encodeToByteArray().size

            (bytes < 8 * 1024 * 1024) shouldBe true
        }

        should("keep digit 16 of a 16x16 game") {
            val entry = entryOfGame(gameOf(SudokuSize.SIXTEEN))

            gameFromBackup(entry, BERLIN)
                .shouldNotBeNull()
                .toSudoku(BERLIN)
                .shouldNotBeNull()
                .board[15]
                .solution shouldBe 16
        }

        should("write created and updated with the offset of the zone") {
            val entry = entryOfGame(gameOf(SudokuSize.FOUR))

            entry.created shouldBe OffsetDateTime.parse("2026-05-02T08:00:00+02:00")
            entry.updated shouldBe OffsetDateTime.parse("2026-05-02T08:10:00+02:00")
            Json.encodeToString(SudokuBackupEntry.serializer(), entry).contains("\"created\":\"2026-05-02T08:00:00+02:00\"") shouldBe true
        }

        should("write the mode level and the difficulty name") {
            val entry = entryOfGame(gameOf(SudokuSize.FOUR))

            listOf(entry.modeLevel, entry.difficulty) shouldBe listOf(7, "HARD")
        }

        should("convert updated to the zone of the device at the same instant") {
            val entry = backupEntry(updated = "2026-05-02T08:00:00+02:00")

            gameFromBackup(entry, NEW_YORK)
                .shouldNotBeNull()
                .toSudoku(NEW_YORK)
                .shouldNotBeNull()
                .info.updated shouldBe
                LocalDateTime.of(2026, 5, 2, 2, 0)
        }

        should("keep the written local time of created in any zone") {
            val entry = backupEntry(created = "2026-05-02T08:00:00+02:00")

            gameFromBackup(entry, NEW_YORK)
                .shouldNotBeNull()
                .toSudoku(NEW_YORK)
                .shouldNotBeNull()
                .info.created shouldBe
                LocalDateTime.of(2026, 5, 2, 8, 0)
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

        should("refuse an entry without a board when decoding") {
            shouldThrow<SerializationException> { Json.decodeFromString(SudokuBackupEntry.serializer(), """{"id":"x"}""") }
        }

        context("an entry is broken") {
            val nine = base64Of(gameOf(SudokuSize.NINE).board)
            val broken =
                mapOf(
                    "size is unknown" to backupEntry().copy(size = 5),
                    "difficulty name is unknown" to backupEntry().copy(difficulty = "IMPOSSIBLE"),
                    "difficulty is the ordinal" to backupEntry().copy(difficulty = "2"),
                    "mode level is below daily" to backupEntry().copy(modeLevel = -2),
                    "seconds are negative" to backupEntry().copy(seconds = -1),
                    "board is no Base64" to backupEntry().copy(board = "%%%"),
                    "board bytes are no board" to backupEntry().copy(board = Base64.getEncoder().encodeToString(byteArrayOf(1))),
                    "board is empty" to backupEntry().copy(board = ""),
                    "board has another size than the entry" to backupEntry().copy(board = nine),
                )
            broken.forEach { (name, entry) ->
                should("be unreadable when the $name") { gameFromBackup(entry, BERLIN).shouldBeNull() }
            }
        }

        should("write no entry for a header that names no size") {
            val stored = gameOf(SudokuSize.FOUR).toEntity(BERLIN)

            entryOf(GameEntity(stored.header.copy(size = 6), stored.board), BERLIN).shouldBeNull()
        }
    },
)
