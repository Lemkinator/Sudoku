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

import android.net.Uri
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import de.lemke.sudoku.data.database.SudokuExport
import de.lemke.sudoku.data.database.sudokuToExport
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A bare Application context satisfies neither of the two things the dialogs [ExportDataUseCase] shows
 * need: an AppCompat-themed context and a real Activity matching production's [dagger.hilt.android.qualifiers.ActivityContext].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExportDataUseCaseTest {
    private lateinit var context: AppCompatActivity
    private lateinit var getAllSudokus: GetAllSudokusUseCase
    private lateinit var useCase: ExportDataUseCase
    private lateinit var destinationFile: File

    @Before
    fun setUp() {
        context = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        getAllSudokus = mockk()
        useCase = ExportDataUseCase(context, getAllSudokus, UnconfinedTestDispatcher(), UnconfinedTestDispatcher())
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

            useCase(Uri.fromFile(destinationFile))
            shadowOf(Looper.getMainLooper()).idle()

            destinationFile.readText().parseJSON<List<SudokuExport>>().shouldBeEmpty()
        }

    @Test
    fun `writes every sudoku as JSON that round-trips to the mapped export`() =
        runTest {
            val sudokus = listOf(testLevelSudoku(size = 9), testLevelSudoku(size = 4))
            coEvery { getAllSudokus() } returns sudokus

            useCase(Uri.fromFile(destinationFile))
            shadowOf(Looper.getMainLooper()).idle()

            val exported = destinationFile.readText().parseJSON<List<SudokuExport>>()
            exported shouldBe sudokus.map { sudokuToExport(it) }
        }
}
