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
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TEST_DB = "migration-test"

/**
 * Exercises the real [MIGRATION_1_2] against a v1 database built from the exported schema
 * (`app/schemas/de.lemke.sudoku.data.database.AppDatabase/1.json`), rather than only ever creating a fresh v2
 * database in-memory — that path never runs Room's generated schema-validation code
 * (`AppDatabase_Impl`'s open-helper delegate) against an actual migration.
 *
 * Uses the [File]/[androidx.sqlite.SQLiteDriver] constructor (not the deprecated name-based one): `AppDatabase` is
 * generated with `room.generateKotlin = true`, whose connection manager always resolves an absolute database path;
 * the name-based `MigrationTestHelper(instrumentation, databaseClass, ..., SupportSQLiteOpenHelper.Factory)`
 * overload throws `IllegalArgumentException: This driver is configured to open a database named 'X' but
 * '<resolved path>\X' was requested` because its `SupportSQLiteOpenHelper` is bound to the bare name while Room's
 * generated impl requests the resolved absolute path.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = File(ApplicationProvider.getApplicationContext<Context>().getDatabasePath(TEST_DB).path),
            driver = AndroidSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )

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
    fun `opening a real v1 database through the production builder runs the real migration and validates it`() {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO sudoku (id, size, difficulty, hintsUsed, notesMade, errorsMade, seconds, created, updated, " +
                    "neighborHighlightingUsed, numberHighlightingUsed, autoNotesUsed, modeLevel) VALUES " +
                    "('id-2', 9, 1, 0, 0, 0, 0, '2026-01-01T00:00:00', '2026-01-01T00:00:00', 0, 0, 0, 0)",
            )
            close()
        }

        // Opens the same file through the real production AppDatabase_Impl (not MigrationTestHelper's own
        // schema-bundle-driven delegate), forcing its generated createOpenDelegate/onValidateSchema to run for real.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val realDatabase = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB).addMigrations(MIGRATION_1_2).build()
        runBlocking { realDatabase.sudokuDao().getMaxSudokuLevel(4) }.shouldBeNull()
        realDatabase.close()
    }

    @Test
    fun `opening a v1 database file stamped as v2 without migrating fails real schema validation`() {
        // Bumps the on-disk user_version to 2 without ever running MIGRATION_1_2, so the real generated
        // onValidateSchema (AppDatabase_Impl$createOpenDelegate$_openDelegate$1) runs against the untouched v1
        // schema, finds a genuine column mismatch, and returns an invalid ValidationResult for real (rather than
        // the always-matching schema every other test in this suite produces via a correct migration).
        helper.createDatabase(1).apply {
            execSQL("PRAGMA user_version = 2")
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val realDatabase = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB).build()
        shouldThrow<IllegalStateException> {
            runBlocking { realDatabase.sudokuDao().getMaxSudokuLevel(4) }
        }
        realDatabase.close()
    }
}
