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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room validates the schema only when reopening a file-backed database, never for in-memory ones.
 *
 * sdk = 36: Robolectric's max supported SDK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseValidationTest {
    @Test
    fun `reopening a persisted database validates its schema and keeps saved data intact`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "validation-test-${System.nanoTime()}"
        val sudokuId = "id-2"
        val now = LocalDateTime.now()

        Room
            .databaseBuilder(context, AppDatabase::class.java, dbName)
            .build()
            .apply {
                runBlocking {
                    sudokuDao().insert(
                        SudokuDb(
                            id = sudokuId,
                            size = 4,
                            difficulty = 0,
                            modeLevel = 0,
                            regionalHighlightingUsed = false,
                            numberHighlightingUsed = false,
                            eraserUsed = false,
                            isChecklist = false,
                            isReverseChecklist = false,
                            checklistNumber = 0,
                            hintsUsed = 0,
                            notesMade = 0,
                            errorsMade = 0,
                            created = now,
                            updated = now,
                            seconds = 0,
                        ),
                        emptyList(),
                    )
                }
                close()
            }

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val row = runBlocking { reopened.sudokuDao().getById(sudokuId) }.shouldNotBeNull()
        row.sudoku.id shouldBe sudokuId
        row.sudoku.size shouldBe 4
        row.sudoku.created shouldBe now
        reopened.close()

        context.deleteDatabase(dbName)
    }
}
