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

package de.lemke.sudoku.ui.utils

import de.lemke.sudoku.domain.model.SudokuSize

/** The selection indices of a [size] board: its fields first, then the number buttons, the delete button and the hint button. */
@JvmInline
value class SelectionIndices(val size: SudokuSize) {
    val fields: IntRange get() = 0 until size.cellCount
    val numberButtons: IntRange get() = size.cellCount until size.cellCount + size.value
    val deleteButton: Int get() = numberButtons.last + 1
    val hintButton: Int get() = deleteButton + 1
    val buttons: IntRange get() = numberButtons.first..hintButton

    /** The kind of control that [index] selects. */
    fun targetOf(index: Int): SelectionTarget {
        require(index in fields.first..hintButton) { "Selection index $index is outside 0..$hintButton" }
        return when {
            index <= fields.last -> SelectionTarget.FIELD
            index <= numberButtons.last -> SelectionTarget.NUMBER_BUTTON
            index == deleteButton -> SelectionTarget.DELETE_BUTTON
            else -> SelectionTarget.HINT_BUTTON
        }
    }
}

enum class SelectionTarget { FIELD, NUMBER_BUTTON, DELETE_BUTTON, HINT_BUTTON }
