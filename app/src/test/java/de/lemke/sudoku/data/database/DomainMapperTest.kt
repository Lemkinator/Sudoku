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

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class DomainMapperTest : ShouldSpec(
    {
        val created = LocalDateTime.of(2026, 1, 1, 12, 0)

        fun sudoku(size: Int = 4) =
            Sudoku.create(
                size = size,
                difficulty = Difficulty.EASY,
                modeLevel = Sudoku.MODE_NORMAL,
                created = created,
                updated = created,
                fields =
                    MutableList(size * size) {
                        Field(position = Position.create(it, size), solution = it % size + 1, notes = mutableListOf('1', '2'))
                    },
            )

        should("fieldFromDb returns null when the stored solution is null") {
            val sudoku = sudoku()
            val fieldDb = fieldToDb(sudoku[0], sudoku.id)

            fieldFromDb(fieldDb.copy(solution = null)).shouldBeNull()
        }

        should("fieldFromDb returns null for a null input") {
            fieldFromDb(null).shouldBeNull()
        }

        should("fieldFromDb round-trips notes as a char list") {
            val sudoku = sudoku()
            val fieldDb = fieldToDb(sudoku[0], sudoku.id)

            val restored = fieldFromDb(fieldDb)

            restored.shouldNotBeNull()
            restored.notes shouldBe sudoku[0].notes
        }

        should("sudokuFromDb returns null when the raw field count does not match size squared") {
            val sudoku = sudoku()
            val sudokuWithFields =
                SudokuWithFields(
                    sudoku = sudokuToDb(sudoku),
                    fields = sudoku.fields.dropLast(1).map { fieldToDb(it, sudoku.id) },
                )

            sudokuFromDb(sudokuWithFields).shouldBeNull()
        }

        should("sudokuFromDb returns null for a null input") {
            sudokuFromDb(null).shouldBeNull()
        }

        should("sudokuFromDb round-trips a valid sudoku") {
            val sudoku = sudoku()
            val sudokuWithFields = SudokuWithFields(sudoku = sudokuToDb(sudoku), fields = sudoku.fields.map { fieldToDb(it, sudoku.id) })

            val restored = sudokuFromDb(sudokuWithFields)

            restored.shouldNotBeNull()
            restored.id shouldBe sudoku.id
            restored.fields.size shouldBe sudoku.fields.size
        }

        should("fieldFromExport returns null when the stored solution is null") {
            val export = fieldToExport(sudoku()[0])
            fieldFromExport(export.copy(solution = null), size = 4).shouldBeNull()
        }

        should("fieldFromExport defaults notes to empty when absent") {
            val export = FieldExport(index = 0, solution = 1, notes = null)

            val field = fieldFromExport(export, size = 4)

            field.shouldNotBeNull()
            field.notes shouldBe mutableListOf()
        }

        should("fieldFromExport restores notes characters when present") {
            val export = FieldExport(index = 0, solution = 1, notes = "12")

            val field = fieldFromExport(export, size = 4)

            field.shouldNotBeNull()
            field.notes shouldBe mutableListOf('1', '2')
        }

        should("fieldToExport blanks out false flags and empty notes to null") {
            val field = Field(position = Position.create(0, 4), solution = 1, given = false, hint = false, notes = mutableListOf())

            val export = fieldToExport(field)

            export.given.shouldBeNull()
            export.hint.shouldBeNull()
            export.notes.shouldBeNull()
        }

        should("fieldToExport keeps true flags and non-empty notes") {
            val field = Field(position = Position.create(0, 4), solution = 1, given = true, hint = true, notes = mutableListOf('3'))

            val export = fieldToExport(field)

            export.given shouldBe true
            export.hint shouldBe true
            export.notes shouldBe "3"
        }

        should("sudokuFromExport returns null when the field count does not match size squared") {
            val sudoku = sudoku()
            val export = sudokuToExport(sudoku).copy(fields = sudoku.fields.dropLast(1).map { fieldToExport(it) })

            sudokuFromExport(export).shouldBeNull()
        }

        should("sudokuToExport and sudokuFromExport round-trip a sudoku with all flags unset") {
            val sudoku = sudoku()

            val restored = sudokuFromExport(sudokuToExport(sudoku))

            restored.shouldNotBeNull()
            restored.id shouldBe sudoku.id
            restored.regionalHighlightingUsed shouldBe false
            restored.checklistNumber shouldBe 0
        }

        should("sudokuToExport and sudokuFromExport round-trip a sudoku with all flags set") {
            val sudoku =
                sudoku().apply {
                    regionalHighlightingUsed = true
                    numberHighlightingUsed = true
                    eraserUsed = true
                    isChecklist = true
                    isReverseChecklist = true
                    checklistNumber = 5
                    hintsUsed = 2
                    notesMade = 3
                    errorsMade = 1
                }

            val restored = sudokuFromExport(sudokuToExport(sudoku))

            restored.shouldNotBeNull()
            restored.regionalHighlightingUsed shouldBe true
            restored.numberHighlightingUsed shouldBe true
            restored.eraserUsed shouldBe true
            restored.isChecklist shouldBe true
            restored.isReverseChecklist shouldBe true
            restored.checklistNumber shouldBe 5
            restored.hintsUsed shouldBe 2
            restored.notesMade shouldBe 3
            restored.errorsMade shouldBe 1
        }
    },
)
