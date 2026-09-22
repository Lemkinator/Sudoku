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

package de.lemke.sudoku.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import de.lemke.sudoku.data.database.AppDatabase
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import io.kotest.matchers.shouldBe
import java.util.concurrent.Executor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A real in-memory Room DB backs [SudokusRepository] here (rather than mocking it and a `Sudoku`
 * instance), so the assertion is against a genuine saved-and-observed row, not just whatever a mock was told
 * to return.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ObserveAllNormalSudokusUseCaseTest {
    private lateinit var database: AppDatabase
    private lateinit var sudokusRepository: SudokusRepository
    private lateinit var useCase: ObserveAllNormalSudokusUseCase

    @Before
    fun setUp() {
        val directExecutor = Executor { it.run() }
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor(directExecutor)
                .setTransactionExecutor(directExecutor)
                .build()
        sudokusRepository = SudokusRepository(database.sudokuDao())
        useCase = ObserveAllNormalSudokusUseCase(sudokusRepository, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun normalSudoku(
        size: Int = 4,
        modeLevel: Int = Sudoku.MODE_NORMAL,
    ): Sudoku =
        Sudoku.create(
            size = size,
            difficulty = Difficulty.EASY,
            modeLevel = modeLevel,
            fields = MutableList(size * size) { index -> Field(position = Position.create(index, size), solution = index % size + 1) },
        )

    @Test
    fun `passes through the repository's observeAllNormalSudokus flow`() =
        runTest {
            val sudoku = normalSudoku()
            sudokusRepository.saveSudoku(sudoku)

            useCase().test {
                val emitted = awaitItem()
                emitted.map { it.id } shouldBe listOf(sudoku.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `only normal-mode sudokus reach the flow`() =
        runTest {
            val normal = normalSudoku()
            val level = normalSudoku(modeLevel = 1)
            sudokusRepository.saveSudoku(normal)
            sudokusRepository.saveSudoku(level)

            useCase().test {
                val emitted = awaitItem()
                emitted.map { it.id } shouldBe listOf(normal.id)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
