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

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import de.lemke.sudoku.data.codec.BoardCodec
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.NoteMask
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.time.ZoneOffset
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val FOUR = 4
private const val NINE = 9
private const val SIXTEEN = 16
private const val DAY = "2026-10-01"

/**
 * The name-based MigrationTestHelper overload fails with `room.generateKotlin`'s absolute database path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationTest {
    private val testDb = "migration-test-${System.nanoTime()}"
    private val defaultZone = TimeZone.getDefault()

    @get:Rule
    val helper =
        MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = File(ApplicationProvider.getApplicationContext<Context>().getDatabasePath(testDb).path),
            driver = AndroidSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )

    @Before
    fun useUtc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After
    fun restoreZone() = TimeZone.setDefault(defaultZone)

    private fun seedMix(): SQLiteConnection =
        helper.createDatabase(2).also { connection ->
            LegacyDatabaseSeed(connection).apply {
                begin()
                game("normal-4", FOUR, 0, "${DAY}T07:00:00", "${DAY}T08:00:00", patternFields(SudokuSize.FOUR).withNotes(1, "2Z9"))
                game(
                    "normal-9",
                    NINE,
                    0,
                    "${DAY}T07:00:00",
                    "${DAY}T08:00:00",
                    patternFields(SudokuSize.NINE, filled = { it % 3 != 0 && it < 41 }).withHint(11).withNotes(79, "139"),
                )
                game("normal-16", SIXTEEN, 0, "${DAY}T07:00:00", "${DAY}T08:00:00", patternFields(SudokuSize.SIXTEEN).withNotes(5, "AG"))
                game("daily-0900", FOUR, -1, "${DAY}T06:00:00", "${DAY}T09:00:00", patternFields(SudokuSize.FOUR))
                game("daily-1000", FOUR, -1, "${DAY}T06:30:00", "${DAY}T10:00:00", patternFields(SudokuSize.FOUR))
                game("level-1", NINE, 1, "${DAY}T07:00:00", "${DAY}T08:00:00", patternFields(SudokuSize.NINE))
                game("level-2", NINE, 2, "${DAY}T07:00:00", "${DAY}T08:00:00", patternFields(SudokuSize.NINE))
                game("size-5", 5, 0, "${DAY}T07:00:00", "${DAY}T08:00:00", List(25) { SeedField(it, it % 5 + 1) })
                game("short-9", NINE, 0, "${DAY}T07:00:00", "${DAY}T08:00:00", patternFields(SudokuSize.NINE).dropLast(1))
                commit()
            }
        }

    private fun List<SeedField>.withNotes(
        index: Int,
        notes: String,
    ) = map { if (it.index == index) it.copy(notes = notes) else it }

    private fun List<SeedField>.withHint(index: Int) = map { if (it.index == index) it.copy(hint = true) else it }

    private fun SQLiteConnection.long(sql: String): Long =
        prepare(sql).use {
            it.step()
            it.getLong(0)
        }

    private fun SQLiteConnection.ids(sql: String): List<String> =
        prepare(sql).use { statement -> generateSequence { statement.takeIf { it.step() }?.getText(0) }.toList() }

    private fun SQLiteConnection.board(id: String): Board =
        prepare("SELECT board FROM game WHERE id = '$id'").use { statement ->
            statement.step()
            BoardCodec.decode(statement.getBlob(0)).shouldNotBeNull()
        }

    private fun migrated(connection: SQLiteConnection): SQLiteConnection {
        connection.close()
        return helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3))
    }

    @Test
    fun `migrating from version 1 to 2 renames neighborHighlightingUsed and adds the new columns`() {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO sudoku (id, size, difficulty, hintsUsed, notesMade, errorsMade, seconds, created, updated, " +
                    "neighborHighlightingUsed, numberHighlightingUsed, autoNotesUsed, modeLevel) VALUES " +
                    "('id-1', 9, 1, 0, 0, 0, 0, '2026-01-01T00:00:00', '2026-01-01T00:00:00', 1, 0, 0, 0)",
            )
            close()
        }

        val migratedConnection = helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2))
        migratedConnection.use { connection ->
            connection
                .prepare(
                    "SELECT regionalHighlightingUsed, eraserUsed, isChecklist, isReverseChecklist, checklistNumber " +
                        "FROM sudoku WHERE id = 'id-1'",
                ).use { stmt ->
                    stmt.step() shouldBe true
                    stmt.getInt(0) shouldBe 1
                    stmt.getInt(1) shouldBe 0
                    stmt.getInt(2) shouldBe 0
                    stmt.getInt(3) shouldBe 0
                    stmt.getInt(4) shouldBe 0
                }
        }
    }

    @Test
    fun `migrating 2 to 3 keeps 6 readable games and drops the old tables`() {
        migrated(seedMix()).use { connection ->
            connection.long("SELECT COUNT(*) FROM game") shouldBe 6
            connection.long("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('sudoku', 'field')") shouldBe 0
        }
    }

    @Test
    fun `the newer daily of a day wins`() {
        migrated(seedMix()).use { connection ->
            connection.ids("SELECT id FROM game WHERE mode = ${GameMode.CODE_DAILY}") shouldBe listOf("daily-1000")
        }
    }

    @Test
    fun `notes and hints survive the conversion`() {
        migrated(seedMix()).use { connection ->
            val nine = connection.board("normal-9")
            nine[79].notes shouldBe NoteMask.of(1, 3, 9)
            nine[11].origin shouldBe CellOrigin.HINT
        }
    }

    @Test
    fun `16x16 letter notes become digits 10 and 16`() {
        migrated(seedMix()).use { connection -> connection.board("normal-16")[5].notes shouldBe NoteMask.of(10, 16) }
    }

    @Test
    fun `a note char beyond the size is dropped`() {
        migrated(seedMix()).use { connection -> connection.board("normal-4")[1].notes shouldBe NoteMask.of(2) }
    }

    @Test
    fun `summary columns match the board`() {
        migrated(seedMix()).use { connection ->
            connection
                .prepare("SELECT completed, progress FROM game WHERE id = 'normal-9'")
                .use { statement ->
                    statement.step() shouldBe true
                    statement.getLong(0) shouldBe 0L
                    statement.getLong(1) shouldBe 50L
                }
        }
    }

    @Test
    fun `header columns carry the stamps, the mode keys and the counters of the old rows`() {
        migrated(seedMix()).use { connection ->
            connection
                .prepare(
                    "SELECT mode, level, dailyDate, size, difficulty, createdAt, updatedAt, seconds, hintsUsed, notesMade, errorsMade, " +
                        "regionalHighlightingUsed, eraserUsed, numberHighlightingUsed FROM game WHERE id IN ('level-2', 'daily-1000') " +
                        "ORDER BY mode",
                ).use { statement ->
                    statement.step() shouldBe true
                    listOf(statement.getLong(0), statement.getLong(2), statement.getLong(3)) shouldBe listOf(1L, 20727L, 4L)
                    statement.isNull(1) shouldBe true
                    listOf(statement.getLong(5), statement.getLong(6)) shouldBe listOf(1790836200000L, 1790848800000L)
                    statement.step() shouldBe true
                    listOf(statement.getLong(0), statement.getLong(1), statement.getLong(3), statement.getLong(4)) shouldBe
                        listOf(2L, 2L, 9L, 1L)
                    statement.isNull(2) shouldBe true
                    listOf(statement.getLong(7), statement.getLong(8), statement.getLong(9), statement.getLong(10)) shouldBe
                        listOf(95L, 2L, 3L, 4L)
                    listOf(statement.getLong(11), statement.getLong(12), statement.getLong(13)) shouldBe listOf(1L, 1L, 0L)
                }
        }
    }

    @Test
    fun `an empty version 2 database migrates to an empty game table`() {
        migrated(helper.createDatabase(2)).use { connection -> connection.long("SELECT COUNT(*) FROM game") shouldBe 0 }
    }

    @Test
    fun `repair rules keep the game and fix the cells they can`() {
        val fields =
            patternFields(SudokuSize.FOUR, given = { false })
                .map {
                    when (it.index) {
                        0 -> it.copy(given = true)
                        1 -> it.copy(given = true, value = 3)
                        2 -> it.copy(hint = true, value = it.solution)
                        3 -> it.copy(hint = true)
                        4 -> it.copy(hint = true, value = 2)
                        5 -> it.copy(given = true, hint = true, value = it.solution)
                        else -> it
                    }
                }
        val connection = helper.createDatabase(2)
        LegacyDatabaseSeed(connection).game("repair", FOUR, 0, "${DAY}T07:00:00", "${DAY}T08:00:00", fields)

        migrated(connection).use { migrated ->
            val board = migrated.board("repair")
            board.cells.take(6) shouldBe
                listOf(
                    Cell(1, 1, CellOrigin.GIVEN),
                    Cell(2, 2, CellOrigin.GIVEN),
                    Cell(3, 3, CellOrigin.HINT),
                    Cell(4, null, CellOrigin.PLAYER),
                    Cell(3, 2, CellOrigin.PLAYER),
                    Cell(4, 4, CellOrigin.GIVEN),
                )
        }
    }

    private fun LegacyDatabaseSeed.unreadableGames(valid: List<SeedField>) {
        fun broken(
            id: String,
            modeLevel: Int = 0,
            created: String = "${DAY}T07:00:00",
            updated: String = "${DAY}T08:00:00",
            fields: List<SeedField> = valid,
        ) = game(id, FOUR, modeLevel, created, updated, fields)

        fun List<SeedField>.at2(change: (SeedField) -> SeedField) = map { if (it.index == 2) change(it) else it }
        broken("null-solution", fields = valid.at2 { it.copy(solution = null) })
        broken("solution-5", fields = valid.at2 { it.copy(solution = 5) })
        broken("value-5", fields = valid.at2 { it.copy(value = 5) })
        broken("other-size-field", fields = valid.at2 { it.copy(gameSize = NINE) })
        broken("extra-field", fields = valid + SeedField(16, 1))
        broken("no-fields", fields = emptyList())
        broken("bad-created", created = "yesterday")
        broken("bad-updated", updated = "yesterday")
        broken("bad-mode", modeLevel = -2)
    }

    @Test
    fun `games that cannot be read are dropped and the rest stays`() {
        val connection = helper.createDatabase(2)
        val valid = patternFields(SudokuSize.FOUR)
        LegacyDatabaseSeed(connection).apply {
            game("control", FOUR, 0, "${DAY}T07:00:00", "${DAY}T08:00:00", valid)
            game("unknown-difficulty", FOUR, 0, "${DAY}T07:00:00", "${DAY}T08:00:00", valid, difficulty = 9)
            unreadableGames(valid)
        }

        migrated(connection).use { migrated ->
            migrated.ids("SELECT id FROM game ORDER BY id") shouldBe listOf("control", "unknown-difficulty")
            migrated.long("SELECT difficulty FROM game WHERE id = 'unknown-difficulty'") shouldBe 2
        }
    }

    @Test
    fun `opening a v2 file through the production builder migrates and validates`() {
        val connection = seedMix()
        connection.close()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(context, AppDatabase::class.java, testDb).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        val game = runBlocking { database.gameDao().getById("level-1") }.shouldNotBeNull()
        game.header.level shouldBe 1
        game
            .toSudoku(ZoneOffset.UTC)
            .shouldNotBeNull()
            .info.id shouldBe SudokuId("level-1")
        database.close()
    }

    @Test
    fun `opening a real v1 database through the production builder runs both migrations`() {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO sudoku (id, size, difficulty, hintsUsed, notesMade, errorsMade, seconds, created, updated, " +
                    "neighborHighlightingUsed, numberHighlightingUsed, autoNotesUsed, modeLevel) VALUES " +
                    "('id-2', 4, 1, 0, 0, 0, 0, '2026-01-01T00:00:00', '2026-01-01T00:00:00', 1, 0, 0, 0)",
            )
            repeat(SudokuSize.FOUR.cellCount) { index ->
                val solution = patternSolution(SudokuSize.FOUR, index)
                val value = if (index == 0) solution.toString() else "NULL"
                val given = if (index == 0) 1 else 0
                execSQL(
                    "INSERT INTO field (sudokuId, gameSize, `index`, value, solution, notes, given, hint) VALUES " +
                        "('id-2', 4, $index, $value, $solution, '', $given, 0)",
                )
            }
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.databaseBuilder(context, AppDatabase::class.java, testDb).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        val sudoku = runBlocking { database.gameDao().getById("id-2") }.shouldNotBeNull().toSudoku(ZoneOffset.UTC).shouldNotBeNull()
        sudoku.info.regionalHighlightingUsed shouldBe true
        sudoku.board[0] shouldBe Cell(1, 1, CellOrigin.GIVEN)
        database.close()
    }

    @Test
    fun `opening a v1 database file stamped as v3 without migrating fails real schema validation`() {
        helper.createDatabase(1).apply {
            execSQL("PRAGMA user_version = 3")
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val realDatabase = Room.databaseBuilder(context, AppDatabase::class.java, testDb).build()
        val exception =
            shouldThrow<IllegalStateException> {
                runBlocking { realDatabase.gameDao().maxLevel(4) }
            }
        exception.message.shouldNotBeNull() shouldContain "data integrity"
        realDatabase.close()
    }
}
