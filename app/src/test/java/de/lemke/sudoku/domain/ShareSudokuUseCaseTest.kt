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
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.data.DefaultSudokuShareFiles
import de.lemke.sudoku.data.database.FieldExport
import de.lemke.sudoku.data.database.SudokuExport
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import io.kjson.parseJSON
import io.kotest.matchers.shouldBe
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun testFields(size: SudokuSize): MutableList<Field> =
    (0 until size.cellCount)
        .map { index ->
            val solution = (index % size.value) + 1
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
    size: SudokuSize = SudokuSize.FOUR,
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
    private val useCase = ShareSudokuUseCase(DefaultSudokuShareFiles(context), UnconfinedTestDispatcher())

    @Test
    fun `invoke writes the exported sudoku as JSON to the returned cache file`() =
        runTest {
            val sudoku = testSudoku()

            val file = useCase(sudoku)

            file shouldBe File(context.cacheDir, "Sudoku (4×4 Hard).sudoku")
            file.readText().parseJSON<SudokuExport>() shouldBe
                SudokuExport(
                    id = "share-test-id",
                    size = 4,
                    difficulty = 3,
                    modeLevel = 0,
                    created = LocalDateTime.of(2024, 1, 1, 12, 0),
                    updated = LocalDateTime.of(2024, 1, 2, 13, 30),
                    seconds = 120,
                    checklistNumber = 2,
                    hintsUsed = 3,
                    notesMade = 5,
                    errorsMade = 1,
                    fields =
                        listOf(
                            FieldExport(index = 0, solution = 1, value = 1, given = true),
                            FieldExport(index = 1, solution = 2, hint = true),
                            FieldExport(index = 2, solution = 3, value = 3, notes = "12"),
                            FieldExport(index = 3, solution = 4, given = true),
                            FieldExport(index = 4, solution = 1, value = 1),
                            FieldExport(index = 5, solution = 2),
                            FieldExport(index = 6, solution = 3, value = 3, given = true),
                            FieldExport(index = 7, solution = 4),
                            FieldExport(index = 8, solution = 1, value = 1),
                            FieldExport(index = 9, solution = 2, given = true),
                            FieldExport(index = 10, solution = 3, value = 3),
                            FieldExport(index = 11, solution = 4),
                            FieldExport(index = 12, solution = 1, value = 1, given = true),
                            FieldExport(index = 13, solution = 2),
                            FieldExport(index = 14, solution = 3, value = 3),
                            FieldExport(index = 15, solution = 4, given = true),
                        ),
                )
        }

    @Test
    fun `invoke names the file after the sudoku's size and localized difficulty`() =
        runTest {
            val file = useCase(testSudoku(size = SudokuSize.NINE, difficulty = Difficulty.EASY))

            file shouldBe File(context.cacheDir, "Sudoku (9×9 Easy).sudoku")
            file.exists() shouldBe true
        }
}
