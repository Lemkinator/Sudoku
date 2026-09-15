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

import java.time.LocalDateTime
import kotlin.concurrent.timer

fun Sudoku.startTimer(delay: Long = 1000L) {
    if (completed) return
    timer?.cancel()
    timer =
        timer(initialDelay = delay, period = 1000L) {
            seconds++
            updated = LocalDateTime.now()
            gameListener?.onTimeChanged()
        }
}

fun Sudoku.stopTimer() {
    timer?.cancel()
    timer = null
}

fun Sudoku.move(
    index: Int,
    value: Int?,
    isNote: Boolean = false,
) = move(Position.create(index, size), value, isNote)

fun Sudoku.move(
    position: Position,
    value: Int?,
    isNote: Boolean = false,
): Boolean {
    val field = get(position)
    return when {
        timer == null || field.given || field.hint -> false
        isNote -> moveNote(position, field, value)
        value == null -> moveErase(position, field)
        field.correct || field.value == value -> false
        else -> moveSet(position, field, value)
    }
}

private fun Sudoku.moveNote(
    position: Position,
    field: Field,
    value: Int?,
): Boolean {
    if (field.value != null) return false
    if (value != null) {
        if (field.toggleNote(value)) notesMade++
    } else {
        field.notes.clear()
    }
    gameListener?.onFieldChanged(position)
    return true
}

private fun Sudoku.moveErase(
    position: Position,
    field: Field,
): Boolean {
    if (field.value == null && field.notes.isEmpty()) return false
    eraserUsed = true
    if (field.value == null) field.notes.clear()
    field.value = null
    gameListener?.onFieldChanged(position)
    return true
}

private fun Sudoku.moveSet(
    position: Position,
    field: Field,
    value: Int,
): Boolean {
    field.value = value
    gameListener?.onFieldChanged(position)
    checkChecklist(value)
    if (field.error) {
        errorsMade++
        gameListener?.onError()
    } else {
        removeNumberNotesFromNeighbors(position, value)
    }
    if (completed) {
        stopTimer()
        gameListener?.onCompleted(position)
    }
    return true
}

private fun Sudoku.checkChecklist(value: Int) {
    when {
        isChecklist -> {
            when {
                value == checklistNumber -> return
                value > checklistNumber -> checklistNumber = value
                else -> isChecklist = false
            }
        }

        isReverseChecklist -> {
            when {
                value == checklistNumber -> return
                value < checklistNumber -> checklistNumber = value
                else -> isReverseChecklist = false
            }
        }

        checklistNumber == 0 -> {
            when (value) {
                1 -> isChecklist = true
                size -> isReverseChecklist = true
            }
        }
    }
    checklistNumber = value
}

private fun Sudoku.removeNumberNotesFromNeighbors(
    position: Position,
    value: Int?,
) {
    get(position).notes.clear()
    gameListener?.onFieldChanged(position)
    getNeighbors(position).forEach {
        if (it.removeNote(value)) gameListener?.onFieldChanged(it.position)
    }
}

fun Sudoku.setHint(index: Int) = setHint(Position.create(index, size))

fun Sudoku.setHint(position: Position) {
    if (timer == null || get(position).given || get(position).hint || !isHintAvailable) return
    hintsUsed++
    get(position).setHint()
    gameListener?.onFieldChanged(position)
    removeNumberNotesFromNeighbors(position, get(position).value)
    if (completed) {
        stopTimer()
        gameListener?.onCompleted(position)
    }
}
