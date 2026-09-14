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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Timer

// A valid 4x4 solved grid (rows, columns and 2x2 blocks each contain 1..4 exactly once).
private val solutions = intArrayOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 1, 4, 3, 4, 3, 2, 1)

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
}

class SudokuMovesTest : ShouldSpec(
    {
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

            sudoku.move(0, 1) // idx0 solution 1: starts the forward checklist
            sudoku.isChecklist shouldBe true
            sudoku.checklistNumber shouldBe 1

            sudoku.move(1, 2) // idx1 solution 2: continues it
            sudoku.isChecklist shouldBe true
            sudoku.checklistNumber shouldBe 2

            sudoku.move(6, 1) // idx6 solution 1, lower than checklistNumber: breaks it
            sudoku.isChecklist shouldBe false
            sudoku.checklistNumber shouldBe 1

            sudoku.move(2, 3) // idx2 solution 3: no checklist active, just tracks the value
            sudoku.isChecklist shouldBe false
            sudoku.isReverseChecklist shouldBe false
            sudoku.checklistNumber shouldBe 3

            sudoku.timer?.cancel()
        }

        should("ignore a repeated value while a forward checklist is active") {
            val sudoku = runningSudoku()

            sudoku.move(0, 1) // idx0 solution 1
            sudoku.move(6, 1) // idx6 also solution 1: same as checklistNumber, no-op

            sudoku.isChecklist shouldBe true
            sudoku.checklistNumber shouldBe 1
            sudoku.timer?.cancel()
        }

        should("not start any checklist when the first placed value is neither 1 nor size") {
            val sudoku = runningSudoku()

            sudoku.move(1, 2) // idx1 solution 2, first move ever

            sudoku.isChecklist shouldBe false
            sudoku.isReverseChecklist shouldBe false
            sudoku.checklistNumber shouldBe 2
            sudoku.timer?.cancel()
        }

        should("progress and then break the reverse checklist") {
            val sudoku = runningSudoku()

            sudoku.move(3, 4) // idx3 solution 4 == size: starts the reverse checklist
            sudoku.isReverseChecklist shouldBe true
            sudoku.checklistNumber shouldBe 4

            sudoku.move(1, 2) // idx1 solution 2, lower than checklistNumber: continues it
            sudoku.isReverseChecklist shouldBe true
            sudoku.checklistNumber shouldBe 2

            sudoku.move(2, 3) // idx2 solution 3, higher than checklistNumber: breaks it
            sudoku.isReverseChecklist shouldBe false
            sudoku.checklistNumber shouldBe 3

            sudoku.timer?.cancel()
        }

        should("ignore a repeated value while a reverse checklist is active") {
            val sudoku = runningSudoku()

            sudoku.move(3, 4) // idx3 solution 4
            sudoku.move(12, 4) // idx12 also solution 4: same as checklistNumber, no-op

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
            // size 4's hint limit is 1 (see Sudoku.hintLimitBySize), so hintsUsed == 1 exhausts it.
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
    },
)
