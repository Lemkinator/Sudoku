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
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.data.database.SudokuExport
import de.lemke.sudoku.data.database.sudokuToExport
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.resetFileProviderCache
import io.kjson.parseJSON
import io.kotest.matchers.shouldBe
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun testFields(size: Int): MutableList<Field> =
    (0 until size * size)
        .map { index ->
            val solution = (index % size) + 1
            Field(
                position = Position.create(index, size),
                solution = solution,
                value = if (index % 2 == 0) solution else null,
                given = index % 3 == 0,
                hint = index == 1,
                notes = if (index == 2) mutableListOf('1', '2') else mutableListOf(),
            )
        }.toMutableList()

private fun testSudoku(
    size: Int = 4,
    difficulty: Difficulty = Difficulty.HARD,
): Sudoku =
    Sudoku.create(
        sudokuId = SudokuId("share-test-id"),
        size = size,
        difficulty = difficulty,
        modeLevel = Sudoku.MODE_NORMAL,
        checklistNumber = 2,
        hintsUsed = 3,
        notesMade = 5,
        errorsMade = 1,
        seconds = 120,
        created = LocalDateTime.of(2024, 1, 1, 12, 0),
        updated = LocalDateTime.of(2024, 1, 2, 13, 30),
        fields = testFields(size),
    )

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ShareSudokuUseCaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val useCase = ShareSudokuUseCase(context, UnconfinedTestDispatcher())

    // FileProvider's SimplePathStrategy hardcodes '/' as separator, so this fails on a Windows JVM.
    @Before
    fun assumeUnixPaths() = assumeTrue(File.separatorChar == '/')

    @Before
    fun resetFileProviderStrategyCache() = resetFileProviderCache()

    @Test
    fun `invoke writes the exported sudoku as JSON readable back through the returned uri`() =
        runTest {
            val sudoku = testSudoku()

            val uri = useCase(sudoku)

            val json =
                context.contentResolver
                    .openInputStream(uri)!!
                    .bufferedReader()
                    .use { it.readText() }
            val parsed = json.parseJSON<SudokuExport>()
            parsed shouldBe sudokuToExport(sudoku)
        }

    @Test
    fun `invoke names the file after the sudoku's size and localized difficulty`() {
        val sudoku = testSudoku(size = 9, difficulty = Difficulty.EASY)
        val expectedName = "Sudoku (${sudoku.sizeString} ${sudoku.difficulty.getLocalString(context.resources)}).sudoku"

        lateinit var uri: Uri
        runTest { uri = useCase(sudoku) }

        val displayName =
            context.contentResolver.query(uri, null, null, null, null)!!.use { cursor ->
                cursor.moveToFirst()
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        displayName shouldBe expectedName
        File(context.cacheDir, expectedName).exists() shouldBe true
    }
}
