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

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.NoteMask
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.domain.model.toSudokuInt
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId

private const val SCHEMA_VERSION_BEFORE = 2
private const val SCHEMA_VERSION_AFTER = 3

private const val CREATE_GAME =
    "CREATE TABLE IF NOT EXISTS `game` (`id` TEXT NOT NULL, `mode` INTEGER NOT NULL, `level` INTEGER, `dailyDate` INTEGER, " +
        "`size` INTEGER NOT NULL, `difficulty` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
        "`seconds` INTEGER NOT NULL, `hintsUsed` INTEGER NOT NULL, `notesMade` INTEGER NOT NULL, `errorsMade` INTEGER NOT NULL, " +
        "`regionalHighlightingUsed` INTEGER NOT NULL, `numberHighlightingUsed` INTEGER NOT NULL, `eraserUsed` INTEGER NOT NULL, " +
        "`isChecklist` INTEGER NOT NULL, `isReverseChecklist` INTEGER NOT NULL, `checklistNumber` INTEGER NOT NULL, " +
        "`completed` INTEGER NOT NULL, `progress` INTEGER NOT NULL, `board` BLOB NOT NULL, PRIMARY KEY(`id`))"

private const val INSERT_GAME =
    "INSERT OR REPLACE INTO `game` (id, mode, level, dailyDate, size, difficulty, createdAt, updatedAt, seconds, hintsUsed, notesMade, " +
        "errorsMade, regionalHighlightingUsed, numberHighlightingUsed, eraserUsed, isChecklist, isReverseChecklist, checklistNumber, " +
        "completed, progress, board) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

private const val SELECT_SUDOKUS =
    "SELECT id, size, difficulty, modeLevel, regionalHighlightingUsed, numberHighlightingUsed, eraserUsed, isChecklist, " +
        "isReverseChecklist, checklistNumber, hintsUsed, notesMade, errorsMade, created, updated, seconds FROM sudoku"

private const val SELECT_FIELDS =
    "SELECT sudokuId, gameSize, `index`, solution, value, given, hint, notes FROM field ORDER BY sudokuId, `index`"

/**
 * Replaces the `sudoku` and `field` tables with the `game` table. A game that cannot be read is dropped, and so is a game whose
 * given cell differs from its solution. Of two games that claim the same level or day, the completed one stays, and of two
 * completed or two unfinished ones the most recently updated one.
 */
