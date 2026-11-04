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

package de.lemke.sudoku.data.database

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
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class DomainMapperTest : ShouldSpec(
    {
        val created = LocalDateTime.of(2026, 1, 1, 12, 0)

        fun sudoku(
            size: SudokuSize = SudokuSize.FOUR,
            mode: GameMode = GameMode.Normal,
        ) = Sudoku(
            info =
                GameInfo(
                    id = SudokuId.generate(),
                    size = size,
                    difficulty = Difficulty.EASY,
                    mode = mode,
                    created = created,
                    updated = created,
                ),
            board = Board(size, List(size.cellCount) { Cell(solution = it % size.value + 1, notes = NoteMask.of(1, 2)) }),
        )

        fun rowsWith(
            sudoku: Sudoku,
            change: (index: Int, field: FieldDb) -> FieldDb,
        ): SudokuWithFields {
            val rows = sudokuWithFieldsToDb(sudoku)
            return rows.copy(fields = rows.fields.mapIndexed(change))
        }

        fun fieldDb(
            gameSize: Int = 4,
            index: Int = 0,
            solution: Int? = 1,
            value: Int? = null,
            given: Boolean = false,
            hint: Boolean = false,
            notes: String = "",
        ) = FieldDb(
            sudokuId = "id",
            gameSize = gameSize,
            index = index,
            solution = solution,
            value = value,
            given = given,
            hint = hint,
            notes = notes,
        )

        context("the legacy mode level") {
            listOf(
                GameMode.Normal to 0,
                GameMode.Daily to -1,
                GameMode.Level(1) to 1,
                GameMode.Level(250) to 250,
            ).forEach { (mode, modeLevel) ->
                should("map $mode to $modeLevel and back") {
                    mode.toLegacyModeLevel() shouldBe modeLevel
                    legacyGameModeOrNull(modeLevel) shouldBe mode
                }
            }

            listOf(-2, -100, Int.MIN_VALUE).forEach { modeLevel ->
                should("name no mode for $modeLevel") {
                    legacyGameModeOrNull(modeLevel).shouldBeNull()
                }
            }
        }

        should("fieldFromDb returns null when the stored solution is null") {
            val fieldDb = sudokuWithFieldsToDb(sudoku()).fields[0]

            fieldFromDb(fieldDb.copy(solution = null)).shouldBeNull()
        }

        should("fieldFromDb returns null for a null input") {
            fieldFromDb(null).shouldBeNull()
        }

        should("fieldFromDb round-trips notes as a digit mask") {
            val sudoku = sudoku()
            val fieldDb = sudokuWithFieldsToDb(sudoku).fields[0]

            val restored = fieldFromDb(fieldDb)

            restored.shouldNotBeNull()
            restored.cell.notes shouldBe sudoku.board[0].notes
        }

        should("fieldFromDb returns null for a game size that is not a supported sudoku size") {
            val fieldDb = sudokuWithFieldsToDb(sudoku()).fields[0]

            fieldFromDb(fieldDb.copy(gameSize = 6)).shouldBeNull()
        }

        should("sudokuFromDb returns null when the raw field count does not match size squared") {
            val rows = sudokuWithFieldsToDb(sudoku())

            sudokuFromDb(rows.copy(fields = rows.fields.dropLast(1))).shouldBeNull()
        }

        listOf(-1, 16).forEach { index ->
            should("fieldFromDb returns null for the out-of-range index $index") {
                val fieldDb = sudokuWithFieldsToDb(sudoku()).fields[0]

                fieldFromDb(fieldDb.copy(index = index)).shouldBeNull()
            }
        }

        should("fieldFromDb keeps the first and last valid index") {
            val fields = sudokuWithFieldsToDb(sudoku()).fields

            fieldFromDb(fields[0])?.index shouldBe 0
            fieldFromDb(fields[15])?.index shouldBe 15
        }

        should("sudokuFromDb returns null when two raw fields share an index") {
            val sudoku = sudoku()

            sudokuFromDb(rowsWith(sudoku) { i, f -> if (i == 5) f.copy(index = 4) else f }).shouldBeNull()
        }

        should("sudokuFromDb returns null when a raw field index is out of range") {
            val sudoku = sudoku()

            sudokuFromDb(rowsWith(sudoku) { i, f -> if (i == 5) f.copy(index = 16) else f }).shouldBeNull()
        }

        should("sudokuFromDb returns null for a null input") {
            sudokuFromDb(null).shouldBeNull()
        }

        should("sudokuFromDb returns null when a raw field has a null solution") {
            val sudoku = sudoku()

            sudokuFromDb(rowsWith(sudoku) { index, field -> if (index == 0) field.copy(solution = null) else field }).shouldBeNull()
        }

        should("sudokuFromDb returns null when an undecodable raw field comes on top of every cell") {
            val rows = sudokuWithFieldsToDb(sudoku())
            val extra = rows.fields[0].copy(index = 16)

            sudokuFromDb(rows.copy(fields = rows.fields + extra)).shouldBeNull()
        }

        should("sudokuFromDb round-trips a valid sudoku") {
            val sudoku = sudoku()

            sudokuFromDb(sudokuWithFieldsToDb(sudoku)) shouldBe sudoku
        }

        should("sudokuFromDb returns null for a stored size that is not a supported sudoku size") {
            val sudokuDb = sudokuToDb(sudoku()).copy(size = 6)
            val fields = List(36) { index -> fieldDb(gameSize = 6, index = index, solution = index % 6 + 1).copy(sudokuId = sudokuDb.id) }

            sudokuFromDb(SudokuWithFields(sudoku = sudokuDb, fields = fields)).shouldBeNull()
        }

        should("sudokuFromDb returns null when a raw field has a game size that is not a supported sudoku size") {
            val sudoku = sudoku()

            sudokuFromDb(rowsWith(sudoku) { index, field -> if (index == 0) field.copy(gameSize = 6) else field }).shouldBeNull()
        }

        should("sudokuFromDb returns null when a raw field is stored for another sudoku size") {
            val sudoku = sudoku(SudokuSize.NINE)
            val foreign = fieldToDb(0, Cell(solution = 1), sudoku.info.id, SudokuSize.SIXTEEN)

            sudokuFromDb(rowsWith(sudoku) { i, f -> if (i == 0) foreign else f }).shouldBeNull()
        }

        should("sudokuFromDb returns null when a raw field index exceeds the sudoku but fits its own game size") {
            val sudoku = sudoku(SudokuSize.NINE)

            sudokuFromDb(rowsWith(sudoku) { i, f -> if (i == 0) f.copy(gameSize = 16, index = 200) else f }).shouldBeNull()
        }

        should("sudokuFromDb restores the cells in cell order from unordered rows") {
            val sudoku = sudoku()
            val rows = sudokuWithFieldsToDb(sudoku)

            val restored = sudokuFromDb(rows.copy(fields = rows.fields.reversed()))

            restored.shouldNotBeNull().board shouldBe sudoku.board
        }

        context("a game of every mode") {
            listOf(GameMode.Normal, GameMode.Daily, GameMode.Level(7)).forEach { mode ->
                should("round-trip $mode through the stored rows") {
                    val sudoku = sudoku(mode = mode)

                    sudokuFromDb(sudokuWithFieldsToDb(sudoku)) shouldBe sudoku
                }
            }

            should("store the legacy mode level and the difficulty code") {
                val base = sudoku(mode = GameMode.Level(7))
                val row = sudokuToDb(base.copy(info = base.info.copy(difficulty = Difficulty.HARD)))

                row.modeLevel shouldBe 7
                row.difficulty shouldBe 3
            }

            should("sudokuFromDb reads an unknown difficulty code as medium") {
                val rows = sudokuWithFieldsToDb(sudoku())

                val restored = sudokuFromDb(rows.copy(sudoku = rows.sudoku.copy(difficulty = 9)))

                restored.shouldNotBeNull().info.difficulty shouldBe Difficulty.MEDIUM
            }

            listOf(0, 5, -1).forEach { value ->
                should("sudokuFromDb returns null for the stored value $value on a 4x4 board") {
                    val sudoku = sudoku()

                    sudokuFromDb(rowsWith(sudoku) { i, f -> if (i == 2) f.copy(value = value) else f }).shouldBeNull()
                }
            }

            listOf(0, 5).forEach { solution ->
                should("sudokuFromDb returns null for the stored solution $solution on a 4x4 board") {
                    val sudoku = sudoku()

                    sudokuFromDb(rowsWith(sudoku) { i, f -> if (i == 2) f.copy(solution = solution) else f }).shouldBeNull()
                }
            }

            should("fieldFromDb never throws for a value or solution outside the board") {
                fieldFromDb(fieldDb(solution = 17)).shouldBeNull()
                fieldFromDb(fieldDb(value = 17)).shouldBeNull()
                fieldFromDb(fieldDb(gameSize = 16, solution = 16, value = 16)).shouldNotBeNull().cell.value shouldBe 16
            }
        }

        context("a stored field becomes a cell") {
            should("read the given flag as a given cell, also when the hint flag is set") {
                fieldFromDb(fieldDb(value = 1, given = true))?.cell?.origin shouldBe CellOrigin.GIVEN
                fieldFromDb(fieldDb(value = 1, given = true, hint = true))?.cell?.origin shouldBe CellOrigin.GIVEN
            }

            should("read the hint flag alone as a hint cell and no flag as a player cell") {
                fieldFromDb(fieldDb(value = 1, hint = true))?.cell?.origin shouldBe CellOrigin.HINT
                fieldFromDb(fieldDb(value = 1))?.cell?.origin shouldBe CellOrigin.PLAYER
            }

            should("give a given cell without value its solution") {
                fieldFromDb(fieldDb(solution = 3, value = null, given = true))?.cell?.value shouldBe 3
            }

            should("keep the value of a given cell and leave a player cell without value empty") {
                fieldFromDb(fieldDb(solution = 3, value = 2, given = true))?.cell?.value shouldBe 2
                fieldFromDb(fieldDb(solution = 3, value = null))?.cell?.value.shouldBeNull()
            }

            should("drop a note that is no digit of the board") {
                fieldFromDb(fieldDb(notes = "1x59A3"))?.cell?.notes shouldBe NoteMask.of(1, 3)
                fieldFromDb(fieldDb(gameSize = 16, solution = 16, notes = "1A"))?.cell?.notes shouldBe NoteMask.of(1, 10)
            }
        }

        context("a cell becomes a stored field") {
            val id = SudokuId("cell")

            should("store the origin as flags") {
                val given = fieldToDb(0, Cell(solution = 1, value = 1, origin = CellOrigin.GIVEN), id, SudokuSize.FOUR)
                val hint = fieldToDb(1, Cell(solution = 2, value = 2, origin = CellOrigin.HINT), id, SudokuSize.FOUR)
                val player = fieldToDb(2, Cell(solution = 3, value = 1), id, SudokuSize.FOUR)

                listOf(given, hint, player).map { it.given to it.hint } shouldBe listOf(true to false, false to true, false to false)
            }

            should("store the notes as ascending sudoku chars") {
                fieldToDb(0, Cell(solution = 1, notes = NoteMask.of(3, 1)), id, SudokuSize.FOUR).notes shouldBe "13"
                fieldToDb(0, Cell(solution = 1, notes = NoteMask.of(16, 10, 2)), id, SudokuSize.SIXTEEN).notes shouldBe "2AG"
                fieldToDb(0, Cell(solution = 1), id, SudokuSize.FOUR).notes shouldBe ""
            }

            should("store the index, the board size, the solution and the value") {
                fieldToDb(7, Cell(solution = 4, value = 2), id, SudokuSize.NINE) shouldBe
                    FieldDb(
                        sudokuId = "cell",
                        gameSize = 9,
                        index = 7,
                        solution = 4,
                        value = 2,
                        given = false,
                        hint = false,
                        notes = "",
                    )
            }
        }
    },
)
