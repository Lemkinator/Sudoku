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
 * Room only runs its generated `onValidateSchema` (comparing the live schema against every entity's expected
 * [androidx.room.util.TableInfo]) when opening a persisted, file-backed database — never for
 * [Room.inMemoryDatabaseBuilder], which every other test in this suite uses via `TestPersistenceModule` (in-memory
 * databases are always freshly created, so Room skips validation as redundant). Reopening a real file-backed
 * database, the way the production `PersistenceModule` builds one, is the only way to exercise that generated code.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
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

        // Reopening an already-created database file forces Room to validate the live schema against every
        // entity's expected TableInfo (AppDatabase_Impl's generated onValidateSchema), not just create it.
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        val row = runBlocking { reopened.sudokuDao().getById(sudokuId) }.shouldNotBeNull()
        row.sudoku.id shouldBe sudokuId
        row.sudoku.size shouldBe 4
        row.sudoku.created shouldBe now
        reopened.close()

        context.deleteDatabase(dbName)
    }
}
