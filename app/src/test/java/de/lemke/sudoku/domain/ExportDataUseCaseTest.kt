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

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.data.database.SudokuExport
import de.lemke.sudoku.data.database.sudokuToExport
import de.lemke.sudoku.domain.model.ExportProgress
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testLevelSudoku
import io.kjson.parseJSON
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ExportDataUseCaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val progress = mutableListOf<ExportProgress>()
    private lateinit var getAllSudokus: GetAllSudokusUseCase
    private lateinit var useCase: ExportDataUseCase
    private lateinit var destinationFile: File

    @Before
    fun setUp() {
        getAllSudokus = mockk()
        useCase = ExportDataUseCase(context, getAllSudokus, UnconfinedTestDispatcher())
        destinationFile = File.createTempFile("export-data-use-case-test", ".json")
    }

    @After
    fun tearDown() {
        destinationFile.delete()
    }

    @Test
    fun `writes an empty JSON array when there are no sudokus`() =
        runTest {
            coEvery { getAllSudokus() } returns emptyList()

            useCase(Uri.fromFile(destinationFile)) { progress += it }

            destinationFile.readText().parseJSON<List<SudokuExport>>().shouldBeEmpty()
            progress shouldBe
                listOf(
                    ExportProgress.Reading,
                    ExportProgress.Converting(done = 0, total = 0),
                    ExportProgress.Writing,
                )
        }

    @Test
    fun `writes every sudoku as JSON that round-trips to the mapped export`() =
        runTest {
            val sudokus = listOf(testLevelSudoku(size = SudokuSize.NINE), testLevelSudoku(size = SudokuSize.FOUR))
            coEvery { getAllSudokus() } returns sudokus

            useCase(Uri.fromFile(destinationFile)) { progress += it }

            val exported = destinationFile.readText().parseJSON<List<SudokuExport>>()
            exported shouldBe sudokus.map { sudokuToExport(it) }
        }

    @Test
    fun `reports reading, each converted sudoku and writing in order`() =
        runTest {
            coEvery { getAllSudokus() } returns listOf(testLevelSudoku(size = SudokuSize.NINE), testLevelSudoku(size = SudokuSize.FOUR))

            useCase(Uri.fromFile(destinationFile)) { progress += it }

            progress shouldBe
                listOf(
                    ExportProgress.Reading,
                    ExportProgress.Converting(done = 0, total = 2),
                    ExportProgress.Converting(done = 1, total = 2),
                    ExportProgress.Converting(done = 2, total = 2),
                    ExportProgress.Writing,
                )
        }
}
