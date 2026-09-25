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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.util.Timer

private fun testFields(): MutableList<Field> =
    mutableListOf(
        Field(position = Position.create(0, 2), solution = 1, value = 1, given = true),
        Field(position = Position.create(1, 2), solution = 2, value = 2, given = false, hint = true, notes = mutableListOf('3')),
        Field(position = Position.create(2, 2), solution = 1, value = null, given = false),
        Field(position = Position.create(3, 2), solution = 2, value = 2, given = false),
    )

private fun testSudoku(
    sudokuId: SudokuId = SudokuId.generate(),
    modeLevel: Int = Sudoku.MODE_NORMAL,
    created: LocalDateTime = LocalDateTime.of(2024, 1, 1, 12, 0),
): Sudoku =
    Sudoku.create(
        sudokuId = sudokuId,
        size = 2,
        difficulty = Difficulty.EASY,
        modeLevel = modeLevel,
        created = created,
        updated = created.plusDays(1),
        fields = testFields(),
    )

class SudokuExtensionsTest : ShouldSpec(
    {
        val originalLocale = Locale.getDefault()
        beforeSpec { Locale.setDefault(Locale.US) }
        afterSpec { Locale.setDefault(originalLocale) }

        // Field has no equals(), so contentEquals compares fields by reference.
        should("consider sudokus with the same id, matching flags and shared field instances content-equal") {
            val id = SudokuId.generate()
            val created = LocalDateTime.of(2024, 1, 1, 12, 0)
            val fields = testFields()
            val sudoku =
                Sudoku.create(
                    sudokuId = id,
                    size = 2,
                    difficulty = Difficulty.EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    created = created,
                    updated = created,
                    fields = fields,
                )
            val other =
                Sudoku.create(
                    sudokuId = id,
                    size = 2,
                    difficulty = Difficulty.HARD,
                    modeLevel = Sudoku.MODE_DAILY,
                    created = created,
                    updated = created,
                    fields = fields.toMutableList(),
                )

            sudoku.contentEquals(other) shouldBe true
        }

        should("not consider sudokus with different ids content-equal") {
            val created = LocalDateTime.of(2024, 1, 1, 12, 0)
            val fields = testFields()
            val sudoku =
                Sudoku.create(
                    sudokuId = SudokuId.generate(),
                    size = 2,
                    difficulty = Difficulty.EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    created = created,
                    updated = created,
                    fields = fields,
                )
            val other =
                Sudoku.create(
                    sudokuId = SudokuId.generate(),
                    size = 2,
                    difficulty = Difficulty.EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    created = created,
                    updated = created,
                    fields = fields.toMutableList(),
                )

            sudoku.contentEquals(other) shouldBe false
        }

        should("not consider sudokus with different field values content-equal") {
            val id = SudokuId.generate()
            val created = LocalDateTime.of(2024, 1, 1, 12, 0)
            val fields = testFields()
            val sudoku =
                Sudoku.create(
                    sudokuId = id,
                    size = 2,
                    difficulty = Difficulty.EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    created = created,
                    updated = created,
                    fields = fields,
                )
            val other =
                Sudoku.create(
                    sudokuId = id,
                    size = 2,
                    difficulty = Difficulty.EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    created = created,
                    updated = created,
                    fields = fields.toMutableList(),
                )
            other[0] = other[0].copy(value = 2)

            other[0].value shouldBe 2
            sudoku[0].value shouldNotBe other[0].value
            sudoku.contentEquals(other) shouldBe false
        }

        should("not consider sudokus with a different flag content-equal") {
            val id = SudokuId.generate()
            val created = LocalDateTime.of(2024, 1, 1, 12, 0)
            val fields = testFields()
            val sudoku =
                Sudoku.create(
                    sudokuId = id,
                    size = 2,
                    difficulty = Difficulty.EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    created = created,
                    updated = created,
                    regionalHighlightingUsed = false,
                    fields = fields,
                )
            val other =
                Sudoku.create(
                    sudokuId = id,
                    size = 2,
                    difficulty = Difficulty.EASY,
                    modeLevel = Sudoku.MODE_NORMAL,
                    created = created,
                    updated = created,
                    regionalHighlightingUsed = true,
                    fields = fields.toMutableList(),
                )

            sudoku.contentEquals(other) shouldBe false
        }

        should("copy with defaults producing an equal-valued but instance-distinct sudoku") {
            val sudoku = testSudoku()

            val copy = sudoku.copy()

            copy.id shouldBe sudoku.id
            copy.fields.indices.forEach { i ->
                copy.fields[i].value shouldBe sudoku.fields[i].value
                copy.fields[i].given shouldBe sudoku.fields[i].given
            }
            (copy.fields === sudoku.fields) shouldBe false
            (copy.fields[0] === sudoku.fields[0]) shouldBe false
            sudoku.contentEquals(copy) shouldBe false
        }

        should("copy with overrides replacing the originals") {
            val sudoku = testSudoku()
            val newId = SudokuId.generate()

            val copy =
                sudoku.copy(
                    sudokuId = newId,
                    difficulty = Difficulty.HARD,
                    modeLevel = Sudoku.MODE_DAILY,
                    regionalHighlightingUsed = true,
                    numberHighlightingUsed = true,
                    eraserUsed = true,
                    isChecklist = true,
                    isReverseChecklist = true,
                    checklistNumber = 2,
                    hintsUsed = 1,
                    notesMade = 2,
                    errorsMade = 3,
                    seconds = 42,
                )

            copy.id shouldBe newId
            copy.difficulty shouldBe Difficulty.HARD
            copy.modeLevel shouldBe Sudoku.MODE_DAILY
            copy.regionalHighlightingUsed shouldBe true
            copy.numberHighlightingUsed shouldBe true
            copy.eraserUsed shouldBe true
            copy.isChecklist shouldBe true
            copy.isReverseChecklist shouldBe true
            copy.checklistNumber shouldBe 2
            copy.hintsUsed shouldBe 1
            copy.notesMade shouldBe 2
            copy.errorsMade shouldBe 3
            copy.seconds shouldBe 42
        }

        should("never report the error limit reached when the limit is 0") {
            val sudoku = testSudoku().apply { errorsMade = 1000 }

            sudoku.errorLimitReached(0) shouldBe false
        }

        should("not report the error limit reached while under the limit") {
            val sudoku = testSudoku().apply { errorsMade = 2 }

            sudoku.errorLimitReached(3) shouldBe false
        }

        should("report the error limit reached exactly at the limit") {
            val sudoku = testSudoku().apply { errorsMade = 3 }

            sudoku.errorLimitReached(3) shouldBe true
        }

        should("report the error limit reached above the limit") {
            val sudoku = testSudoku().apply { errorsMade = 4 }

            sudoku.errorLimitReached(3) shouldBe true
        }

        should("build a fresh initial sudoku with a new id and normal mode") {
            val created = LocalDateTime.of(2024, 5, 1, 8, 0)
            val sudoku =
                testSudoku(modeLevel = Sudoku.MODE_DAILY, created = created).apply {
                    regionalHighlightingUsed = true
                    numberHighlightingUsed = true
                    eraserUsed = true
                    isChecklist = true
                    isReverseChecklist = true
                    checklistNumber = 2
                    hintsUsed = 1
                    notesMade = 2
                    errorsMade = 3
                    seconds = 99
                    timer = Timer()
                    gameListener = RecordingGameListener()
                }

            val initial = sudoku.getInitialSudoku()

            (initial.id == sudoku.id) shouldBe false
            initial.modeLevel shouldBe Sudoku.MODE_NORMAL
            initial.regionalHighlightingUsed shouldBe false
            initial.numberHighlightingUsed shouldBe false
            initial.eraserUsed shouldBe false
            initial.isChecklist shouldBe false
            initial.isReverseChecklist shouldBe false
            initial.checklistNumber shouldBe 0
            initial.hintsUsed shouldBe 0
            initial.notesMade shouldBe 0
            initial.errorsMade shouldBe 0
            initial.seconds shouldBe 0
            initial.timer shouldBe null
            initial.gameListener shouldBe null
            initial.created shouldBe created
            initial.updated shouldBe created
            initial[0].value shouldBe 1
            initial[1].value shouldBe null
            initial[1].hint shouldBe false
            initial[1].notes shouldBe mutableListOf()

            sudoku.stopTimer()
        }

        should("reset every mutable field, cancel a running timer and clear the game listener") {
            val trackingTimer = TrackingTimer()
            val sudoku =
                testSudoku().apply {
                    regionalHighlightingUsed = true
                    numberHighlightingUsed = true
                    eraserUsed = true
                    isChecklist = true
                    isReverseChecklist = true
                    checklistNumber = 3
                    hintsUsed = 2
                    notesMade = 5
                    errorsMade = 1
                    seconds = 77
                    timer = trackingTimer
                    gameListener = RecordingGameListener()
                }

            sudoku.reset()

            sudoku.regionalHighlightingUsed shouldBe false
            sudoku.numberHighlightingUsed shouldBe false
            sudoku.eraserUsed shouldBe false
            sudoku.isChecklist shouldBe false
            sudoku.isReverseChecklist shouldBe false
            sudoku.checklistNumber shouldBe 0
            sudoku.hintsUsed shouldBe 0
            sudoku.notesMade shouldBe 0
            sudoku.errorsMade shouldBe 0
            sudoku.seconds shouldBe 0
            sudoku.timer shouldBe null
            sudoku.gameListener shouldBe null
            trackingTimer.cancelled shouldBe true
            sudoku[0].value shouldBe 1
            sudoku[1].value shouldBe null
            sudoku[1].hint shouldBe false
            sudoku[1].notes shouldBe mutableListOf()
        }

        should("map null to a null sudoku string and char") {
            (null as Int?).toSudokuString().shouldBeNull()
            (null as Int?).toSudokuChar().shouldBeNull()
        }

        should("map standard digits 1..9 to their digit string and char") {
            1.toSudokuString() shouldBe "1"
            9.toSudokuString() shouldBe "9"
            1.toSudokuChar() shouldBe '1'
            9.toSudokuChar() shouldBe '9'
        }

        should("map large digits 10..16 to letters A..G") {
            10.toSudokuString() shouldBe "A"
            16.toSudokuString() shouldBe "G"
            10.toSudokuChar() shouldBe 'A'
            16.toSudokuChar() shouldBe 'G'
        }

        should("map out-of-range values to null") {
            0.toSudokuString().shouldBeNull()
            17.toSudokuString().shouldBeNull()
            (-1).toSudokuString().shouldBeNull()
            0.toSudokuChar().shouldBeNull()
            17.toSudokuChar().shouldBeNull()
            (-1).toSudokuChar().shouldBeNull()
        }

        should("format monthAndYear as full month name and year") {
            LocalDateTime.of(2024, 3, 15, 10, 30).monthAndYear shouldBe "March 2024"
        }

        should("format dateFormatShort using the short localized date style") {
            LocalDateTime.of(2024, 3, 15, 10, 30).dateFormatShort shouldBe "3/15/24"
        }

        should("format LocalDate formatFull using the full localized date style") {
            LocalDate.of(2024, 3, 15).formatFull shouldBe "Friday, March 15, 2024"
        }

        should("format LocalDateTime formatFull using the full date and medium time style") {
            val formatted = LocalDateTime.of(2024, 3, 15, 10, 30).formatFull

            formatted.contains("Friday, March 15, 2024") shouldBe true
            formatted.contains("10:30") shouldBe true
        }
    },
)

private class TrackingTimer : Timer() {
    var cancelled = false

    override fun cancel() {
        cancelled = true
        super.cancel()
    }
}

private class RecordingGameListener : GameListener {
    override fun onFieldClicked(position: Position) = Unit

    override fun onFieldChanged(position: Position) = Unit

    override fun onCompleted(position: Position) = Unit

    override fun onError() = Unit

    override fun onTimeChanged() = Unit
}