val MIGRATION_2_3 =
    object : Migration(SCHEMA_VERSION_BEFORE, SCHEMA_VERSION_AFTER) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_GAME)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_game_size_level` ON `game` (`size`, `level`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_game_dailyDate` ON `game` (`dailyDate`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_game_mode_updatedAt` ON `game` (`mode`, `updatedAt`)")
            val zone = ZoneId.systemDefault()
            db
                .readLegacyGames(zone)
                .sortedWith(compareBy<Sudoku>({ it.completed }, { it.info.updated }))
                .forEach { db.execSQL(INSERT_GAME, it.toEntity(zone).bindArgs()) }
            db.execSQL("DROP TABLE `field`")
            db.execSQL("DROP TABLE `sudoku`")
        }
    }

private class LegacyField(
    val sudokuId: String,
    val gameSize: Int,
    val index: Int,
    val solution: Int?,
    val value: Int?,
    val given: Boolean,
    val hint: Boolean,
    val notes: String,
)

private fun GameEntity.bindArgs(): Array<Any?> =
    with(header) {
        arrayOf(
            id,
            mode,
            level,
            dailyDate,
            size,
            difficulty,
            createdAt,
            updatedAt,
            seconds,
            hintsUsed,
            notesMade,
            errorsMade,
            regionalHighlightingUsed,
            numberHighlightingUsed,
            eraserUsed,
            isChecklist,
            isReverseChecklist,
            checklistNumber,
            completed,
            progress,
            board,
        )
    }

private fun SupportSQLiteDatabase.readLegacyGames(zone: ZoneId): List<Sudoku> {
    val infos =
        query(SELECT_SUDOKUS).use { cursor -> buildList { while (cursor.moveToNext()) cursor.legacyInfoOrNull(zone)?.let(::add) } }
    val boards = readLegacyBoards(infos.associate { it.id.value to it.size })
    return infos.mapNotNull { info -> boards[info.id.value]?.let { Sudoku(info, it) } }
}

/** The readable boards by sudoku id. The field rows stream by sudoku id and become a board per game; all decoded boards stay in memory. */
private fun SupportSQLiteDatabase.readLegacyBoards(sizes: Map<String, SudokuSize>): Map<String, Board> =
    buildMap {
        val group = mutableListOf<LegacyField>()

        fun flush() {
            val size = group.firstOrNull()?.let { sizes[it.sudokuId] }
            if (size != null) legacyBoardOrNull(size, group)?.let { put(group.first().sudokuId, it) }
            group.clear()
        }
        query(SELECT_FIELDS).use { cursor ->
            while (cursor.moveToNext()) {
                val field = cursor.legacyField()
                if (group.isNotEmpty() && group.first().sudokuId != field.sudokuId) flush()
                group += field
            }
        }
        flush()
    }

private fun Cursor.legacyInfoOrNull(zone: ZoneId): GameInfo? {
    val size = SudokuSize.fromValueOrNull(int("size"))
    val created = stampOrNull("created", zone)
    val mode = created?.let { legacyGameModeOrNull(int("modeLevel"), it.toLocalDate()) }
    val updated = stampOrNull("updated", zone)
    return if (size != null && mode != null && created != null && updated != null) {
        GameInfo(
            id = SudokuId(string("id")),
            size = size,
            difficulty = Difficulty.fromCodeOrNull(int("difficulty")) ?: Difficulty.MEDIUM,
            mode = mode,
            created = created,
            updated = updated,
            seconds = int("seconds"),
            hintsUsed = int("hintsUsed"),
            notesMade = int("notesMade"),
            errorsMade = int("errorsMade"),
            regionalHighlightingUsed = int("regionalHighlightingUsed") != 0,
            numberHighlightingUsed = int("numberHighlightingUsed") != 0,
            eraserUsed = int("eraserUsed") != 0,
            isChecklist = int("isChecklist") != 0,
            isReverseChecklist = int("isReverseChecklist") != 0,
            checklistNumber = int("checklistNumber"),
        )
    } else {
        null
    }
}

private fun Cursor.legacyField(): LegacyField =
    LegacyField(
        sudokuId = string("sudokuId"),
        gameSize = int("gameSize"),
        index = int("index"),
        solution = intOrNull("solution"),
        value = intOrNull("value"),
        given = int("given") != 0,
        hint = int("hint") != 0,
        notes = string("notes"),
    )

private fun legacyBoardOrNull(
    size: SudokuSize,
    fields: List<LegacyField>,
): Board? {
    val cells = fields.map { it.cellOrNull(size) }
    val complete = fields.map { it.index } == (0 until size.cellCount).toList() && fields.all { it.gameSize == size.value }
    return if (complete && null !in cells) Board(size, cells.requireNoNulls()) else null
}

/**
 * The cell of a stored field, or null if its solution or value is no digit of [size] or a given cell does not hold its solution.
 * A hint cell that does not hold its solution becomes a player cell. A note that is no digit of [size] is dropped.
 */
private fun LegacyField.cellOrNull(size: SudokuSize): Cell? {
    val digits = 1..size.value
    val readable = solution in digits && (value == null || value in digits) && (!given || value == solution)
    return if (solution == null || !readable) {
        null
    } else {
        val origin =
            when {
                given -> CellOrigin.GIVEN
                hint && value == solution -> CellOrigin.HINT
                else -> CellOrigin.PLAYER
            }
        val noteDigits = notes.mapNotNull { it.toSudokuInt() }.filter { it <= size.value }
        Cell(solution, value, origin, NoteMask.of(*noteDigits.toIntArray()))
    }
}

private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

private fun Cursor.intOrNull(column: String): Int? = getColumnIndexOrThrow(column).takeUnless { isNull(it) }?.let { getInt(it) }

private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

/** The stamp, or null if it is no date time or [zone] places it outside the range of epoch milliseconds. */
private fun Cursor.stampOrNull(
    column: String,
    zone: ZoneId,
): LocalDateTime? =
    try {
        LocalDateTime.parse(string(column)).also { it.atZone(zone).toInstant().toEpochMilli() }
    } catch (_: DateTimeException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
