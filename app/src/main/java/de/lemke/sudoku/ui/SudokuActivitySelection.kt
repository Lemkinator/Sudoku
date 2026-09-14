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

import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.getCompletedNumbers
import de.lemke.sudoku.domain.model.move
import de.lemke.sudoku.domain.model.setHint
import de.lemke.commonutils.R as commonutilsR

internal fun SudokuActivity.selectFromNothing(newSelected: Int?) {
    when (newSelected) {
        // selected nothing
        null -> {}

        // selected field
        in 0 until sudoku.itemCount -> {
            gameAdapter.selectFieldView(newSelected, userSettings.highlightRegional, userSettings.highlightNumber)
            selected = newSelected
        }

        // selected button
        in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> {
            selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
        }

        // selected nothing
        else -> {}
    }
}

internal fun SudokuActivity.selectFromField(newSelected: Int?) {
    val position = Position.create(selected!!, sudoku.size)
    when (newSelected) {
        // selected nothing / selected same field
        null, selected -> {
            selected = null
        }

        // selected field
        in 0 until sudoku.itemCount -> {
            selected = newSelected
        }

        // selected number
        in sudoku.itemCount until sudoku.itemCount + sudoku.size -> {
            sudoku.move(position, newSelected - sudoku.itemCount + 1, notesEnabled)
            selected = null
        }

        // selected delete
        sudoku.itemCount + sudoku.size -> {
            sudoku.move(position, null, notesEnabled)
            selected = null
        }

        // selected hint
        sudoku.itemCount + sudoku.size + 1 -> {
            sudoku.setHint(position)
            selected = null
            refreshHintButton()
        }
    }
    gameAdapter.selectFieldView(selected, userSettings.highlightRegional, userSettings.highlightNumber)
}

internal fun SudokuActivity.selectFromNumberButton(newSelected: Int?) {
    when (newSelected) {
        // selected nothing / selected same button
        null, selected -> {
            selectButton(null, userSettings.highlightNumber)
        }

        // selected field
        in 0 until sudoku.itemCount -> {
            sudoku.move(newSelected, selected!! - sudoku.itemCount + 1, notesEnabled)
            highlightCurrentNumber(selected!! - sudoku.itemCount + 1)
        }

        // selected button
        in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> {
            gameAdapter.selectFieldView(null, userSettings.highlightRegional, userSettings.highlightNumber)
            selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
        }

        // selected nothing
        else -> {
            selectButton(null, userSettings.highlightNumber)
        }
    }
}

internal fun SudokuActivity.selectFromDeleteButton(newSelected: Int?) {
    when (newSelected) {
        // selected nothing / selected same button
        null, selected -> {
            selectButton(null, userSettings.highlightNumber)
        }

        // selected field
        in 0 until sudoku.itemCount -> {
            sudoku.move(newSelected, null, notesEnabled)
        }

        // selected button(not delete)
        in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> {
            selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
        }

        // selected nothing
        else -> {
            selectButton(null, userSettings.highlightNumber)
        }
    }
}

internal fun SudokuActivity.selectFromHintButton(newSelected: Int?) {
    when (newSelected) {
        // selected nothing / selected same button
        null, selected -> {
            selectButton(null, userSettings.highlightNumber)
        }

        // selected field
        in 0 until sudoku.itemCount -> {
            sudoku.setHint(newSelected)
            if (!sudoku.isHintAvailable) selected = null
            refreshHintButton()
        }

        // selected button(not hint)
        in sudoku.itemCount until sudoku.itemCount + sudoku.size + 1 -> {
            selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
        }

        // selected nothing
        else -> {
            selectButton(null, userSettings.highlightNumber)
        }
    }
}

internal fun SudokuActivity.selectButton(
    i: Int?,
    highlightSelectedNumber: Boolean,
) {
    for (button in sudokuButtons) button.backgroundTintList = transparent
    binding.deleteButton.backgroundTintList = transparent
    binding.hintButton.backgroundTintList = transparent
    if (i != null) {
        when (i) {
            sudoku.size -> {
                binding.deleteButton.backgroundTintList = colorPrimary
            }

            sudoku.size + 1 -> {
                binding.hintButton.backgroundTintList = colorPrimary
            }

            else -> {
                sudokuButtons[i].backgroundTintList = colorPrimary
                if (highlightSelectedNumber) gameAdapter.highlightNumber(i + 1)
            }
        }
        selected = sudoku.itemCount + i
    } else {
        selected = null
        if (highlightSelectedNumber) gameAdapter.highlightNumber(null)
    }
}

private fun SudokuActivity.selectNextButton(
    currentNumber: Int,
    completedNumbers: List<Pair<Int, Boolean>>,
) {
    var number = currentNumber
    while (completedNumbers[number - 1].second) {
        number++
        if (number > completedNumbers.size) number = 1 // wrap around
        if (number == currentNumber) { // all numbers are completed
            selectButton(null, userSettings.highlightNumber)
            return
        }
    }
    selectButton(number - 1, userSettings.highlightNumber)
}

internal fun SudokuActivity.checkAnyNumberCompleted() {
    sudoku.getCompletedNumbers().forEach { pair ->
        if (pair.second) {
            sudokuButtons[pair.first - 1].isEnabled = false
            sudokuButtons[pair.first - 1].setTextColor(getColor(commonutilsR.color.commonutils_secondary_text_icon_color))
        } else {
            sudokuButtons[pair.first - 1].isEnabled = true
            sudokuButtons[pair.first - 1].setTextColor(getColor(commonutilsR.color.commonutils_primary_text_icon_color))
        }
    }
}

private fun SudokuActivity.highlightCurrentNumber(currentNumber: Int) {
    selectNextButton(currentNumber, sudoku.getCompletedNumbers())
}
