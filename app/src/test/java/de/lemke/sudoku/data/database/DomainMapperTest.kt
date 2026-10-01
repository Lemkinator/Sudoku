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
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class DomainMapperTest : ShouldSpec(
    {
        val created = LocalDateTime.of(2026, 1, 1, 12, 0)

        fun sudoku(size: SudokuSize = SudokuSize.FOUR) =
            Sudoku.create(
                size = size,
                difficulty = Difficulty.EASY,
                modeLevel = Sudoku.MODE_NORMAL,
                created = created,
                updated = created,
                fields =
                    MutableList(size.cellCount) {
                        Field(position = Position.create(it, size), solution = it % size.value + 1, notes = mutableListOf('1', '2'))
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

        should("fieldFromDb returns null for a game size that is not a supported sudoku size") {
            val sudoku = sudoku()
            val fieldDb = fieldToDb(sudoku[0], sudoku.id)

            fieldFromDb(fieldDb.copy(gameSize = 6)).shouldBeNull()
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

        should("sudokuFromDb filters out a raw field with a null solution before checking the count") {
            val sudoku = sudoku()
            val fields =
                sudoku.fields
                    .map { fieldToDb(it, sudoku.id) }
                    .mapIndexed { index, field -> if (index == 0) field.copy(solution = null) else field }
            val sudokuWithFields = SudokuWithFields(sudoku = sudokuToDb(sudoku), fields = fields)

            sudokuFromDb(sudokuWithFields).shouldBeNull()
        }

        should("sudokuFromDb round-trips a valid sudoku") {
            val sudoku = sudoku()
            val sudokuWithFields = SudokuWithFields(sudoku = sudokuToDb(sudoku), fields = sudoku.fields.map { fieldToDb(it, sudoku.id) })

            val restored = sudokuFromDb(sudokuWithFields)

            restored.shouldNotBeNull()
            restored.id shouldBe sudoku.id
            restored.size shouldBe SudokuSize.FOUR
            restored.fields.size shouldBe 16
        }

        should("sudokuFromDb returns null for a stored size that is not a supported sudoku size") {
            val sudokuDb = sudokuToDb(sudoku()).copy(size = 6)
            val fields =
                List(36) { index ->
                    FieldDb(
                        sudokuId = sudokuDb.id,
                        gameSize = 6,
                        index = index,
                        solution = index % 6 + 1,
                        value = null,
                        given = false,
                        hint = false,
                        notes = "",
                    )
                }

            sudokuFromDb(SudokuWithFields(sudoku = sudokuDb, fields = fields)).shouldBeNull()
        }

        should("sudokuFromDb returns null when a raw field has a game size that is not a supported sudoku size") {
            val sudoku = sudoku()
            val fields =
                sudoku.fields
                    .map { fieldToDb(it, sudoku.id) }
                    .mapIndexed { index, field -> if (index == 0) field.copy(gameSize = 6) else field }

            sudokuFromDb(SudokuWithFields(sudoku = sudokuToDb(sudoku), fields = fields)).shouldBeNull()
        }

        should("fieldFromExport returns null when the stored solution is null") {
            val export = fieldToExport(sudoku()[0])
            fieldFromExport(export.copy(solution = null), size = SudokuSize.FOUR).shouldBeNull()
        }

        should("fieldFromExport defaults notes to empty when absent") {
            val export = FieldExport(index = 0, solution = 1, notes = null)

            val field = fieldFromExport(export, size = SudokuSize.FOUR)

            field.shouldNotBeNull()
            field.notes shouldBe mutableListOf()
        }

        should("fieldFromExport restores notes characters when present") {
            val export = FieldExport(index = 0, solution = 1, notes = "12")

            val field = fieldFromExport(export, size = SudokuSize.FOUR)

            field.shouldNotBeNull()
            field.notes shouldBe mutableListOf('1', '2')
        }

        should("fieldToExport blanks out false flags and empty notes to null") {
            val field =
                Field(
                    position = Position.create(0, SudokuSize.FOUR),
                    solution = 1,
                    given = false,
                    hint = false,
                    notes = mutableListOf(),
                )

            val export = fieldToExport(field)

            export.given.shouldBeNull()
            export.hint.shouldBeNull()
            export.notes.shouldBeNull()
        }

        should("fieldToExport keeps true flags and non-empty notes") {
            val field =
                Field(
                    position = Position.create(0, SudokuSize.FOUR),
                    solution = 1,
                    given = true,
                    hint = true,
                    notes = mutableListOf('3'),
                )

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

        should("sudokuFromExport returns null for a size that is not a supported sudoku size") {
            val export = sudokuToExport(sudoku()).let { it.copy(size = -2, fields = it.fields.take(4)) }

            sudokuFromExport(export).shouldBeNull()
        }

        should("sudokuFromExport returns null for a 6x6 export with a matching field count") {
            val export = sudokuToExport(sudoku()).copy(size = 6, fields = List(36) { FieldExport(index = it, solution = it % 6 + 1) })

            sudokuFromExport(export).shouldBeNull()
        }

        listOf(
            Triple(SudokuSize.FOUR, 16, 3),
            Triple(SudokuSize.NINE, 81, 8),
            Triple(SudokuSize.SIXTEEN, 256, 15),
        ).forEach { (size, fieldCount, lastBlock) ->
            should("sudokuToExport and sudokuFromExport round-trip a $size sudoku") {
                val sudoku = sudoku(size)

                val restored = sudokuFromExport(sudokuToExport(sudoku))

                restored.shouldNotBeNull()
                restored.size shouldBe size
                restored.fields.size shouldBe fieldCount
                val lastPosition = restored.fields.last().position
                lastPosition.block shouldBe lastBlock
                restored.fields.map { it.solution } shouldBe sudoku.fields.map { it.solution }
            }
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
