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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import java.util.Timer

class SudokuTest : ShouldSpec(
    {
        should("report 100% progress instead of throwing when every field is given") {
            val sudoku =
                Sudoku.create(
                    size = 4,
                    difficulty = Difficulty.VERY_EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    fields =
                        mutableListOf(
                            Field(position = Position.create(0, 4), solution = 1, value = 1, given = true),
                            Field(position = Position.create(1, 4), solution = 2, value = 2, given = true),
                        ),
                )

            sudoku.progress shouldBe 100
        }

        should("compute progress as the percentage of non-given fields solved correctly") {
            val sudoku =
                Sudoku.create(
                    size = 4,
                    difficulty = Difficulty.VERY_EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    fields =
                        mutableListOf(
                            Field(position = Position.create(0, 4), solution = 1, value = 1, given = true),
                            Field(position = Position.create(1, 4), solution = 2, value = 2, given = false),
                            Field(position = Position.create(2, 4), solution = 3, value = null, given = false),
                        ),
                )

            sudoku.progress shouldBe 50
        }

        should("get and set a field by index") {
            val sudoku = fourByFourSudoku()

            val field = sudoku[5]
            sudoku[5] = field.copy(value = 3)

            sudoku[5].value shouldBe 3
            sudoku[5].position shouldBe Position.create(5, 4)
        }

        should("get and set a field by position") {
            val sudoku = fourByFourSudoku()
            val position = Position.create(6, 4)

            sudoku[position] = sudoku[position].copy(value = 2)

            sudoku[position].value shouldBe 2
            sudoku[position].position shouldBe position
        }

        should("get and set a field by row and column") {
            val sudoku = fourByFourSudoku()

            sudoku[1, 2] = sudoku[1, 2].copy(value = 4)

            sudoku[1, 2].value shouldBe 4
            sudoku[1, 2].position shouldBe Position.create(size = 4, row = 1, column = 2)
        }

        should("consider two sudokus with the same id equal even with differing other fields") {
            val id = SudokuId.generate()
            val first = fourByFourSudoku(sudokuId = id, difficulty = Difficulty.EASY)
            val second = fourByFourSudoku(sudokuId = id, difficulty = Difficulty.HARD)

            (first == second) shouldBe true
            first.hashCode() shouldBe second.hashCode()
        }

        should("consider a sudoku equal to itself") {
            val sudoku = fourByFourSudoku()

            (sudoku == sudoku) shouldBe true
        }

        should("consider sudokus with different ids not equal") {
            val first = fourByFourSudoku(sudokuId = SudokuId.generate())
            val second = fourByFourSudoku(sudokuId = SudokuId.generate())

            (first == second) shouldBe false
        }

        should("never equal an instance of a different class") {
            val sudoku = fourByFourSudoku()

            sudoku.equals("not a sudoku") shouldBe false
        }

        should("never equal null") {
            val sudoku = fourByFourSudoku()
            val other: Any? = null

            sudoku.equals(other) shouldBe false
        }

        should("fall back to the default hint limit for a board size without a dedicated entry") {
            val sudoku =
                Sudoku.create(
                    size = 5,
                    difficulty = Difficulty.VERY_EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    fields = MutableList(25) { index -> Field(position = Position.create(index, 5), solution = index % 5 + 1) },
                )

            sudoku.availableHints shouldBe 3
        }

        should("generate unique ids") {
            val first = SudokuId.generate()
            val second = SudokuId.generate()

            (first == second) shouldBe false
        }

        should("format timeString as hh:mm:ss once an hour has elapsed") {
            val sudoku = fourByFourSudoku().apply { seconds = 3725 }

            sudoku.timeString shouldBe "01:02:05"
        }

        should("format timeString as mm:ss below one hour") {
            val sudoku = fourByFourSudoku().apply { seconds = 125 }

            sudoku.timeString shouldBe "02:05"
        }

        should("contentEquals is true for two sudokus sharing identical content, including the same field list") {
            // Field has no equals(), so contentEquals compares fields by reference.
            val id = SudokuId.generate()
            val now = LocalDateTime.of(2026, 1, 1, 12, 0)
            val fields = MutableList(16) { index -> Field(position = Position.create(index, 4), solution = (index % 4) + 1) }

            fun sudokuAt(now: LocalDateTime) =
                Sudoku.create(
                    sudokuId = id,
                    size = 4,
                    difficulty = Difficulty.VERY_EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    created = now,
                    updated = now,
                    fields = fields,
                )
            val first = sudokuAt(now)
            val second = sudokuAt(now)

            first.contentEquals(second) shouldBe true
        }

        should("contentEquals is false once a tracked field differs") {
            val id = SudokuId.generate()
            val fields = sharedFieldList()
            val first = contentEqualsBaseSudoku(id, fields)
            val second = contentEqualsBaseSudoku(id, fields).apply { numberHighlightingUsed = !numberHighlightingUsed }

            first.numberHighlightingUsed shouldNotBe second.numberHighlightingUsed
            first.contentEquals(second) shouldBe false
        }

        should("contentEquals is false once eraserUsed differs") {
            val id = SudokuId.generate()
            val fields = sharedFieldList()
            val first = contentEqualsBaseSudoku(id, fields)
            val second = contentEqualsBaseSudoku(id, fields).apply { eraserUsed = true }

            first.contentEquals(second) shouldBe false
        }

        should("contentEquals is false once isChecklist differs") {
            val id = SudokuId.generate()
            val fields = sharedFieldList()
            val first = contentEqualsBaseSudoku(id, fields)
            val second = contentEqualsBaseSudoku(id, fields).apply { isChecklist = true }

            first.contentEquals(second) shouldBe false
        }

        should("contentEquals is false once isReverseChecklist differs") {
            val id = SudokuId.generate()
            val fields = sharedFieldList()
            val first = contentEqualsBaseSudoku(id, fields)
            val second = contentEqualsBaseSudoku(id, fields).apply { isReverseChecklist = true }

            first.contentEquals(second) shouldBe false
        }

        should("contentEquals is false once checklistNumber differs") {
            val id = SudokuId.generate()
            val fields = sharedFieldList()
            val first = contentEqualsBaseSudoku(id, fields)
            val second = contentEqualsBaseSudoku(id, fields).apply { checklistNumber = 1 }

            first.contentEquals(second) shouldBe false
        }

        should("contentEquals is false once hintsUsed differs") {
            val id = SudokuId.generate()
            val fields = sharedFieldList()
            val first = contentEqualsBaseSudoku(id, fields)
            val second = contentEqualsBaseSudoku(id, fields).apply { hintsUsed = 1 }

            first.contentEquals(second) shouldBe false
        }

        should("contentEquals is false once notesMade differs") {
            val id = SudokuId.generate()
            val fields = sharedFieldList()
            val first = contentEqualsBaseSudoku(id, fields)
            val second = contentEqualsBaseSudoku(id, fields).apply { notesMade = 1 }

            first.contentEquals(second) shouldBe false
        }

        should("contentEquals is false once errorsMade differs") {
            val id = SudokuId.generate()
            val fields = sharedFieldList()
            val first = contentEqualsBaseSudoku(id, fields)
            val second = contentEqualsBaseSudoku(id, fields).apply { errorsMade = 1 }

            first.contentEquals(second) shouldBe false
        }

        should("contentEquals is false once created differs") {
            val id = SudokuId.generate()
            val fields = sharedFieldList()
            val sameUpdated = LocalDateTime.of(2026, 1, 1, 12, 0)
            val first = contentEqualsBaseSudoku(id, fields, created = LocalDateTime.of(2026, 1, 1, 12, 0), updated = sameUpdated)
            val second = contentEqualsBaseSudoku(id, fields, created = LocalDateTime.of(2026, 1, 2, 12, 0), updated = sameUpdated)

            first.contentEquals(second) shouldBe false
        }

        should("contentEquals is false once updated differs") {
            val id = SudokuId.generate()
            val fields = sharedFieldList()
            val sameCreated = LocalDateTime.of(2026, 1, 1, 12, 0)
            val first = contentEqualsBaseSudoku(id, fields, created = sameCreated, updated = sameCreated)
            val second = contentEqualsBaseSudoku(id, fields, created = sameCreated, updated = sameCreated.plusHours(1))

            first.contentEquals(second) shouldBe false
        }

        should("contentEquals is false once seconds differs") {
            val id = SudokuId.generate()
            val fields = sharedFieldList()
            val first = contentEqualsBaseSudoku(id, fields)
            val second = contentEqualsBaseSudoku(id, fields).apply { seconds = 1 }

            first.contentEquals(second) shouldBe false
        }

        should("reset cancels a running timer") {
            val sudoku = fourByFourSudoku()
            sudoku.startTimer()

            sudoku.reset()

            sudoku.timer shouldBe null
        }

        should("reset still clears progress when no timer is running") {
            val sudoku =
                fourByFourSudoku().apply {
                    seconds = 42
                    errorsMade = 2
                }

            sudoku.reset()

            sudoku.seconds shouldBe 0
            sudoku.errorsMade shouldBe 0
            sudoku.timer shouldBe null
        }

        context("move, setHint and timer") {
            should("return false and not mutate the field when no timer is running") {
                val sudoku = Sudoku.create(size = 4, difficulty = Difficulty.EASY, modeLevel = Sudoku.MODE_NORMAL, fields = testFields())

                sudoku.move(0, 1) shouldBe false

                sudoku[0].value shouldBe null
            }

            should("return false for a given field") {
                val sudoku = runningSudoku(fields = testFields(given = setOf(0)))

                sudoku.move(0, 2) shouldBe false

                sudoku[0].value shouldBe 1
                sudoku.timer?.cancel()
            }

            should("return false for a hint field") {
                val sudoku = runningSudoku(fields = testFields(hints = setOf(0), values = mapOf(0 to 2)))

                sudoku.move(0, 3) shouldBe false

                sudoku[0].value shouldBe 2
                sudoku.timer?.cancel()
            }

            should("set a note, notify the listener and count it in notesMade") {
                val listener = MoveRecordingGameListener()
                val sudoku = runningSudoku(gameListener = listener)

                val result = sudoku.move(0, 1, isNote = true)

                result shouldBe true
                sudoku[0].notes shouldBe mutableListOf('1')
                sudoku.notesMade shouldBe 1
                listener.fieldChanged shouldContain sudoku[0].position
                sudoku.timer?.cancel()
            }

            should("toggle an existing note off without incrementing notesMade") {
                val sudoku = runningSudoku(fields = testFields(notes = mapOf(0 to mutableListOf('1'))))

                val result = sudoku.move(0, 1, isNote = true)

                result shouldBe true
                sudoku[0].notes shouldBe mutableListOf()
                sudoku.notesMade shouldBe 0
                sudoku.timer?.cancel()
            }

            should("clear all notes when noting a null value") {
                val sudoku = runningSudoku(fields = testFields(notes = mapOf(0 to mutableListOf('1', '2'))))

                val result = sudoku.move(0, null, isNote = true)

                result shouldBe true
                sudoku[0].notes shouldBe mutableListOf()
                sudoku.timer?.cancel()
            }

            should("return false when noting a field that already has a value") {
                val sudoku = runningSudoku(fields = testFields(values = mapOf(0 to 2)))

                val result = sudoku.move(0, 3, isNote = true)

                result shouldBe false
                sudoku[0].notes shouldBe mutableListOf()
                sudoku.timer?.cancel()
            }

            should("return false erasing an already empty field with no notes") {
                val sudoku = runningSudoku()

                val result = sudoku.move(0, null)

                result shouldBe false
                sudoku.eraserUsed shouldBe false
                sudoku.timer?.cancel()
            }

            should("erase a field's value and mark the eraser used") {
                val sudoku = runningSudoku(fields = testFields(values = mapOf(0 to 2)))

                val result = sudoku.move(0, null)

                result shouldBe true
                sudoku[0].value shouldBe null
                sudoku.eraserUsed shouldBe true
                sudoku.timer?.cancel()
            }

            should("erase only the notes when the field has no value") {
                val sudoku = runningSudoku(fields = testFields(notes = mapOf(0 to mutableListOf('1', '2'))))

                val result = sudoku.move(0, null)

                result shouldBe true
                sudoku[0].notes shouldBe mutableListOf()
                sudoku.eraserUsed shouldBe true
                sudoku.timer?.cancel()
            }

            should("return false when the field is already correct") {
                val sudoku = runningSudoku(fields = testFields(values = mapOf(0 to 1)))

                sudoku.move(0, 1) shouldBe false
                sudoku.timer?.cancel()
            }

            should("return false when setting the same incorrect value again") {
                val sudoku = runningSudoku(fields = testFields(values = mapOf(0 to 2)))

                sudoku.move(0, 2) shouldBe false
                sudoku.timer?.cancel()
            }

            should("overwrite an existing incorrect value with a different one") {
                val sudoku = runningSudoku(fields = testFields(values = mapOf(0 to 2)))

                val result = sudoku.move(0, 3)

                result shouldBe true
                sudoku[0].value shouldBe 3
                sudoku.timer?.cancel()
            }

            should("set a correct value, notify the listener and clear a matching neighbor note") {
                val listener = MoveRecordingGameListener()
                val sudoku = runningSudoku(fields = testFields(notes = mapOf(1 to mutableListOf('1'))), gameListener = listener)

                val result = sudoku.move(0, 1)

                result shouldBe true
                sudoku[0].value shouldBe 1
                sudoku.errorsMade shouldBe 0
                sudoku[1].notes shouldBe mutableListOf()
                listener.fieldChanged shouldContain sudoku[1].position
                sudoku.timer?.cancel()
            }

            should("set an incorrect value, count an error and leave neighbor notes untouched") {
                val listener = MoveRecordingGameListener()
                val sudoku = runningSudoku(fields = testFields(notes = mapOf(1 to mutableListOf('2'))), gameListener = listener)

                val result = sudoku.move(0, 2)

                result shouldBe true
                sudoku[0].value shouldBe 2
                sudoku.errorsMade shouldBe 1
                listener.errorCount shouldBe 1
                sudoku[1].notes shouldBe mutableListOf('2')
                sudoku.timer?.cancel()
            }

            should("progress and then break the forward checklist, tracking the last value afterwards") {
                val sudoku = runningSudoku()

                sudoku.move(0, 1)
                sudoku.isChecklist shouldBe true
                sudoku.checklistNumber shouldBe 1

                sudoku.move(1, 2)
                sudoku.isChecklist shouldBe true
                sudoku.checklistNumber shouldBe 2

                sudoku.move(6, 1)
                sudoku.isChecklist shouldBe false
                sudoku.checklistNumber shouldBe 1

                sudoku.move(2, 3)
                sudoku.isChecklist shouldBe false
                sudoku.isReverseChecklist shouldBe false
                sudoku.checklistNumber shouldBe 3

                sudoku.timer?.cancel()
            }

            should("ignore a repeated value while a forward checklist is active") {
                val sudoku = runningSudoku()

                sudoku.move(0, 1)
                sudoku.move(6, 1)

                sudoku.isChecklist shouldBe true
                sudoku.checklistNumber shouldBe 1
                sudoku.timer?.cancel()
            }

            should("not start any checklist when the first placed value is neither 1 nor size") {
                val sudoku = runningSudoku()

                sudoku.move(1, 2)

                sudoku.isChecklist shouldBe false
                sudoku.isReverseChecklist shouldBe false
                sudoku.checklistNumber shouldBe 2
                sudoku.timer?.cancel()
            }

            should("progress and then break the reverse checklist") {
                val sudoku = runningSudoku()

                sudoku.move(3, 4)
                sudoku.isReverseChecklist shouldBe true
                sudoku.checklistNumber shouldBe 4

                sudoku.move(1, 2)
                sudoku.isReverseChecklist shouldBe true
                sudoku.checklistNumber shouldBe 2

                sudoku.move(2, 3)
                sudoku.isReverseChecklist shouldBe false
                sudoku.checklistNumber shouldBe 3

                sudoku.timer?.cancel()
            }

            should("ignore a repeated value while a reverse checklist is active") {
                val sudoku = runningSudoku()

                sudoku.move(3, 4)
                sudoku.move(12, 4)

                sudoku.isReverseChecklist shouldBe true
                sudoku.checklistNumber shouldBe 4
                sudoku.timer?.cancel()
            }

            should("complete the board, stop the timer and notify the listener") {
                val listener = MoveRecordingGameListener()
                val given = (0..15).filter { it != 5 }.toSet()
                val sudoku = runningSudoku(fields = testFields(given = given), gameListener = listener)

                val result = sudoku.move(5, solutions[5])

                result shouldBe true
                sudoku.completed shouldBe true
                sudoku.timer shouldBe null
                listener.completed shouldContain sudoku[5].position
            }

            should("do nothing when setting a hint while no timer is running") {
                val sudoku = Sudoku.create(size = 4, difficulty = Difficulty.EASY, modeLevel = Sudoku.MODE_NORMAL, fields = testFields())

                sudoku.setHint(0)

                sudoku.hintsUsed shouldBe 0
                sudoku[0].value shouldBe null
            }

            should("do nothing when setting a hint on a given field") {
                val sudoku = runningSudoku(fields = testFields(given = setOf(0)))

                sudoku.setHint(0)

                sudoku.hintsUsed shouldBe 0
                sudoku.timer?.cancel()
            }

            should("do nothing when setting a hint on a field that already is a hint") {
                val sudoku = runningSudoku(fields = testFields(hints = setOf(0), values = mapOf(0 to 1)))

                sudoku.setHint(0)

                sudoku.hintsUsed shouldBe 0
                sudoku.timer?.cancel()
            }

            should("do nothing when no hint is available") {
                val sudoku = runningSudoku(hintsUsed = 1)

                sudoku.setHint(0)

                sudoku.hintsUsed shouldBe 1
                sudoku[0].value shouldBe null
                sudoku.timer?.cancel()
            }

            should("set the hint value, notify the listener and clear a matching neighbor note") {
                val listener = MoveRecordingGameListener()
                val sudoku = runningSudoku(fields = testFields(notes = mapOf(1 to mutableListOf('1'))), gameListener = listener)

                sudoku.setHint(0)

                sudoku.hintsUsed shouldBe 1
                sudoku[0].value shouldBe 1
                sudoku[0].hint shouldBe true
                sudoku[1].notes shouldBe mutableListOf()
                listener.fieldChanged shouldContain sudoku[0].position
                sudoku.timer?.cancel()
            }

            should("complete the board via a hint, stop the timer and notify the listener") {
                val listener = MoveRecordingGameListener()
                val given = (0..15).filter { it != 5 }.toSet()
                val sudoku = runningSudoku(fields = testFields(given = given), gameListener = listener)

                sudoku.setHint(5)

                sudoku.completed shouldBe true
                sudoku.timer shouldBe null
                listener.completed shouldContain sudoku[5].position
            }

            should("do nothing when starting the timer on an already-completed sudoku") {
                val sudoku = runningSudoku(fields = testFields(given = (0..15).toSet()))
                sudoku.timer?.cancel()
                sudoku.timer = null

                sudoku.startTimer()

                sudoku.timer shouldBe null
            }

            should("cancel a previously running timer before starting a new one") {
                val sudoku = runningSudoku()
                val oldTimer = sudoku.timer

                sudoku.startTimer()

                sudoku.timer shouldNotBe null
                sudoku.timer shouldNotBe oldTimer
                sudoku.timer?.cancel()
            }

            should("tick the running timer, incrementing seconds and notifying the listener") {
                val listener = MoveRecordingGameListener()
                val sudoku = runningSudoku(gameListener = listener)
                sudoku.timer?.cancel()
                val secondsBefore = sudoku.seconds

                sudoku.startTimer(delay = 0L)
                awaitUntil { sudoku.seconds > secondsBefore }

                sudoku.seconds shouldNotBe secondsBefore
                listener.timeChangedCount shouldNotBe 0
                sudoku.timer?.cancel()
            }

            should("tick the running timer without a listener to notify") {
                val sudoku = runningSudoku()
                sudoku.timer?.cancel()
                val secondsBefore = sudoku.seconds

                sudoku.startTimer(delay = 0L)
                awaitUntil { sudoku.seconds > secondsBefore }

                sudoku.seconds shouldNotBe secondsBefore
                sudoku.timer?.cancel()
            }

            should("count an error without notifying when there is no listener") {
                val sudoku = runningSudoku(fields = testFields(notes = mapOf(1 to mutableListOf('2'))))

                val result = sudoku.move(0, 2)

                result shouldBe true
                sudoku.errorsMade shouldBe 1
                sudoku.timer?.cancel()
            }

            should("leave a neighbor's unrelated note untouched when there is no listener") {
                val sudoku = runningSudoku(fields = testFields(notes = mapOf(1 to mutableListOf('2'))))

                val result = sudoku.move(0, 1)

                result shouldBe true
                sudoku[1].notes shouldBe mutableListOf('2')
                sudoku.timer?.cancel()
            }

            should("clear a matching neighbor note without notifying when there is no listener") {
                val sudoku = runningSudoku(fields = testFields(notes = mapOf(1 to mutableListOf('1'))))

                val result = sudoku.move(0, 1)

                result shouldBe true
                sudoku[1].notes shouldBe mutableListOf()
                sudoku.timer?.cancel()
            }

            should("complete the board via a move without notifying when there is no listener") {
                val given = (0..15).filter { it != 5 }.toSet()
                val sudoku = runningSudoku(fields = testFields(given = given))

                val result = sudoku.move(5, solutions[5])

                result shouldBe true
                sudoku.completed shouldBe true
                sudoku.timer shouldBe null
            }

            should("set a hint value without notifying when there is no listener") {
                val sudoku = runningSudoku(fields = testFields(notes = mapOf(1 to mutableListOf('1'))))

                sudoku.setHint(0)

                sudoku.hintsUsed shouldBe 1
                sudoku[0].value shouldBe 1
                sudoku.timer?.cancel()
            }

            should("complete the board via a hint without notifying when there is no listener") {
                val given = (0..15).filter { it != 5 }.toSet()
                val sudoku = runningSudoku(fields = testFields(given = given))

                sudoku.setHint(5)

                sudoku.completed shouldBe true
                sudoku.timer shouldBe null
            }
        }

        context("board queries") {
            should("return the union of row, column and block fields as neighbors of an index") {
                val sudoku = querySudoku()

                val neighbors = sudoku.getNeighbors(0)

                neighbors.map { it.position.index } shouldContainExactlyInAnyOrder listOf(0, 1, 2, 3, 0, 4, 8, 12, 0, 1, 4, 5)
            }

            should("report a row completed when every field in it is correct") {
                val sudoku = querySudoku()

                sudoku.isRowCompleted(0) shouldBe true
            }

            should("report a row not completed when a field in it is wrong or unfilled") {
                val wrong = querySudoku(incorrect = setOf(1))
                val unfilled = querySudoku(unfilled = setOf(2))

                wrong.isRowCompleted(0) shouldBe false
                unfilled.isRowCompleted(0) shouldBe false
            }

            should("report a column completed when every field in it is correct") {
                val sudoku = querySudoku()

                sudoku.isColumnCompleted(0) shouldBe true
            }

            should("report a column not completed when a field in it is wrong or unfilled") {
                val wrong = querySudoku(incorrect = setOf(4))
                val unfilled = querySudoku(unfilled = setOf(8))

                wrong.isColumnCompleted(0) shouldBe false
                unfilled.isColumnCompleted(0) shouldBe false
            }

            should("report a block completed when every field in it is correct") {
                val sudoku = querySudoku()

                sudoku.isBlockCompleted(0) shouldBe true
            }

            should("report a block not completed when a field in it is wrong or unfilled") {
                val wrong = querySudoku(incorrect = setOf(1))
                val unfilled = querySudoku(unfilled = setOf(5))

                wrong.isBlockCompleted(0) shouldBe false
                unfilled.isBlockCompleted(0) shouldBe false
            }

            should("report each number complete only once it appears size times correctly") {
                val sudoku = querySudoku()

                val completedNumbers = sudoku.getCompletedNumbers()

                completedNumbers shouldBe listOf(1 to true, 2 to true, 3 to true, 4 to true)
            }

            should("report a number as not complete while some of its placements are wrong or unfilled") {
                val sudoku = querySudoku(incorrect = setOf(0), unfilled = setOf(6))

                val completedNumbers = sudoku.getCompletedNumbers()

                completedNumbers.first { it.first == 1 }.second shouldBe false
                completedNumbers.first { it.first == 2 }.second shouldBe true
            }
        }
    },
)

