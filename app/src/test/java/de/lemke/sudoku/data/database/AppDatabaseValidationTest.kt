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
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.testSudoku
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room validates the schema only when reopening a file-backed database, never for in-memory ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseValidationTest {
    @Test
    fun `reopening a persisted database validates its schema and keeps saved data intact`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "validation-test-${System.nanoTime()}"
        val sudoku = testSudoku(created = LocalDateTime.of(2026, 3, 14, 12, 0, 5))

        Room
            .databaseBuilder(context, AppDatabase::class.java, dbName)
            .build()
            .apply {
                runBlocking { gameDao().save(sudoku.toEntity(ZoneOffset.UTC)) }
                close()
            }

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val game = runBlocking { reopened.gameDao().getById(sudoku.info.id.value) }.shouldNotBeNull()
        game.header.size shouldBe 4
        game.toSudoku(ZoneOffset.UTC) shouldBe sudoku
        reopened.close()

        context.deleteDatabase(dbName)
    }
}
