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

import android.content.res.Resources
import de.lemke.sudoku.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId.systemDefault
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofLocalizedDate
import java.time.format.DateTimeFormatter.ofLocalizedDateTime
import java.time.format.FormatStyle
import java.time.format.FormatStyle.FULL
import java.time.format.FormatStyle.MEDIUM
import java.util.UUID

private const val MAX_STANDARD_DIGIT = 9
private val MAX_LARGE_DIGIT = SudokuSize.entries.maxOf { it.value }
private const val LARGE_DIGIT_OFFSET = 10

@JvmInline
value class SudokuId(val value: String) {
    companion object {
        fun generate(): SudokuId = SudokuId(UUID.randomUUID().toString())
    }
}

data class Sudoku(val info: GameInfo, val board: Board) {
    init {
        require(info.size == board.size) { "Game size ${info.size} differs from board size ${board.size}" }
    }

    val completed: Boolean get() = board.completed

    val progress: Int get() = board.progress

    /** Applies [move] and the counters it changes; null when the board rejects it or no hint is left. */
    fun play(move: BoardMove): GameMove? {
        val result = board.takeIf { move !is BoardMove.RevealHint || info.isHintAvailable }?.apply(move) ?: return null
        val played =
            when (move) {
                is BoardMove.Enter -> info.withDigitEntered(move.digit)
                is BoardMove.Erase -> info.copy(eraserUsed = true)
                is BoardMove.RevealHint -> info.copy(hintsUsed = info.hintsUsed + 1)
                is BoardMove.ToggleNote, is BoardMove.ClearNotes -> info
            }
        val counted =
            played.copy(
                notesMade = played.notesMade + if (result.noteAdded) 1 else 0,
                errorsMade = played.errorsMade + if (result.error) 1 else 0,
            )
        return GameMove(copy(info = counted, board = result.board), result)
    }

    fun tick(now: LocalDateTime): Sudoku = copy(info = info.copy(seconds = info.seconds + 1, updated = now))

    fun withHighlightingUsed(highlighting: Highlighting): Sudoku = copy(info = info.withHighlightingUsed(highlighting))

    fun restarted(): Sudoku = copy(info = info.zeroed(), board = board.initial())

    fun initialCopy(): Sudoku =
        Sudoku(info.zeroed().copy(id = SudokuId.generate(), mode = GameMode.Normal, updated = info.created), board.initial())

    fun copyAsNormal(): Sudoku = copy(info = info.copy(id = SudokuId.generate(), mode = GameMode.Normal))

    fun getLocalStatisticsString(resources: Resources): String =
        resources.getString(
            R.string.sudokuStatisticsString,
            when (val mode = info.mode) {
                GameMode.Normal -> resources.getString(R.string.normal_sudoku)
                GameMode.Daily -> resources.getString(R.string.daily_sudoku)
                is GameMode.Level -> resources.getString(R.string.level_number, mode.number)
            },
            info.size.value,
            info.difficulty.getLocalString(resources),
            info.timeString,
            info.errorsMade,
            info.hintsUsed,
            info.notesMade,
            resources.getString(if (info.regionalHighlightingUsed) R.string.commonutils_yes else R.string.commonutils_no),
            resources.getString(if (info.numberHighlightingUsed) R.string.commonutils_yes else R.string.commonutils_no),
            info.created.formatFull,
            info.updated.formatFull,
        )

    fun getLocalStatisticsStringShare(resources: Resources): String =
        if (completed) {
            resources.getString(R.string.sudoku_completed)
        } else {
            resources.getString(R.string.sudoku_solving, progress)
        } + getLocalStatisticsString(resources)

    companion object {
        const val MODE_LEVEL_ERROR_LIMIT = 3
        const val MODE_DAILY_ERROR_LIMIT = 3
    }
}

data class GameMove(val sudoku: Sudoku, val result: MoveResult)

private fun GameInfo.zeroed(): GameInfo =
    copy(
        seconds = 0,
        hintsUsed = 0,
        notesMade = 0,
        errorsMade = 0,
        regionalHighlightingUsed = false,
        numberHighlightingUsed = false,
        eraserUsed = false,
        isChecklist = false,
        isReverseChecklist = false,
        checklistNumber = 0,
    )

fun Int?.toSudokuString(): CharSequence? =
    when (this) {
        null -> null
        in 1..MAX_STANDARD_DIGIT -> this.toString()
        in LARGE_DIGIT_OFFSET..MAX_LARGE_DIGIT -> ('A' + (this - LARGE_DIGIT_OFFSET)).toString()
        else -> null
    }

/** The char of a digit in `1..16`: `1`-`9`, then `A`-`G`. */
fun Int.toSudokuDigitChar(): Char = if (this < LARGE_DIGIT_OFFSET) '0' + this else 'A' + (this - LARGE_DIGIT_OFFSET)

fun Char.toSudokuInt(): Int? =
    when (this) {
        in '1'..'0' + MAX_STANDARD_DIGIT -> this - '0'
        in 'A'..'A' + (MAX_LARGE_DIGIT - LARGE_DIGIT_OFFSET) -> this - 'A' + LARGE_DIGIT_OFFSET
        else -> null
    }

val LocalDateTime.monthAndYear: String get() = format(DateTimeFormatter.ofPattern("MMMM yyyy"))
val LocalDateTime.dateFormatShort: String get() = format(ofLocalizedDate(FormatStyle.SHORT))
val LocalDate.formatFull: String get() = format(ofLocalizedDate(FULL))
val LocalDateTime.formatFull: String get() = format(ofLocalizedDateTime(FULL, MEDIUM).withZone(systemDefault()))
