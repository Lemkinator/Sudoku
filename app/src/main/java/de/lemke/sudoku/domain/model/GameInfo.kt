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
import java.util.Locale

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE

/** Which assisted highlighting a player used. */
enum class Highlighting { REGIONAL, NUMBER }

data class GameInfo(
    val id: SudokuId,
    val size: SudokuSize,
    val difficulty: Difficulty,
    val mode: GameMode,
    val created: LocalDateTime,
    val updated: LocalDateTime,
    val seconds: Int = 0,
    val hintsUsed: Int = 0,
    val notesMade: Int = 0,
    val errorsMade: Int = 0,
    val regionalHighlightingUsed: Boolean = false,
    val numberHighlightingUsed: Boolean = false,
    val eraserUsed: Boolean = false,
    val isChecklist: Boolean = false,
    val isReverseChecklist: Boolean = false,
    val checklistNumber: Int = 0,
) {
    val availableHints: Int get() = size.hintLimit - hintsUsed

    val isHintAvailable: Boolean get() = mode == GameMode.Normal && hintsUsed < size.hintLimit

    val timeString: String
        get() =
            if (seconds >= SECONDS_PER_HOUR) {
                String.format(
                    Locale.getDefault(),
                    "%02d:%02d:%02d",
                    seconds / SECONDS_PER_HOUR,
                    seconds / SECONDS_PER_MINUTE % SECONDS_PER_MINUTE,
                    seconds % SECONDS_PER_MINUTE,
                )
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
            }

    fun errorLimitReached(errorLimit: Int): Boolean = if (errorLimit == 0) false else errorsMade >= errorLimit

    fun withHighlightingUsed(highlighting: Highlighting): GameInfo =
        when (highlighting) {
            Highlighting.REGIONAL -> copy(regionalHighlightingUsed = true)
            Highlighting.NUMBER -> copy(numberHighlightingUsed = true)
        }

    fun withDigitEntered(digit: Int): GameInfo =
        when {
            isChecklist -> {
                when {
                    digit == checklistNumber -> this
                    digit > checklistNumber -> copy(checklistNumber = digit)
                    else -> copy(isChecklist = false, checklistNumber = digit)
                }
            }

            isReverseChecklist -> {
                when {
                    digit == checklistNumber -> this
                    digit < checklistNumber -> copy(checklistNumber = digit)
                    else -> copy(isReverseChecklist = false, checklistNumber = digit)
                }
            }

            checklistNumber == 0 && digit == 1 -> {
                copy(isChecklist = true, checklistNumber = digit)
            }

            checklistNumber == 0 && digit == size.value -> {
                copy(isReverseChecklist = true, checklistNumber = digit)
            }

            else -> {
                copy(checklistNumber = digit)
            }
        }
}
