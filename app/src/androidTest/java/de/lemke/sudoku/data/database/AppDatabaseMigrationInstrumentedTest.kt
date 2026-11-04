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
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.matchers.shouldBe
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val FOUR = 4
private const val DAY = "2026-10-01"

/** Runs the real 2 to 3 migration on a device against the exported schemas. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationInstrumentedTest {
    private val testDb = "migration-test-${System.nanoTime()}"

    @get:Rule
    val helper =
        MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = File(ApplicationProvider.getApplicationContext<Context>().getDatabasePath(testDb).path),
            driver = AndroidSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )

    private fun SQLiteConnection.ids(sql: String): List<String> =
        prepare(sql).use { statement -> generateSequence { statement.takeIf { it.step() }?.getText(0) }.toList() }

    @Test
    fun migrating2To3KeepsTheReadableGamesAndPicksOneGamePerLevelAndDay() {
        val open = patternFields(SudokuSize.FOUR)
        val won = patternFields(SudokuSize.FOUR, filled = { true })
        val connection = helper.createDatabase(2)
        LegacyDatabaseSeed(connection).apply {
            game("normal-4", FOUR, 0, "${DAY}T07:00:00", "${DAY}T08:00:00", open)
            game("level-1-won", FOUR, 1, "${DAY}T07:00:00", "${DAY}T08:00:00", won)
            game("level-1-open", FOUR, 1, "${DAY}T07:00:00", "${DAY}T10:00:00", open)
            game("daily-0900", FOUR, -1, "${DAY}T06:00:00", "${DAY}T09:00:00", open)
            game("daily-1000", FOUR, -1, "${DAY}T06:30:00", "${DAY}T10:00:00", open)
            game(
                "damaged-given",
                FOUR,
                0,
                "${DAY}T07:00:00",
                "${DAY}T08:00:00",
                open.map {
                    if (it.index ==
                        0
                    ) {
                        it.copy(value = FOUR)
                    } else {
                        it
                    }
                },
            )
        }
        connection.close()

        helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3)).use { migrated ->
            migrated.ids("SELECT id FROM game ORDER BY id") shouldBe listOf("daily-1000", "level-1-won", "normal-4")
            migrated.ids("SELECT id FROM game WHERE completed = 1") shouldBe listOf("level-1-won")
            migrated.ids("SELECT name FROM sqlite_master WHERE name IN ('sudoku', 'field')") shouldBe emptyList()
        }
    }
}
