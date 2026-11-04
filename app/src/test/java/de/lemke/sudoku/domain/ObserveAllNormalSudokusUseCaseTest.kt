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
import de.lemke.sudoku.data.database.DefaultSudokusRepository
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.util.concurrent.Executor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        sudokusRepository = DefaultSudokusRepository(database.gameDao(), Clock.systemDefaultZone())
        useCase = ObserveAllNormalSudokusUseCase(sudokusRepository, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun normalSudoku(
        size: SudokuSize = SudokuSize.FOUR,
        mode: GameMode = GameMode.Normal,
    ): Sudoku = testSudoku(size = size, mode = mode, difficulty = Difficulty.EASY)

    @Test
    fun `passes through the repository's observeAllNormalSudokus flow`() =
        runTest {
            val sudoku = normalSudoku()
            sudokusRepository.saveSudoku(sudoku)

            useCase().test {
                val emitted = awaitItem()
                emitted.map { it.info.id } shouldBe listOf(sudoku.info.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `only normal-mode sudokus reach the flow`() =
        runTest {
            val normal = normalSudoku()
            val level = normalSudoku(mode = GameMode.Level(1))
            sudokusRepository.saveSudoku(normal)
            sudokusRepository.saveSudoku(level)

            useCase().test {
                val emitted = awaitItem()
                emitted.map { it.info.id } shouldBe listOf(normal.info.id)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
