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

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SudokusRepositoryTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        // A same-thread executor keeps Room's invalidation re-query synchronous with the triggering write.
        val directExecutor = Executor { it.run() }
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor(directExecutor)
                .setTransactionExecutor(directExecutor)
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sudoku(): Sudoku =
        Sudoku.create(
            size = SudokuSize.FOUR,
            difficulty = Difficulty.EASY,
            modeLevel = Sudoku.MODE_NORMAL,
            fields =
                MutableList(
                    SudokuSize.FOUR.cellCount,
                ) { Field(position = Position.create(it, SudokuSize.FOUR), solution = it % 4 + 1) },
        )

    @Test
    fun `sudokuDao insert and getAll round-trip a raw field row with a null solution`() =
        runTest {
            val sudoku = sudoku()
            val fields =
                sudoku.fields
                    .map { fieldToDb(it, sudoku.id) }
                    .mapIndexed { index, field -> if (index == 0) field.copy(solution = null) else field }
            database.sudokuDao().insert(sudokuToDb(sudoku), fields)

            val fetched = database.sudokuDao().getAll().single()

            fetched.fields.single { it.index == 0 }.solution shouldBe null
        }

    @Test
    fun `sudokuDao observeAll round-trips a raw field row with a null solution`() =
        runTest {
            val sudoku = sudoku()
            val fields =
                sudoku.fields
                    .map { fieldToDb(it, sudoku.id) }
                    .mapIndexed { index, field -> if (index == 0) field.copy(solution = null) else field }

            database.sudokuDao().observeAll().test {
                awaitItem().shouldBeEmpty()

                database.sudokuDao().insert(sudokuToDb(sudoku), fields)

                awaitItem()
                    .single()
                    .fields
                    .single { it.index == 0 }
                    .solution shouldBe null
            }
        }
}
