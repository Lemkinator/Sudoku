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
import io.kotest.matchers.shouldBe

class FieldTest : ShouldSpec(
    {
        should("keep the value in the initial field when the field is given") {
            val field =
                Field(
                    position = Position.create(0, 4),
                    solution = 3,
                    value = 3,
                    given = true,
                    hint = true,
                    notes = mutableListOf('1'),
                )

            val initial = field.getInitialField()

            initial.value shouldBe 3
            initial.given shouldBe true
            initial.hint shouldBe false
            initial.notes shouldBe mutableListOf()
        }

        should("clear the value in the initial field when the field is not given") {
            val field =
                Field(
                    position = Position.create(0, 4),
                    solution = 3,
                    value = 3,
                    given = false,
                    hint = true,
                    notes = mutableListOf('1'),
                )

            val initial = field.getInitialField()

            initial.value shouldBe null
            initial.given shouldBe false
            initial.hint shouldBe false
            initial.notes shouldBe mutableListOf()
        }

        should("add a note and sort it into place, returning true") {
            val field = Field(position = Position.create(0, 4), solution = 1, notes = mutableListOf('1', '3'))

            val added = field.toggleNote(2)

            added shouldBe true
            field.notes shouldBe mutableListOf('1', '2', '3')
        }

        should("remove an existing note on toggle, returning false") {
            val field = Field(position = Position.create(0, 4), solution = 1, notes = mutableListOf('1', '2', '3'))

            val added = field.toggleNote(2)

            added shouldBe false
            field.notes shouldBe mutableListOf('1', '3')
        }

        should("return false when toggling a note for null") {
            val field = Field(position = Position.create(0, 4), solution = 1)

            field.toggleNote(null) shouldBe false
            field.notes shouldBe mutableListOf()
        }

        should("remove a note via removeNote") {
            val field = Field(position = Position.create(0, 4), solution = 1, notes = mutableListOf('1', '2'))

            val removed = field.removeNote(1)

            removed shouldBe true
            field.notes shouldBe mutableListOf('2')
        }

        should("report false from removeNote when the note wasn't present") {
            val field = Field(position = Position.create(0, 4), solution = 1, notes = mutableListOf('2'))

            field.removeNote(1) shouldBe false
        }

        should("set the value to the solution and mark hint used") {
            val field = Field(position = Position.create(0, 4), solution = 4)

            field.setHint()

            field.hint shouldBe true
            field.value shouldBe 4
        }

        should("copy with all defaulted params producing an equal but distinct field") {
            val field =
                Field(
                    position = Position.create(0, 4),
                    solution = 2,
                    value = 2,
                    given = true,
                    hint = true,
                    notes = mutableListOf('1'),
                )

            val copy = field.copy()

            copy.position shouldBe field.position
            copy.solution shouldBe field.solution
            copy.value shouldBe field.value
            copy.given shouldBe field.given
            copy.hint shouldBe field.hint
            copy.notes shouldBe field.notes
            (copy.notes === field.notes) shouldBe false
        }

        should("copy with overridden params replacing the originals") {
            val field =
                Field(
                    position = Position.create(0, 4),
                    solution = 2,
                    value = 2,
                    given = true,
                    hint = true,
                    notes = mutableListOf('1'),
                )
            val newPosition = Position.create(1, 4)

            val copy =
                field.copy(
                    position = newPosition,
                    solution = 3,
                    value = 1,
                    notes = mutableListOf('2'),
                    given = false,
                    hint = false,
                )

            copy.position shouldBe newPosition
            copy.solution shouldBe 3
            copy.value shouldBe 1
            copy.given shouldBe false
            copy.hint shouldBe false
            copy.notes shouldBe mutableListOf('2')
        }

        should("keep the value on reset when given") {
            val field =
                Field(
                    position = Position.create(0, 4),
                    solution = 1,
                    value = 1,
                    given = true,
                    hint = true,
                    notes = mutableListOf('2'),
                )

            field.reset()

            field.value shouldBe 1
            field.hint shouldBe false
            field.notes shouldBe mutableListOf()
        }

        should("clear the value on reset when not given") {
            val field =
                Field(
                    position = Position.create(0, 4),
                    solution = 1,
                    value = 1,
                    given = false,
                    hint = true,
                    notes = mutableListOf('2'),
                )

            field.reset()

            field.value shouldBe null
            field.hint shouldBe false
            field.notes shouldBe mutableListOf()
        }

        should("report error when the value is set but doesn't match the solution") {
            val field = Field(position = Position.create(0, 4), solution = 1, value = 2)

            field.error shouldBe true
            field.correct shouldBe false
        }

        should("report correct when the value matches the solution") {
            val field = Field(position = Position.create(0, 4), solution = 1, value = 1)

            field.error shouldBe false
            field.correct shouldBe true
        }

        should("report neither error nor correct when the value is null") {
            val field = Field(position = Position.create(0, 4), solution = 1, value = null)

            field.error shouldBe false
            field.correct shouldBe false
        }
    },
)
