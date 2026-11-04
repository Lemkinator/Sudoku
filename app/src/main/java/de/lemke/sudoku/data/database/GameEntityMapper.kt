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

import de.lemke.sudoku.data.codec.BoardCodec
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.GameSummary
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

fun Sudoku.toEntity(zone: ZoneId): GameEntity = GameEntity(summary().toHeader(zone), BoardCodec.encode(board))

/** The game of this row, or null if its header or its board is unreadable. */
fun GameEntity.toSudoku(zone: ZoneId): Sudoku? =
    header.toSummary(zone)?.let { summary ->
        BoardCodec.decode(board)?.takeIf { it.size == summary.info.size }?.let { Sudoku(summary.info, it) }
    }

fun GameSummary.toHeader(zone: ZoneId): GameHeader =
    GameHeader(
        id = info.id.value,
        mode = info.mode.code,
        level = (info.mode as? GameMode.Level)?.number,
        dailyDate = info.dailyDate?.toEpochDay(),
        size = info.size.value,
        difficulty = info.difficulty.code,
        createdAt = info.created.toEpochMilli(zone),
        updatedAt = info.updated.toEpochMilli(zone),
        seconds = info.seconds,
        hintsUsed = info.hintsUsed,
        notesMade = info.notesMade,
        errorsMade = info.errorsMade,
        regionalHighlightingUsed = info.regionalHighlightingUsed,
        numberHighlightingUsed = info.numberHighlightingUsed,
        eraserUsed = info.eraserUsed,
        isChecklist = info.isChecklist,
        isReverseChecklist = info.isReverseChecklist,
        checklistNumber = info.checklistNumber,
        completed = completed,
        progress = progress,
    )

/** The summary of this header, or null if its size, mode or difficulty names nothing this version knows. */
fun GameHeader.toSummary(zone: ZoneId): GameSummary? {
    val size = SudokuSize.fromValueOrNull(size)
    val mode = GameMode.fromCode(mode, level, dailyDate?.let(::localDateOrNull))
    val difficulty = Difficulty.fromCodeOrNull(difficulty)
    return if (size != null && mode != null && difficulty != null) {
        GameSummary(
            info =
                GameInfo(
                    id = SudokuId(id),
                    size = size,
                    difficulty = difficulty,
                    mode = mode,
                    created = createdAt.toLocalDateTime(zone),
                    updated = updatedAt.toLocalDateTime(zone),
                    seconds = seconds,
                    hintsUsed = hintsUsed,
                    notesMade = notesMade,
                    errorsMade = errorsMade,
                    regionalHighlightingUsed = regionalHighlightingUsed,
                    numberHighlightingUsed = numberHighlightingUsed,
                    eraserUsed = eraserUsed,
                    isChecklist = isChecklist,
                    isReverseChecklist = isReverseChecklist,
                    checklistNumber = checklistNumber,
                ),
            completed = completed,
            progress = progress,
        )
    } else {
        null
    }
}

/** The date of [epochDay], or null if it lies outside the supported dates. */
private fun localDateOrNull(epochDay: Long): LocalDate? =
    try {
        LocalDate.ofEpochDay(epochDay)
    } catch (_: DateTimeException) {
        null
    }

private fun LocalDateTime.toEpochMilli(zone: ZoneId): Long = atZone(zone).toInstant().toEpochMilli()

private fun Long.toLocalDateTime(zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)
