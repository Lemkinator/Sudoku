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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime

private val SOLUTIONS = listOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 1, 4, 3, 4, 3, 2, 1)
private val CREATED = LocalDateTime.of(2026, 1, 15, 12, 0)
private val DAILY = GameMode.Daily(CREATED.toLocalDate())

private fun gameInfo(
    id: SudokuId = SudokuId("sudoku-test"),
    size: SudokuSize = SudokuSize.FOUR,
    difficulty: Difficulty = Difficulty.EASY,
    mode: GameMode = GameMode.Normal,
    hintsUsed: Int = 0,
): GameInfo =
    GameInfo(id = id, size = size, difficulty = difficulty, mode = mode, created = CREATED, updated = CREATED, hintsUsed = hintsUsed)

private fun fourByFour(
    given: Set<Int> = emptySet(),
    values: Map<Int, Int?> = emptyMap(),
    hints: Set<Int> = emptySet(),
    notes: Map<Int, NoteMask> = emptyMap(),
    info: GameInfo = gameInfo(),
): Sudoku =
    Sudoku(
        info = info,
        board =
            Board(
                SudokuSize.FOUR,
                SOLUTIONS.mapIndexed { index, solution ->
                    Cell(
                        solution = solution,
                        value = if (index in values) values[index] else solution.takeIf { index in given },
                        origin =
                            when (index) {
                                in given -> CellOrigin.GIVEN
                                in hints -> CellOrigin.HINT
                                else -> CellOrigin.PLAYER
                            },
                        notes = notes[index] ?: NoteMask.EMPTY,
                    )
                },
            ),
    )

private fun Sudoku.played(vararg moves: BoardMove): Sudoku = moves.fold(this) { sudoku, move -> checkNotNull(sudoku.play(move)).sudoku }

private fun cellsOf(
    size: SudokuSize,
    solution: Int = 1,
): Board = Board(size, List(size.cellCount) { Cell(solution) })

