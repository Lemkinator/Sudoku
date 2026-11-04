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

package de.lemke.sudoku.data.backup

import de.lemke.sudoku.data.codec.BoardCodec
import de.lemke.sudoku.data.database.GameEntity
import de.lemke.sudoku.data.database.legacyGameModeOrNull
import de.lemke.sudoku.data.database.toHeader
import de.lemke.sudoku.data.database.toLegacyModeLevel
import de.lemke.sudoku.data.database.toSummary
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.GameSummary
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import java.time.ZoneId
import java.time.temporal.ChronoUnit.SECONDS
import java.util.Base64

/**
 * The row of [entry], or null if the entry is broken. A broken entry has a size, difficulty or mode level that names nothing,
 * negative seconds, or a board that is no Base64 or no board of its size. The row keeps the bytes of the entry's board. [zone]
 * places the updated stamp on the device clock.
 */
fun gameFromBackup(
    entry: SudokuBackupEntry,
    zone: ZoneId,
): GameEntity? {
    val info = infoOrNull(entry, zone)
    val stored = boardBytesOrNull(entry.board)?.let { bytes -> BoardCodec.decode(bytes)?.let { board -> bytes to board } }
    return if (info != null && stored != null && stored.second.size == info.size) {
        GameEntity(GameSummary(info, stored.second.completed, stored.second.progress).toHeader(zone), stored.first)
    } else {
        null
    }
}

private fun infoOrNull(
    entry: SudokuBackupEntry,
    zone: ZoneId,
): GameInfo? {
    val size = SudokuSize.fromValueOrNull(entry.size)
    val difficulty = Difficulty.fromNameOrNull(entry.difficulty)
    val mode = legacyGameModeOrNull(entry.modeLevel)
    return if (size != null && difficulty != null && mode != null && entry.seconds >= 0) {
        GameInfo(
            id = SudokuId(entry.id),
            size = size,
            difficulty = difficulty,
            mode = mode,
            created = entry.created.toLocalDateTime(),
            updated = entry.updated.atZoneSameInstant(zone).toLocalDateTime(),
            seconds = entry.seconds,
            hintsUsed = entry.hintsUsed,
            notesMade = entry.notesMade,
            errorsMade = entry.errorsMade,
            regionalHighlightingUsed = entry.regionalHighlightingUsed,
            numberHighlightingUsed = entry.numberHighlightingUsed,
            eraserUsed = entry.eraserUsed,
            isChecklist = entry.isChecklist,
            isReverseChecklist = entry.isReverseChecklist,
            checklistNumber = entry.checklistNumber,
        )
    } else {
        null
    }
}

/**
 * The entry of [game], or null if its header is unreadable. The board bytes go into the entry as they are stored. [zone] gives the
 * offset of both stamps.
 */
fun entryOf(
    game: GameEntity,
    zone: ZoneId,
): SudokuBackupEntry? =
    game.header.toSummary(zone)?.info?.let { info ->
        SudokuBackupEntry(
            id = info.id.value,
            size = info.size.value,
            difficulty = info.difficulty.name,
            modeLevel = info.mode.toLegacyModeLevel(),
            created =
                info.created
                    .atZone(zone)
                    .toOffsetDateTime()
                    .truncatedTo(SECONDS),
            updated =
                info.updated
                    .atZone(zone)
                    .toOffsetDateTime()
                    .truncatedTo(SECONDS),
            seconds = info.seconds,
            regionalHighlightingUsed = info.regionalHighlightingUsed,
            numberHighlightingUsed = info.numberHighlightingUsed,
            eraserUsed = info.eraserUsed,
            isChecklist = info.isChecklist,
            isReverseChecklist = info.isReverseChecklist,
            checklistNumber = info.checklistNumber,
            hintsUsed = info.hintsUsed,
            notesMade = info.notesMade,
            errorsMade = info.errorsMade,
            board = Base64.getEncoder().encodeToString(game.board),
        )
    }

/** The progress of the game of [entry]; a broken entry has none. */
fun progressOf(
    entry: SudokuBackupEntry,
    zone: ZoneId,
): Int = gameFromBackup(entry, zone)?.header?.progress ?: 0

private fun boardBytesOrNull(text: String): ByteArray? =
    try {
        Base64.getDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }
