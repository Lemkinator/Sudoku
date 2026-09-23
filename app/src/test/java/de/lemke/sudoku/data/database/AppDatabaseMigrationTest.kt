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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The name-based MigrationTestHelper overload fails with `room.generateKotlin`'s absolute database path.
 *
 * sdk = 36: Robolectric's max supported SDK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationTest {
    private val testDb = "migration-test-${System.nanoTime()}"

    @get:Rule
    val helper =
        MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = File(ApplicationProvider.getApplicationContext<Context>().getDatabasePath(testDb).path),
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

        val context = ApplicationProvider.getApplicationContext<Context>()
        val realDatabase = Room.databaseBuilder(context, AppDatabase::class.java, testDb).addMigrations(MIGRATION_1_2).build()
        val row = runBlocking { realDatabase.sudokuDao().getById("id-2") }.shouldNotBeNull()
        row.sudoku.size shouldBe 9
        row.sudoku.regionalHighlightingUsed shouldBe false
        row.sudoku.eraserUsed shouldBe false
        row.sudoku.isChecklist shouldBe false
        row.sudoku.isReverseChecklist shouldBe false
        row.sudoku.checklistNumber shouldBe 0
        realDatabase.close()
    }

    @Test
    fun `opening a v1 database file stamped as v2 without migrating fails real schema validation`() {
        helper.createDatabase(1).apply {
            execSQL("PRAGMA user_version = 2")
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val realDatabase = Room.databaseBuilder(context, AppDatabase::class.java, testDb).build()
        val exception =
            shouldThrow<IllegalStateException> {
                runBlocking { realDatabase.sudokuDao().getMaxSudokuLevel(4) }
            }
        exception.message.shouldNotBeNull() shouldContain "data integrity"
        realDatabase.close()
    }
}