private val solutions = listOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 1, 4, 3, 4, 3, 2, 1)

private fun testFields(
    given: Set<Int> = emptySet(),
    values: Map<Int, Int?> = emptyMap(),
    hints: Set<Int> = emptySet(),
    notes: Map<Int, MutableList<Char>> = emptyMap(),
): MutableList<Field> =
    solutions
        .mapIndexed { index, solution ->
            Field(
                position = Position.create(index, 4),
                solution = solution,
                value =
                    if (index in values) {
                        values[index]
                    } else if (index in given) {
                        solution
                    } else {
                        null
                    },
                given = index in given,
                hint = index in hints,
                notes = notes[index] ?: mutableListOf(),
            )
        }.toMutableList()

private fun runningSudoku(
    fields: MutableList<Field> = testFields(),
    gameListener: GameListener? = null,
    hintsUsed: Int = 0,
): Sudoku =
    Sudoku
        .create(
            size = 4,
            difficulty = Difficulty.EASY,
            modeLevel = Sudoku.MODE_NORMAL,
            hintsUsed = hintsUsed,
            fields = fields,
            gameListener = gameListener,
        ).apply { timer = Timer(true) }

private class MoveRecordingGameListener : GameListener {
    val fieldChanged = mutableListOf<Position>()
    val completed = mutableListOf<Position>()
    var errorCount = 0
    var timeChangedCount = 0

