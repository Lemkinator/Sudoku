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
class SudokuObservationsRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var sudokusRepository: SudokusRepository
    private lateinit var observationsRepository: SudokuObservationsRepository

    @Before
    fun setUp() {
        // A same-thread executor makes Room's invalidation-tracker re-query (and thus each Flow emission) finish
        // synchronously with the write that triggered it, matching TestPersistenceModule's production test setup —
        // without it, a save's re-emission can land on a background thread after this test's assertions already ran.
        val directExecutor = Executor { it.run() }
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor(directExecutor)
                .setTransactionExecutor(directExecutor)
                .build()
        sudokusRepository = SudokusRepository(database.sudokuDao())
        observationsRepository = SudokuObservationsRepository(database.sudokuObserveDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sudoku(
        size: Int = 4,
        modeLevel: Int = Sudoku.MODE_NORMAL,
    ): Sudoku =
        Sudoku.create(
            size = size,
            difficulty = Difficulty.EASY,
            modeLevel = modeLevel,
            fields = MutableList(size * size) { Field(position = Position.create(it, size), solution = it % size + 1) },
        )

    @Test
    fun `observeAllSudokus starts empty and emits after a sudoku is saved`() =
        runTest {
            observationsRepository.observeAllSudokus().test {
                awaitItem().shouldBeEmpty()

                val saved = sudoku()
                sudokusRepository.saveSudoku(saved)

                awaitItem().single().id shouldBe saved.id
            }
        }

    @Test
    fun `observeAllNormalSudokus only emits normal-mode sudokus`() =
        runTest {
            observationsRepository.observeAllNormalSudokus().test {
                awaitItem().shouldBeEmpty()

                // Room's invalidation tracker fires on any write to the "sudoku" table, so a non-matching save still
                // re-runs (and re-emits) this filtered query — with the same, still-empty result.
                sudokusRepository.saveSudoku(sudoku(modeLevel = Sudoku.MODE_DAILY))
                awaitItem().shouldBeEmpty()

                sudokusRepository.saveSudoku(sudoku(modeLevel = Sudoku.MODE_NORMAL))
                awaitItem().single().isNormalSudoku shouldBe true
            }
        }

    @Test
    fun `observeSudokuLevel only emits level sudokus for the requested size`() =
        runTest {
            observationsRepository.observeSudokuLevel(4).test {
                awaitItem().shouldBeEmpty()

                sudokusRepository.saveSudoku(sudoku(size = 9, modeLevel = 1))
                awaitItem().shouldBeEmpty()

                sudokusRepository.saveSudoku(sudoku(size = 4, modeLevel = 1))
                awaitItem().single().size shouldBe 4
            }
        }

    @Test
    fun `observeDailySudokus only emits daily sudokus`() =
        runTest {
            observationsRepository.observeDailySudokus().test {
                awaitItem().shouldBeEmpty()

                sudokusRepository.saveSudoku(sudoku(modeLevel = Sudoku.MODE_NORMAL))
                awaitItem().shouldBeEmpty()

                sudokusRepository.saveSudoku(sudoku(modeLevel = Sudoku.MODE_DAILY))
                awaitItem().single().isDailySudoku shouldBe true
            }
        }
}
