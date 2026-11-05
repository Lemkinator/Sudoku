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

package de.lemke.sudoku.ui

import de.lemke.sudoku.domain.model.tutorialSudoku
import de.lemke.sudoku.ui.game.BoardInput
import de.lemke.sudoku.ui.game.InputTap
import de.lemke.sudoku.ui.game.Selection
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

private fun at(
    step: IntroStep,
    selection: Selection? = null,
    entries: Map<Int, Int> = emptyMap(),
) = IntroState(step, BoardInput(selection), entries)

class IntroTutorialTest : ShouldSpec(
    {
        should("start at the blocks lesson") { IntroState().step shouldBe IntroStep.BLOCKS }

        should("advance one lesson at a time and stay at the last") {
            val steps = generateSequence(IntroState()) { it.next() }.take(IntroStep.entries.size + 2).map { it.step }.toList()

            steps shouldBe IntroStep.entries + listOf(IntroStep.NOTES, IntroStep.NOTES)
        }

        should("select the demo cell in the select-cell lesson and move on") {
            at(IntroStep.SELECT_CELL).tap(InputTap.Cell(4)) shouldBe at(IntroStep.ENTER_NUMBER, Selection.Cell(4))
        }

        should("ignore another cell in the select-cell lesson") {
            at(IntroStep.SELECT_CELL).tap(InputTap.Cell(0)) shouldBe at(IntroStep.SELECT_CELL)
        }

        should("ignore the demo cell before its lesson") {
            at(IntroStep.BLOCKS).tap(InputTap.Cell(4)) shouldBe at(IntroStep.BLOCKS)
        }

        should("enter 5 into the demo cell in the enter-number lesson and move on") {
            at(IntroStep.ENTER_NUMBER, Selection.Cell(4)).tap(InputTap.Digit(5)) shouldBe
                at(IntroStep.SELECT_NUMBER, entries = mapOf(4 to 5))
        }

        should("ignore another number in the enter-number lesson") {
            at(IntroStep.ENTER_NUMBER, Selection.Cell(4)).tap(InputTap.Digit(3)) shouldBe at(IntroStep.ENTER_NUMBER, Selection.Cell(4))
        }

        should("drop the selection when clearing, without moving on") {
            at(IntroStep.ENTER_NUMBER, Selection.Cell(4)).tap(InputTap.Clear) shouldBe at(IntroStep.ENTER_NUMBER)
        }

        should("select number 2 in the select-number lesson and move on") {
            at(IntroStep.SELECT_NUMBER).tap(InputTap.Digit(2)) shouldBe at(IntroStep.ENTER_ROW_COLUMN, Selection.Digit(2))
        }

        should("ignore another number in the select-number lesson") {
            at(IntroStep.SELECT_NUMBER).tap(InputTap.Digit(3)) shouldBe at(IntroStep.SELECT_NUMBER)
        }

        should("enter the selected number into cell 49 and move on while keeping it selected") {
            at(IntroStep.ENTER_ROW_COLUMN, Selection.Digit(2)).tap(InputTap.Cell(49)) shouldBe
                at(IntroStep.ENTER_BLOCK, Selection.Digit(2), mapOf(49 to 2))
        }

        should("ignore another cell in the row-and-column lesson") {
            at(IntroStep.ENTER_ROW_COLUMN, Selection.Digit(2)).tap(InputTap.Cell(1)) shouldBe
                at(IntroStep.ENTER_ROW_COLUMN, Selection.Digit(2))
        }

        should("ignore a cell in the row-and-column lesson without a selected number") {
            at(IntroStep.ENTER_ROW_COLUMN).tap(InputTap.Cell(49)) shouldBe at(IntroStep.ENTER_ROW_COLUMN)
        }

        should("ignore another control in the row-and-column lesson") {
            at(IntroStep.ENTER_ROW_COLUMN, Selection.Digit(2)).tap(InputTap.Delete) shouldBe
                at(IntroStep.ENTER_ROW_COLUMN, Selection.Digit(2))
        }

        should("enter the selected number into cell 24 and move on") {
            at(IntroStep.ENTER_BLOCK, Selection.Digit(2)).tap(InputTap.Cell(24)) shouldBe
                at(IntroStep.NUMBER_ENTRY, Selection.Digit(2), mapOf(24 to 2))
        }

        should("ignore every tap in a lesson without a task") {
            listOf(IntroStep.SELECTION, IntroStep.NUMBER_ENTRY, IntroStep.NUMBER_ENTRY_DEMO, IntroStep.TOOLS, IntroStep.NOTES).map {
                at(it).tap(InputTap.Cell(4)) == at(it)
            } shouldBe List(5) { true }
        }

        should("toggle the notes mode only in the notes lesson") {
            at(IntroStep.TOOLS).toggleNotes() shouldBe at(IntroStep.TOOLS)
            at(IntroStep.NOTES).toggleNotes().input shouldBe BoardInput(notesMode = true)
            at(IntroStep.NOTES).toggleNotes().toggleNotes().input shouldBe BoardInput()
        }

        should("remove an entered number when erasing it") {
            at(IntroStep.NOTES, entries = mapOf(4 to 5, 49 to 2)).erase(4).entries shouldBe mapOf(49 to 2)
        }

        should("replay the entered numbers onto the tutorial board") {
            val base = tutorialSudoku().board

            val board = at(IntroStep.NOTES, entries = mapOf(4 to 5, 49 to 2)).board(base)

            listOf(board[4].value, board[49].value, board[1].value) shouldBe listOf(5, 2, 1)
        }

        should("keep a number that the board refuses") {
            val base = tutorialSudoku().board

            at(IntroStep.NOTES, entries = mapOf(0 to 9)).board(base) shouldBe base
        }
    },
)