    override fun onFieldClicked(position: Position) = Unit

    override fun onFieldChanged(position: Position) {
        fieldChanged += position
    }

    override fun onCompleted(position: Position) {
        completed += position
    }

    override fun onError() {
        errorCount++
    }

    override fun onTimeChanged() {
        timeChangedCount++
    }
}

private fun awaitUntil(
    timeoutMillis: Long = 2000,
    condition: () -> Boolean,
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(10)
    }
    check(condition()) { "condition not met within ${timeoutMillis}ms" }
}

private fun solvedFields(
    incorrect: Set<Int> = emptySet(),
    unfilled: Set<Int> = emptySet(),
): MutableList<Field> =
    solutions
        .mapIndexed { index, solution ->
            Field(
                position = Position.create(index, 4),
                solution = solution,
                value =
                    when (index) {
                        in unfilled -> null
                        in incorrect -> solution + 1
                        else -> solution
                    },
            )
        }.toMutableList()

private fun querySudoku(
    incorrect: Set<Int> = emptySet(),
    unfilled: Set<Int> = emptySet(),
): Sudoku =
    Sudoku.create(
        size = 4,
        difficulty = Difficulty.EASY,
        modeLevel = Sudoku.MODE_NORMAL,
        fields = solvedFields(incorrect, unfilled),
    )

private fun fourByFourSudoku(
    sudokuId: SudokuId = SudokuId.generate(),
    difficulty: Difficulty = Difficulty.VERY_EASY,
): Sudoku =
    Sudoku.create(
        sudokuId = sudokuId,
        size = 4,
        difficulty = difficulty,
        modeLevel = Sudoku.MODE_NORMAL,
        fields = MutableList(16) { index -> Field(position = Position.create(index, 4), solution = (index % 4) + 1) },
    )

private fun sharedFieldList(): MutableList<Field> =
    MutableList(16) { index -> Field(position = Position.create(index, 4), solution = (index % 4) + 1) }

private fun contentEqualsBaseSudoku(
    sudokuId: SudokuId,
    fields: MutableList<Field>,
    created: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
    updated: LocalDateTime = created,
): Sudoku =
    Sudoku.create(
        sudokuId = sudokuId,
        size = 4,
        difficulty = Difficulty.VERY_EASY,
        modeLevel = Sudoku.MODE_NORMAL,
        created = created,
        updated = updated,
        fields = fields,
    )
