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

package de.lemke.sudoku.domain.model

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.R
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun statisticsSudoku(
    modeLevel: Int,
    completed: Boolean = false,
    numberHighlightingUsed: Boolean = false,
): Sudoku {
    val created = LocalDateTime.of(2024, 1, 1, 12, 0)
    return Sudoku.create(
        size = 4,
        difficulty = Difficulty.EASY,
        modeLevel = modeLevel,
        numberHighlightingUsed = numberHighlightingUsed,
        errorsMade = 2,
        hintsUsed = 1,
        notesMade = 3,
        seconds = 90,
        created = created,
        updated = created.plusHours(1),
        fields =
            (0 until 16)
                .map { index ->
                    val solution = (index % 4) + 1
                    Field(position = Position.create(index, 4), solution = solution, value = if (completed) solution else null)
                }.toMutableList(),
    )
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SudokuLocalStatisticsStringTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `getLocalStatisticsString names a normal sudoku by its type`() {
        val sudoku = statisticsSudoku(modeLevel = Sudoku.MODE_NORMAL)

        val text = sudoku.getLocalStatisticsString(context.resources)

        text.contains(context.resources.getString(R.string.normal_sudoku)) shouldBe true
        text.contains(sudoku.sizeString) shouldBe true
        text.contains(sudoku.difficulty.getLocalString(context.resources)) shouldBe true
        text.contains(sudoku.timeString) shouldBe true
    }

    @Test
    fun `getLocalStatisticsString names a daily sudoku by its type`() {
        val sudoku = statisticsSudoku(modeLevel = Sudoku.MODE_DAILY)

        val text = sudoku.getLocalStatisticsString(context.resources)

        text.contains(context.resources.getString(R.string.daily_sudoku)) shouldBe true
    }

    @Test
    fun `getLocalStatisticsString names a level sudoku by its level number`() {
        val sudoku = statisticsSudoku(modeLevel = 5)

        val text = sudoku.getLocalStatisticsString(context.resources)

        text.contains("${context.resources.getString(R.string.level)} 5") shouldBe true
    }

    @Test
    fun `getLocalStatisticsString reports number highlighting used`() {
        val sudoku = statisticsSudoku(modeLevel = Sudoku.MODE_NORMAL, numberHighlightingUsed = true)

        val text = sudoku.getLocalStatisticsString(context.resources)

        text.contains(context.resources.getString(R.string.commonutils_yes)) shouldBe true
    }

    @Test
    fun `getLocalStatisticsStringShare reports completion for a completed sudoku`() {
        val sudoku = statisticsSudoku(modeLevel = Sudoku.MODE_NORMAL, completed = true)

        val text = sudoku.getLocalStatisticsStringShare(context.resources)

        text.startsWith(context.resources.getString(R.string.sudoku_completed)) shouldBe true
    }

    @Test
    fun `getLocalStatisticsStringShare reports progress for an in-progress sudoku`() {
        val sudoku = statisticsSudoku(modeLevel = Sudoku.MODE_NORMAL, completed = false)

        val text = sudoku.getLocalStatisticsStringShare(context.resources)

        text.startsWith(context.resources.getString(R.string.sudoku_solving, sudoku.progress)) shouldBe true
    }
}
