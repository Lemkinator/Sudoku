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
import de.lemke.sudoku.testSudoku
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun statisticsSudoku(
    mode: GameMode,
    completed: Boolean = false,
    numberHighlightingUsed: Boolean = false,
): Sudoku {
    val created = LocalDateTime.of(2024, 1, 1, 12, 0)
    val base = testSudoku(size = SudokuSize.FOUR, mode = mode, difficulty = Difficulty.EASY, created = created, completed = completed)
    return base.copy(
        info =
            base.info.copy(
                updated = created.plusHours(1),
                seconds = 90,
                errorsMade = 2,
                hintsUsed = 1,
                notesMade = 3,
                numberHighlightingUsed = numberHighlightingUsed,
            ),
    )
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SudokuLocalStatisticsStringTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `getLocalStatisticsString names a normal sudoku by its type`() {
        val sudoku = statisticsSudoku(mode = GameMode.Normal)

        val text = sudoku.getLocalStatisticsString(context.resources)

        text.contains(context.resources.getString(R.string.normal_sudoku)) shouldBe true
        text.contains("Size: 4×4") shouldBe true
        text.contains(sudoku.info.difficulty.getLocalString(context.resources)) shouldBe true
        text.contains(sudoku.info.timeString) shouldBe true
    }

    @Test
    fun `getLocalStatisticsString names a daily sudoku by its type`() {
        val sudoku = statisticsSudoku(mode = GameMode.Daily)

        val text = sudoku.getLocalStatisticsString(context.resources)

        text.contains(context.resources.getString(R.string.daily_sudoku)) shouldBe true
    }

    @Test
    fun `getLocalStatisticsString names a level sudoku by its level number`() {
        val sudoku = statisticsSudoku(mode = GameMode.Level(5))

        val text = sudoku.getLocalStatisticsString(context.resources)

        text.lines().first() shouldBe "Type: Level 5 "
    }

    @Test
    @Config(qualifiers = "es")
    fun `getLocalStatisticsString names a level sudoku by its level number in Spanish`() {
        val sudoku = statisticsSudoku(mode = GameMode.Level(5))

        val text = sudoku.getLocalStatisticsString(context.resources)

        text.lines().first() shouldBe "Tipo: Nivel 5 "
    }

    @Test
    @Config(qualifiers = "de")
    fun `getLocalStatisticsString names a level sudoku by its level number in German`() {
        val sudoku = statisticsSudoku(mode = GameMode.Level(5))

        val text = sudoku.getLocalStatisticsString(context.resources)

        text.lines().first() shouldBe "Typ: Level 5 "
    }

    @Test
    fun `getLocalStatisticsString reports number highlighting used`() {
        val sudoku = statisticsSudoku(mode = GameMode.Normal, numberHighlightingUsed = true)

        val text = sudoku.getLocalStatisticsString(context.resources)

        text.contains(context.resources.getString(R.string.commonutils_yes)) shouldBe true
    }

    @Test
    fun `getLocalStatisticsStringShare reports completion for a completed sudoku`() {
        val sudoku = statisticsSudoku(mode = GameMode.Normal, completed = true)

        val text = sudoku.getLocalStatisticsStringShare(context.resources)

        text.startsWith(context.resources.getString(R.string.sudoku_completed)) shouldBe true
    }

    @Test
    fun `getLocalStatisticsStringShare reports progress for an in-progress sudoku`() {
        val sudoku = statisticsSudoku(mode = GameMode.Normal, completed = false)

        val text = sudoku.getLocalStatisticsStringShare(context.resources)

        text.startsWith(context.resources.getString(R.string.sudoku_solving, sudoku.progress)) shouldBe true
    }
}
