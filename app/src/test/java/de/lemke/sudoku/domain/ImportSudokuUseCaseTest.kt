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
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.data.database.sudokuToExport
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import io.kjson.stringifyJSON
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ImportSudokuUseCaseTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val saveSudoku = mockk<SaveSudokuUseCase>(relaxUnitFun = true)
    private val useCase = ImportSudokuUseCase(context, saveSudoku, UnconfinedTestDispatcher())

    @Before
    fun setUp() {
        clearMocks(saveSudoku)
    }

    private fun testSudoku(size: Int = 4): Sudoku =
        Sudoku.create(
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = Sudoku.MODE_NORMAL,
            fields =
                MutableList(size * size) { i ->
                    Field(position = Position.create(i, size), solution = (i % size) + 1, value = (i % size) + 1, given = true)
                },
        )

    private fun uriFor(content: String): Uri {
        val file = File.createTempFile("sudoku-import", ".json")
        file.writeText(content)
        file.deleteOnExit()
        return Uri.fromFile(file)
    }

    @Test
    fun `returns null and never saves for a null uri`() =
        runTest {
            useCase(null).shouldBeNull()

            coVerify(exactly = 0) { saveSudoku(any(), any()) }
        }

    @Test
    fun `imports a valid export, saves it and returns the mapped sudoku`() =
        runTest {
            val sudoku = testSudoku()
            val uri = uriFor(sudokuToExport(sudoku).stringifyJSON())

            val result = useCase(uri)

            result.shouldNotBeNull()
            result.id shouldBe sudoku.id
            result.fields.size shouldBe sudoku.fields.size
            coVerify(exactly = 1) { saveSudoku(sudoku, false) }
        }

    @Test
    fun `returns null and never saves when content fails schema validation`() =
        runTest {
            val uri = uriFor("""{"foo":"bar"}""")

            useCase(uri).shouldBeNull()

            coVerify(exactly = 0) { saveSudoku(any(), any()) }
        }

    @Test
    fun `returns null and never saves when the mapped field count does not match size`() =
        runTest {
            val export = sudokuToExport(testSudoku())
            val uri = uriFor(export.copy(fields = export.fields.take(1)).stringifyJSON())

            useCase(uri).shouldBeNull()

            coVerify(exactly = 0) { saveSudoku(any(), any()) }
        }

    @Test
    fun `returns null instead of throwing when the file cannot be opened`() =
        runTest {
            val uri = Uri.fromFile(File(context.cacheDir, "does-not-exist-${System.nanoTime()}.json"))

            useCase(uri).shouldBeNull()

            coVerify(exactly = 0) { saveSudoku(any(), any()) }
        }
}