class SudokuTest : ShouldSpec(
    {
        context("progress") {
            should("report 100% progress instead of throwing when every cell is given") {
                fourByFour(given = (0..15).toSet()).progress shouldBe 100
            }

            should("compute progress as the percentage of non-given cells solved correctly") {
                val sudoku = fourByFour(given = (2..15).toSet(), values = mapOf(0 to 1, 1 to null))

                sudoku.progress shouldBe 50
            }
        }

        context("equality") {
            should("equal a sudoku with the same info and board, with the same hash code") {
                val first = fourByFour(given = setOf(0))
                val second = fourByFour(given = setOf(0))

                first shouldBe second
                first.hashCode() shouldBe second.hashCode()
            }

            val base = fourByFour()
            mapOf(
                "id" to base.copy(info = base.info.copy(id = SudokuId("other"))),
                "difficulty" to base.copy(info = base.info.copy(difficulty = Difficulty.HARD)),
                "mode" to base.copy(info = base.info.copy(mode = DAILY)),
                "regionalHighlightingUsed" to base.copy(info = base.info.copy(regionalHighlightingUsed = true)),
                "numberHighlightingUsed" to base.copy(info = base.info.copy(numberHighlightingUsed = true)),
                "eraserUsed" to base.copy(info = base.info.copy(eraserUsed = true)),
                "isChecklist" to base.copy(info = base.info.copy(isChecklist = true)),
                "isReverseChecklist" to base.copy(info = base.info.copy(isReverseChecklist = true)),
                "checklistNumber" to base.copy(info = base.info.copy(checklistNumber = 2)),
                "hintsUsed" to base.copy(info = base.info.copy(hintsUsed = 1)),
                "notesMade" to base.copy(info = base.info.copy(notesMade = 1)),
                "errorsMade" to base.copy(info = base.info.copy(errorsMade = 1)),
                "created" to base.copy(info = base.info.copy(created = CREATED.plusDays(1))),
                "updated" to base.copy(info = base.info.copy(updated = CREATED.plusDays(1))),
                "seconds" to base.copy(info = base.info.copy(seconds = 1)),
                "a cell value" to base.copy(board = base.board.with(0, base.board[0].copy(value = 1))),
                "a cell note" to base.copy(board = base.board.with(0, base.board[0].copy(notes = NoteMask.of(1)))),
            ).forEach { (name, other) ->
                should("not equal a sudoku that differs in the $name") {
                    base shouldNotBe other
                }
            }

            should("reject a board of another size than the game") {
                shouldThrow<IllegalArgumentException> { Sudoku(gameInfo(size = SudokuSize.FOUR), cellsOf(SudokuSize.NINE)) }
            }
        }

        context("info") {
            should("take the hint limit from the board's sudoku size") {
                val hintLimitBySize = mapOf(SudokuSize.FOUR to 1, SudokuSize.NINE to 3, SudokuSize.SIXTEEN to 8)

                hintLimitBySize.forEach { (size, hintLimit) ->
                    Sudoku(gameInfo(size = size), cellsOf(size)).info.availableHints shouldBe hintLimit
                }
            }

            should("generate unique ids") {
                (SudokuId.generate() == SudokuId.generate()) shouldBe false
            }

            should("format timeString as hh:mm:ss once an hour has elapsed") {
                gameInfo().copy(seconds = 3725).timeString shouldBe "01:02:05"
            }

            should("format timeString as mm:ss below one hour") {
                gameInfo().copy(seconds = 125).timeString shouldBe "02:05"
            }

            should("offer a hint in a normal sudoku below the hint limit") {
                gameInfo(hintsUsed = 0).isHintAvailable shouldBe true
            }

            should("offer no hint once the hint limit is used") {
                gameInfo(hintsUsed = 1).isHintAvailable shouldBe false
            }

            should("offer no hint in a daily or level sudoku") {
                gameInfo(mode = DAILY).isHintAvailable shouldBe false
                gameInfo(mode = GameMode.Level(3)).isHintAvailable shouldBe false
            }

            should("never report the error limit reached when the limit is 0") {
                gameInfo().copy(errorsMade = 1000).errorLimitReached(0) shouldBe false
            }

            should("not report the error limit reached while under the limit") {
                gameInfo().copy(errorsMade = 2).errorLimitReached(3) shouldBe false
            }

            should("report the error limit reached at and above the limit") {
                gameInfo().copy(errorsMade = 3).errorLimitReached(3) shouldBe true
                gameInfo().copy(errorsMade = 4).errorLimitReached(3) shouldBe true
            }
        }

        context("tick") {
            should("add one second and stamp updated") {
                val sudoku = fourByFour()
                val now = CREATED.plusSeconds(1)

                sudoku.tick(now).info shouldBe sudoku.info.copy(seconds = 1, updated = now)
            }

            should("leave the board unchanged") {
                val sudoku = fourByFour(given = setOf(0))

                sudoku.tick(CREATED.plusSeconds(1)).board shouldBe sudoku.board
            }

            should("add a second per tick") {
                val sudoku = fourByFour().tick(CREATED.plusSeconds(1)).tick(CREATED.plusSeconds(2))

                sudoku.info.seconds shouldBe 2
                sudoku.info.updated shouldBe CREATED.plusSeconds(2)
            }
        }

        context("highlighting") {
            should("mark regional highlighting as used and nothing else") {
                fourByFour().withHighlightingUsed(Highlighting.REGIONAL).info shouldBe gameInfo().copy(regionalHighlightingUsed = true)
            }

            should("mark number highlighting as used and nothing else") {
                fourByFour().withHighlightingUsed(Highlighting.NUMBER).info shouldBe gameInfo().copy(numberHighlightingUsed = true)
            }
        }

        context("restarted") {
            val fresh = fourByFour(given = setOf(0, 3))

            should("keep givens and zero counters") {
                val played =
                    fresh
                        .played(BoardMove.ToggleNote(1, 4), BoardMove.Enter(2, 4), BoardMove.Erase(2), BoardMove.RevealHint(1))
                        .withHighlightingUsed(Highlighting.REGIONAL)
                        .withHighlightingUsed(Highlighting.NUMBER)
                        .tick(CREATED.plusSeconds(1))

                played.restarted() shouldBe fresh.copy(info = fresh.info.copy(updated = CREATED.plusSeconds(1)))
            }

            should("turn an imported game into the player's own") {
                val imported = fourByFour(info = gameInfo().copy(source = GameSource.IMPORTED))

                imported.restarted().info.source shouldBe GameSource.OWN
                imported.initialCopy().info.source shouldBe GameSource.OWN
            }

            should("keep the id, the mode and the stamps") {
                val daily = fourByFour(info = gameInfo(mode = DAILY)).tick(CREATED.plusSeconds(5))

                daily.restarted().info shouldBe daily.info.copy(seconds = 0)
            }

            should("drop values, hints and notes but keep given cells") {
                val played = fresh.played(BoardMove.Enter(1, 2), BoardMove.ToggleNote(2, 3))

                played.restarted().board shouldBe fresh.board
            }
        }

        context("initialCopy") {
            val created = LocalDateTime.of(2024, 5, 1, 8, 0)
            val started =
                fourByFour(given = setOf(0), info = gameInfo(mode = GameMode.Daily(created.toLocalDate())).copy(created = created))
                    .played(BoardMove.Enter(1, 2), BoardMove.ToggleNote(2, 3))
                    .withHighlightingUsed(Highlighting.REGIONAL)
                    .tick(created.plusMinutes(3))

            should("build a fresh sudoku with a new id and normal mode") {
                val initial = started.initialCopy()

                initial.info.id shouldNotBe started.info.id
                initial.info.mode shouldBe GameMode.Normal
            }

            should("zero every counter and flag and stamp updated with created") {
                val initial = started.initialCopy()

                initial.info shouldBe
                    GameInfo(
                        id = initial.info.id,
                        size = SudokuSize.FOUR,
                        difficulty = Difficulty.EASY,
                        mode = GameMode.Normal,
                        created = created,
                        updated = created,
                    )
            }

            should("restore the initial board") {
                val initial = started.initialCopy()

                initial.board shouldBe started.board.initial()
                initial.board[0].value shouldBe 1
                initial.board[1].value shouldBe null
            }
        }

        context("copyAsNormal") {
            should("keep the game with a new id and normal mode") {
                val daily =
                    fourByFour(given = setOf(0), info = gameInfo(mode = DAILY))
                        .played(BoardMove.Enter(1, 2), BoardMove.ToggleNote(2, 3))
                        .tick(CREATED.plusSeconds(1))

                val copy = daily.copyAsNormal()

                copy.info shouldBe daily.info.copy(id = copy.info.id, mode = GameMode.Normal)
                copy.info.id shouldNotBe daily.info.id
                copy.board shouldBe daily.board
            }
        }

        context("play") {
            should("count a wrong digit as one error") {
                val moved = fourByFour().play(BoardMove.Enter(2, 4)).shouldNotBeNull()

                moved.sudoku.info.errorsMade shouldBe 1
                moved.sudoku.board[2].value shouldBe 4
                moved.result.error shouldBe true
            }

            should("count no error for the correct digit and hand back the board result") {
                val moved = fourByFour().play(BoardMove.Enter(0, 1)).shouldNotBeNull()

                moved.sudoku.info.errorsMade shouldBe 0
                moved.sudoku.board shouldBe moved.result.board
                moved.result.changed shouldBe setOf(0)
            }

            should("reject a move on a given cell and leave the sudoku as it is") {
                val sudoku = fourByFour(given = setOf(0))

                sudoku.play(BoardMove.Enter(0, 2)) shouldBe null
            }

            should("reject a move on a hint cell") {
                val sudoku = fourByFour(hints = setOf(0), values = mapOf(0 to 2))

                sudoku.play(BoardMove.Enter(0, 3)) shouldBe null
            }

            should("set a note and count it in notesMade") {
                val moved = fourByFour().play(BoardMove.ToggleNote(0, 1)).shouldNotBeNull().sudoku

                moved.board[0].notes shouldBe NoteMask.of(1)
                moved.info.notesMade shouldBe 1
            }

            should("toggle an existing note off without incrementing notesMade") {
                val moved = fourByFour(notes = mapOf(0 to NoteMask.of(1))).play(BoardMove.ToggleNote(0, 1)).shouldNotBeNull().sudoku

                moved.board[0].notes shouldBe NoteMask.EMPTY
                moved.info.notesMade shouldBe 0
            }

            should("clear all notes of a cell without counting a note") {
                val moved = fourByFour(notes = mapOf(0 to NoteMask.of(1, 2))).play(BoardMove.ClearNotes(0)).shouldNotBeNull().sudoku

                moved.board[0].notes shouldBe NoteMask.EMPTY
                moved.info.notesMade shouldBe 0
            }

            should("reject a note on a cell that already has a value") {
                fourByFour(values = mapOf(0 to 2)).play(BoardMove.ToggleNote(0, 3)) shouldBe null
            }

            should("reject erasing an empty cell without notes and keep the eraser unused") {
                fourByFour().play(BoardMove.Erase(0)) shouldBe null
            }

            should("erase a cell's value and mark the eraser used") {
                val moved = fourByFour(values = mapOf(0 to 2)).play(BoardMove.Erase(0)).shouldNotBeNull().sudoku

                moved.board[0].value shouldBe null
                moved.info.eraserUsed shouldBe true
            }

            should("erase only the notes when the cell has no value") {
                val moved = fourByFour(notes = mapOf(0 to NoteMask.of(1, 2))).play(BoardMove.Erase(0)).shouldNotBeNull().sudoku

                moved.board[0].notes shouldBe NoteMask.EMPTY
                moved.info.eraserUsed shouldBe true
            }

            should("reject a digit in a cell that is already correct") {
                fourByFour(values = mapOf(0 to 1)).play(BoardMove.Enter(0, 1)) shouldBe null
            }

            should("reject the same incorrect digit again") {
                fourByFour(values = mapOf(0 to 2)).play(BoardMove.Enter(0, 2)) shouldBe null
            }

            should("overwrite an incorrect digit with a different one") {
                val moved = fourByFour(values = mapOf(0 to 2)).play(BoardMove.Enter(0, 3)).shouldNotBeNull().sudoku

                moved.board[0].value shouldBe 3
            }

            should("clear a matching peer note for a correct digit") {
                val moved = fourByFour(notes = mapOf(1 to NoteMask.of(1))).play(BoardMove.Enter(0, 1)).shouldNotBeNull().sudoku

                moved.board[0].value shouldBe 1
                moved.info.errorsMade shouldBe 0
                moved.board[1].notes shouldBe NoteMask.EMPTY
            }

            should("count an error for a wrong digit and leave peer notes untouched") {
                val moved = fourByFour(notes = mapOf(1 to NoteMask.of(2))).play(BoardMove.Enter(0, 2)).shouldNotBeNull().sudoku

                moved.board[0].value shouldBe 2
                moved.info.errorsMade shouldBe 1
                moved.board[1].notes shouldBe NoteMask.of(2)
            }

            should("complete the sudoku with the last correct digit") {
                val almost = fourByFour(given = (0..15).filter { it != 5 }.toSet())
                almost.completed shouldBe false

                val moved = almost.play(BoardMove.Enter(5, SOLUTIONS[5])).shouldNotBeNull()

                moved.sudoku.completed shouldBe true
                moved.result.completed.board shouldBe true
            }
        }

        context("checklist") {
            should("keep the checklist with number 3 after entering 1, 2 and 3") {
                val entered = fourByFour().played(BoardMove.Enter(0, 1), BoardMove.Enter(1, 2), BoardMove.Enter(2, 3))

                entered.info.isChecklist shouldBe true
                entered.info.checklistNumber shouldBe 3
            }

            should("progress and then break the forward checklist, tracking the last digit afterwards") {
                var sudoku = fourByFour().played(BoardMove.Enter(0, 1))
                sudoku.info.isChecklist shouldBe true
                sudoku.info.checklistNumber shouldBe 1

                sudoku = sudoku.played(BoardMove.Enter(1, 2))
                sudoku.info.isChecklist shouldBe true
                sudoku.info.checklistNumber shouldBe 2

                sudoku = sudoku.played(BoardMove.Enter(6, 1))
                sudoku.info.isChecklist shouldBe false
                sudoku.info.checklistNumber shouldBe 1

                sudoku = sudoku.played(BoardMove.Enter(2, 3))
                sudoku.info.isChecklist shouldBe false
                sudoku.info.isReverseChecklist shouldBe false
                sudoku.info.checklistNumber shouldBe 3
            }

            should("ignore a repeated digit while a forward checklist is active") {
                val sudoku = fourByFour().played(BoardMove.Enter(0, 1), BoardMove.Enter(6, 1))

                sudoku.info.isChecklist shouldBe true
                sudoku.info.checklistNumber shouldBe 1
            }

            should("not start any checklist when the first digit is neither 1 nor the size") {
                val sudoku = fourByFour().played(BoardMove.Enter(1, 2))

                sudoku.info.isChecklist shouldBe false
                sudoku.info.isReverseChecklist shouldBe false
                sudoku.info.checklistNumber shouldBe 2
            }

            should("progress and then break the reverse checklist") {
                var sudoku = fourByFour().played(BoardMove.Enter(3, 4))
                sudoku.info.isReverseChecklist shouldBe true
                sudoku.info.checklistNumber shouldBe 4

                sudoku = sudoku.played(BoardMove.Enter(1, 2))
                sudoku.info.isReverseChecklist shouldBe true
                sudoku.info.checklistNumber shouldBe 2

                sudoku = sudoku.played(BoardMove.Enter(2, 3))
                sudoku.info.isReverseChecklist shouldBe false
                sudoku.info.checklistNumber shouldBe 3
            }

            should("ignore a repeated digit while a reverse checklist is active") {
                val sudoku = fourByFour().played(BoardMove.Enter(3, 4), BoardMove.Enter(12, 4))

                sudoku.info.isReverseChecklist shouldBe true
                sudoku.info.checklistNumber shouldBe 4
            }

            should("follow a wrong digit like a correct one") {
                val sudoku = fourByFour().played(BoardMove.Enter(0, 1), BoardMove.Enter(2, 4))

                sudoku.info.isChecklist shouldBe true
                sudoku.info.checklistNumber shouldBe 4
            }
        }

        context("hints") {
            should("reject a hint in a daily sudoku") {
                fourByFour(info = gameInfo(mode = DAILY)).play(BoardMove.RevealHint(1)) shouldBe null
            }

            should("reject a hint in a level sudoku") {
                fourByFour(info = gameInfo(mode = GameMode.Level(2))).play(BoardMove.RevealHint(1)) shouldBe null
            }

            should("reject a hint on a given cell and count no hint") {
                fourByFour(given = setOf(0)).play(BoardMove.RevealHint(0)) shouldBe null
            }

            should("reject a hint on a cell that already is a hint") {
                fourByFour(hints = setOf(0), values = mapOf(0 to 1)).play(BoardMove.RevealHint(0)) shouldBe null
            }

            should("reject a hint when no hint is available") {
                fourByFour(info = gameInfo(hintsUsed = 1)).play(BoardMove.RevealHint(0)) shouldBe null
            }

            should("reveal the solution, count the hint and clear a matching peer note") {
                val moved = fourByFour(notes = mapOf(1 to NoteMask.of(1))).play(BoardMove.RevealHint(0)).shouldNotBeNull().sudoku

                moved.info.hintsUsed shouldBe 1
                moved.board[0] shouldBe Cell(solution = 1, value = 1, origin = CellOrigin.HINT)
                moved.board[1].notes shouldBe NoteMask.EMPTY
            }

            should("count a hint that reveals a cell with a wrong digit as no error") {
                val moved = fourByFour(values = mapOf(0 to 3)).play(BoardMove.RevealHint(0)).shouldNotBeNull().sudoku

                moved.info.errorsMade shouldBe 0
                moved.board[0].value shouldBe 1
            }

            should("complete the sudoku with the last hint") {
                val almost = fourByFour(given = (0..15).filter { it != 5 }.toSet())

                almost
                    .play(BoardMove.RevealHint(5))
                    .shouldNotBeNull()
                    .sudoku.completed shouldBe true
            }

            should("offer no second hint after using the only hint of a 4x4 sudoku") {
                val moved = fourByFour().play(BoardMove.RevealHint(0)).shouldNotBeNull().sudoku

                moved.info.isHintAvailable shouldBe false
                moved.play(BoardMove.RevealHint(2)) shouldBe null
            }
        }
    },
)
