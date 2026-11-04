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
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.system.measureTimeMillis
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SIXTEEN_COUNT = 400
private const val NINE_COUNT = 4_000
private const val MAX_BYTES_AFTER_VACUUM = 2_000_000L

/** A library of a heavy player: every game completed, 400 of 16x16 and 4,000 of 9x9. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationMeasurementTest {
    private val testDb = "measurement-test-${System.nanoTime()}"
    private val dbFile = File(ApplicationProvider.getApplicationContext<Context>().getDatabasePath(testDb).path)

    @get:Rule
    val helper =
        MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            file = dbFile,
            driver = AndroidSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )

    private fun seedLibrary(connection: SQLiteConnection) {
        val seed = LegacyDatabaseSeed(connection)
        val nineFields = patternFields(SudokuSize.NINE, filled = { true })
        val sixteenFields = patternFields(SudokuSize.SIXTEEN, filled = { true })
        seed.begin()
        repeat(SIXTEEN_COUNT) { seed.game("sixteen-$it", 16, 0, "2026-01-01T08:00:00", "2026-01-01T09:00:00", sixteenFields) }
        repeat(NINE_COUNT) { seed.game("nine-$it", 9, 0, "2026-01-01T08:00:00", "2026-01-01T09:00:00", nineFields) }
        seed.commit()
    }

    private fun SQLiteConnection.long(sql: String): Long =
        prepare(sql).use {
            it.step()
            it.getLong(0)
        }

    @Test
    fun `4400 games migrate into less than 2 MB`() {
        val connection = helper.createDatabase(2)
        seedLibrary(connection)
        connection.execSQL("VACUUM")
        connection.close()
        val bytesBefore = dbFile.length()

        var migratedConnection: SQLiteConnection
        val millis = measureTimeMillis { migratedConnection = helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3)) }

        migratedConnection.use {
            val kept = it.long("SELECT COUNT(*) FROM game")
            it.execSQL("VACUUM")
            it.close()
            val bytesAfter = dbFile.length()
            println("MIGRATION_MEASUREMENT kept=$kept bytesBefore=$bytesBefore bytesAfterVacuum=$bytesAfter millis=$millis")
            kept shouldBe 4_400L
            bytesAfter shouldBeLessThan MAX_BYTES_AFTER_VACUUM
        }
    }

    @Test
    fun `a fresh 9x9 game stores a 130-byte board`() {
        val connection = helper.createDatabase(2)
        LegacyDatabaseSeed(connection).apply {
            game("nine-0", 9, 0, "2026-01-01T08:00:00", "2026-01-01T09:00:00", patternFields(SudokuSize.NINE))
            game("sixteen-0", 16, 0, "2026-01-01T08:00:00", "2026-01-01T09:00:00", patternFields(SudokuSize.SIXTEEN))
        }
        connection.close()

        helper.runMigrationsAndValidate(3, listOf(MIGRATION_2_3)).use {
            it.long("SELECT LENGTH(board) FROM game WHERE id = 'nine-0'") shouldBe 130L
            it.long("SELECT LENGTH(board) FROM game WHERE id = 'sixteen-0'") shouldBe 394L
        }
    }
}
