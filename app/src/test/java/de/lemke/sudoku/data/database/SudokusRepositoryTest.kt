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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
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
    private lateinit var repository: SudokusRepository

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
        repository = SudokusRepository(database.sudokuDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sudoku(
        size: Int = 4,
        modeLevel: Int = Sudoku.MODE_NORMAL,
        created: LocalDateTime = LocalDateTime.now(),
        updated: LocalDateTime = created,
    ): Sudoku =
        Sudoku.create(
            size = size,
            difficulty = Difficulty.EASY,
            modeLevel = modeLevel,
            created = created,
            updated = updated,
            fields = MutableList(size * size) { Field(position = Position.create(it, size), solution = it % size + 1) },
        )

    @Test
    fun `getAllSudokus returns an empty list when nothing was saved`() =
        runTest {
            repository.getAllSudokus().shouldBeEmpty()
        }

    @Test
    fun `saveSudoku then getAllSudokus round-trips the sudoku`() =
        runTest {
            val saved = sudoku()
            repository.saveSudoku(saved)

            repository.getAllSudokus().single().id shouldBe saved.id
        }

    @Test
    fun `getSudokuById returns null for an unknown id`() =
        runTest {
            repository.getSudokuById(sudoku().id).shouldBeNull()
        }

    @Test
    fun `getSudokuById returns the matching sudoku`() =
        runTest {
            val saved = sudoku()
            repository.saveSudoku(saved)

            repository.getSudokuById(saved.id)?.id shouldBe saved.id
        }

    @Test
    fun `getRecentlyUpdatedNormalSudoku returns null when there are no normal sudokus`() =
        runTest {
            repository.saveSudoku(sudoku(modeLevel = 1))

            repository.getRecentlyUpdatedNormalSudoku().shouldBeNull()
        }

    @Test
    fun `getRecentlyUpdatedNormalSudoku returns the most recently updated normal sudoku`() =
        runTest {
            val older = sudoku(updated = LocalDateTime.now().minusDays(1))
            val newer = sudoku(updated = LocalDateTime.now())
            repository.saveSudoku(older)
            repository.saveSudoku(newer)

            repository.getRecentlyUpdatedNormalSudoku()?.id shouldBe newer.id
        }

    @Test
    fun `getSudokuLevel returns level sudokus for the given size ordered by level descending`() =
        runTest {
            repository.saveSudoku(sudoku(size = 4, modeLevel = 1))
            repository.saveSudoku(sudoku(size = 4, modeLevel = 2))
            repository.saveSudoku(sudoku(size = 9, modeLevel = 1))

            repository.getSudokuLevel(4).map { it.modeLevel } shouldBe listOf(2, 1)
        }

    @Test
    fun `getDailySudokus returns only daily sudokus`() =
        runTest {
            repository.saveSudoku(sudoku(modeLevel = Sudoku.MODE_DAILY))
            repository.saveSudoku(sudoku(modeLevel = Sudoku.MODE_NORMAL))

            val dailies = repository.getDailySudokus()
            dailies shouldHaveSize 1
            dailies.single().isDailySudoku shouldBe true
        }

    @Test
    fun `deleteSudoku removes the sudoku and its fields`() =
        runTest {
            val saved = sudoku()
            repository.saveSudoku(saved)

            repository.deleteSudoku(saved)

            repository.getAllSudokus().shouldBeEmpty()
        }

    @Test
    fun `saveSudoku replaces the existing daily sudoku for the same day`() =
        runTest {
            val day = LocalDateTime.now()
            val first = sudoku(modeLevel = Sudoku.MODE_DAILY, created = day)
            repository.saveSudoku(first)

            val second = sudoku(modeLevel = Sudoku.MODE_DAILY, created = day)
            repository.saveSudoku(second)

            val all = repository.getAllSudokus()
            all shouldHaveSize 1
            all.single().id shouldBe second.id
        }

    @Test
    fun `saveSudoku keeps daily sudokus from different days`() =
        runTest {
            val first = sudoku(modeLevel = Sudoku.MODE_DAILY, created = LocalDateTime.now().minusDays(1))
            repository.saveSudoku(first)

            val second = sudoku(modeLevel = Sudoku.MODE_DAILY, created = LocalDateTime.now())
            repository.saveSudoku(second)

            repository.getAllSudokus() shouldHaveSize 2
        }

    @Test
    fun `saveSudoku replaces the existing level sudoku for the same size and level`() =
        runTest {
            val first = sudoku(size = 4, modeLevel = 3)
            repository.saveSudoku(first)

            val second = sudoku(size = 4, modeLevel = 3)
            repository.saveSudoku(second)

            val all = repository.getAllSudokus()
            all shouldHaveSize 1
            all.single().id shouldBe second.id
        }

    @Test
    fun `saveSudoku does not delete the daily sudoku being updated`() =
        runTest {
            val daily = sudoku(modeLevel = Sudoku.MODE_DAILY)
            repository.saveSudoku(daily)

            repository.saveSudoku(daily)

            val all = repository.getAllSudokus()
            all shouldHaveSize 1
            all.single().id shouldBe daily.id
        }

    @Test
    fun `saveSudoku does not delete the level sudoku being updated`() =
        runTest {
            val level = sudoku(size = 4, modeLevel = 3)
            repository.saveSudoku(level)

            repository.saveSudoku(level)

            val all = repository.getAllSudokus()
            all shouldHaveSize 1
            all.single().id shouldBe level.id
        }

    @Test
    fun `saveSudoku with onlyUpdate true skips the level dedup even for a matching level`() =
        runTest {
            val first = sudoku(size = 4, modeLevel = 3)
            repository.saveSudoku(first)

            val second = sudoku(size = 4, modeLevel = 3)
            repository.saveSudoku(second, onlyUpdate = true)

            repository.getAllSudokus() shouldHaveSize 2
        }

    @Test
    fun `getMaxSudokuLevel returns 0 when no level sudoku exists for the size`() =
        runTest {
            repository.getMaxSudokuLevel(4) shouldBe 0
        }

    @Test
    fun `getMaxSudokuLevel returns the highest existing level for the size`() =
        runTest {
            repository.saveSudoku(sudoku(size = 4, modeLevel = 1))
            repository.saveSudoku(sudoku(size = 4, modeLevel = 5))

            repository.getMaxSudokuLevel(4) shouldBe 5
        }

    @Test
    fun `getMaxSudokuLevel ignores daily sudokus`() =
        runTest {
            repository.saveSudoku(sudoku(size = 9, modeLevel = Sudoku.MODE_DAILY))

            repository.getMaxSudokuLevel(9) shouldBe 0
        }

    @Test
    fun `deleteInvalidSudokus leaves sudokus whose stored field count matches size squared untouched`() =
        runTest {
            val valid = sudoku(size = 4)
            repository.saveSudoku(valid)

            repository.deleteInvalidSudokus()

            database.sudokuDao().getAll() shouldHaveSize 1
        }

    @Test
    fun `deleteInvalidSudokus deletes a row whose stored field count does not match size squared and keeps valid rows`() =
        runTest {
            val valid = sudoku(size = 4)
            repository.saveSudoku(valid)
            val invalid = sudoku(size = 4)
            database.sudokuDao().insert(
                sudokuToDb(invalid),
                invalid.fields.take(invalid.fields.size - 1).map { fieldToDb(it, invalid.id) },
            )
            database.sudokuDao().getAll() shouldHaveSize 2

            repository.deleteInvalidSudokus()

            val remaining = database.sudokuDao().getAll()
            remaining shouldHaveSize 1
            remaining.single().sudoku.id shouldBe valid.id.value
        }

    @Test
    fun `deleteInvalidSudokus deletes a row with a correct field count but a null solution and keeps valid rows`() =
        runTest {
            val valid = sudoku(size = 4)
            repository.saveSudoku(valid)
            val invalid = sudoku(size = 4)
            val fields =
                invalid.fields
                    .map { fieldToDb(it, invalid.id) }
                    .mapIndexed { index, field -> if (index == 0) field.copy(solution = null) else field }
            database.sudokuDao().insert(sudokuToDb(invalid), fields)
            database.sudokuDao().getAll() shouldHaveSize 2

            repository.deleteInvalidSudokus()

            val remaining = database.sudokuDao().getAll()
            remaining shouldHaveSize 1
            remaining.single().sudoku.id shouldBe valid.id.value
        }

    @Test
    fun `deleteInvalidSudokus deletes a zero-sized corrupted row instead of throwing`() =
        runTest {
            val valid = sudoku(size = 4)
            repository.saveSudoku(valid)
            val invalid = sudoku(size = 4)
            database.sudokuDao().insert(
                sudokuToDb(invalid).copy(size = 0),
                listOf(fieldToDb(invalid.fields.first(), invalid.id).copy(gameSize = 0)),
            )
            database.sudokuDao().getAll() shouldHaveSize 2

            repository.deleteInvalidSudokus()

            val remaining = database.sudokuDao().getAll()
            remaining shouldHaveSize 1
            remaining.single().sudoku.id shouldBe valid.id.value
        }

    @Test
    fun `sudokuDao insert and getAll round-trip a raw field row with a null solution`() =
        runTest {
            val sudoku = sudoku(size = 4)
            val fields =
                sudoku.fields
                    .map { fieldToDb(it, sudoku.id) }
                    .mapIndexed { index, field -> if (index == 0) field.copy(solution = null) else field }
            database.sudokuDao().insert(sudokuToDb(sudoku), fields)

            val fetched = database.sudokuDao().getAll().single()

            fetched.fields.single { it.index == 0 }.solution shouldBe null
        }

    @Test
    fun `observeAllSudokus starts empty and emits after a sudoku is saved`() =
        runTest {
            repository.observeAllSudokus().test {
                awaitItem().shouldBeEmpty()

                val saved = sudoku()
                repository.saveSudoku(saved)

                awaitItem().single().id shouldBe saved.id
            }
        }

    @Test
    fun `observeAllNormalSudokus only emits normal-mode sudokus`() =
        runTest {
            repository.observeAllNormalSudokus().test {
                awaitItem().shouldBeEmpty()

                // Room's invalidation tracker re-emits on any write to the table.
                repository.saveSudoku(sudoku(modeLevel = Sudoku.MODE_DAILY))
                awaitItem().shouldBeEmpty()

                repository.saveSudoku(sudoku(modeLevel = Sudoku.MODE_NORMAL))
                awaitItem().single().isNormalSudoku shouldBe true
            }
        }

    @Test
    fun `observeSudokuLevel only emits level sudokus for the requested size`() =
        runTest {
            repository.observeSudokuLevel(4).test {
                awaitItem().shouldBeEmpty()

                repository.saveSudoku(sudoku(size = 9, modeLevel = 1))
                awaitItem().shouldBeEmpty()

                repository.saveSudoku(sudoku(size = 4, modeLevel = 1))
                awaitItem().single().size shouldBe 4
            }
        }

    @Test
    fun `observeDailySudokus only emits daily sudokus`() =
        runTest {
            repository.observeDailySudokus().test {
                awaitItem().shouldBeEmpty()

                repository.saveSudoku(sudoku(modeLevel = Sudoku.MODE_NORMAL))
                awaitItem().shouldBeEmpty()

                repository.saveSudoku(sudoku(modeLevel = Sudoku.MODE_DAILY))
                awaitItem().single().isDailySudoku shouldBe true
            }
        }

    @Test
    fun `sudokuDao observeAll round-trips a raw field row with a null solution`() =
        runTest {
            val sudoku = sudoku(size = 4)
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
