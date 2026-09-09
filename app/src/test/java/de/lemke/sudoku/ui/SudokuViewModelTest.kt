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

package de.lemke.sudoku.ui

import android.net.Uri
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.GetSudokuUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.ShareSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class SudokuViewModelTest : ShouldSpec(
    {
        val getSudoku = mockk<GetSudokuUseCase>()
        val generateSudoku = mockk<GenerateSudokuUseCase>()
        val generateSudokuLevel = mockk<GenerateSudokuLevelUseCase>()
        val getMaxSudokuLevel = mockk<GetMaxSudokuLevelUseCase>()
        val saveSudoku = mockk<SaveSudokuUseCase>(relaxUnitFun = true)
        val shareSudoku = mockk<ShareSudokuUseCase>()
        val calculatePlayGamesSync = mockk<CalculatePlayGamesSyncUseCase>()
        lateinit var viewModel: SudokuViewModel

        beforeEach {
            clearMocks(getSudoku, generateSudoku, generateSudokuLevel, getMaxSudokuLevel, saveSudoku, shareSudoku, calculatePlayGamesSync)
            viewModel =
                SudokuViewModel(
                    getSudoku,
                    generateSudoku,
                    generateSudokuLevel,
                    getMaxSudokuLevel,
                    saveSudoku,
                    shareSudoku,
                    calculatePlayGamesSync,
                )
        }

        should("loadSudoku delegates to getSudoku and returns its result") {
            val id = SudokuId("id-1")
            val sudoku = mockk<Sudoku>()
            coEvery { getSudoku(id) } returns sudoku
            viewModel.loadSudoku(id) shouldBe sudoku
            coVerify(exactly = 1) { getSudoku(id) }
        }

        should("loadSudoku returns null when getSudoku finds nothing") {
            val id = SudokuId("id-2")
            coEvery { getSudoku(id) } returns null
            viewModel.loadSudoku(id) shouldBe null
        }

        should("generateNewSudoku delegates to generateSudoku and returns its result") {
            val sudoku = mockk<Sudoku>()
            coEvery { generateSudoku(9, Difficulty.MEDIUM) } returns sudoku
            viewModel.generateNewSudoku(9, Difficulty.MEDIUM) shouldBe sudoku
            coVerify(exactly = 1) { generateSudoku(9, Difficulty.MEDIUM) }
        }

        should("generateNextLevelSudoku delegates to generateSudokuLevel and returns its result") {
            val sudoku = mockk<Sudoku>()
            coEvery { generateSudokuLevel(4, 3) } returns sudoku
            viewModel.generateNextLevelSudoku(4, 3) shouldBe sudoku
            coVerify(exactly = 1) { generateSudokuLevel(4, 3) }
        }

        should("isMaxSudokuLevel returns true when getMaxSudokuLevel equals the given level") {
            coEvery { getMaxSudokuLevel(9) } returns 5
            viewModel.isMaxSudokuLevel(9, 5) shouldBe true
        }

        should("isMaxSudokuLevel returns false when getMaxSudokuLevel differs from the given level") {
            coEvery { getMaxSudokuLevel(9) } returns 5
            viewModel.isMaxSudokuLevel(9, 4) shouldBe false
        }

        should("saveSudokuProgress delegates to saveSudoku with onlyUpdate defaulting to false") {
            val sudoku = mockk<Sudoku>()
            viewModel.saveSudokuProgress(sudoku)
            coVerify(exactly = 1) { saveSudoku(sudoku, false) }
        }

        should("saveSudokuProgress delegates to saveSudoku with an explicit onlyUpdate = true") {
            val sudoku = mockk<Sudoku>()
            viewModel.saveSudokuProgress(sudoku, onlyUpdate = true)
            coVerify(exactly = 1) { saveSudoku(sudoku, true) }
        }

        should("exportSudoku delegates to shareSudoku and returns its result") {
            val sudoku = mockk<Sudoku>()
            val uri = mockk<Uri>()
            coEvery { shareSudoku(sudoku) } returns uri
            viewModel.exportSudoku(sudoku) shouldBe uri
            coVerify(exactly = 1) { shareSudoku(sudoku) }
        }

        should("syncPlayGames delegates to calculatePlayGamesSync with sudoku defaulting to null") {
            val sync = mockk<PlayGamesSync>()
            coEvery { calculatePlayGamesSync(null) } returns sync
            viewModel.syncPlayGames() shouldBe sync
            coVerify(exactly = 1) { calculatePlayGamesSync(null) }
        }

        should("syncPlayGames delegates to calculatePlayGamesSync with an explicit sudoku") {
            val sudoku = mockk<Sudoku>()
            val sync = mockk<PlayGamesSync>()
            coEvery { calculatePlayGamesSync(sudoku) } returns sync
            viewModel.syncPlayGames(sudoku) shouldBe sync
            coVerify(exactly = 1) { calculatePlayGamesSync(sudoku) }
        }
    },
)
