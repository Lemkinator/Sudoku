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

import android.app.Application
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
import de.lemke.sudoku.testSudoku
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SudokuViewModelRestartTest {
    private val rig = SudokuViewModelRig()
    private val today = LocalDateTime.of(2026, 1, 15, 0, 0)

    private fun lost(sudoku: Sudoku) = sudoku.apply { errorsMade = 3 }

    private fun restart(sudoku: Sudoku): SudokuViewModel {
        val viewModel = rig.playing(sudoku)
        viewModel.onRestart()
        idleMainLooper()
        return viewModel
    }

    private fun SudokuViewModel.shouldHaveRestarted(sudoku: Sudoku) {
        game.value shouldBe SudokuGame.Ready(sudoku)
        sudoku.errorsMade shouldBe 0
        rig.repository.saves.map { it.onlyUpdate } shouldBe listOf(false)
    }

    private fun SudokuViewModel.shouldHaveKept(
        sudoku: Sudoku,
        errorsMade: Int,
    ) {
        (game.value as SudokuGame.Playing).sudoku shouldBe sudoku
        sudoku.errorsMade shouldBe errorsMade
        rig.repository.saves shouldBe emptyList()
    }

    @Test
    fun `restartPolicy follows the decision table`() {
        val table =
            mapOf(
                SudokuKind.Normal to listOf(false, true, true, false),
                SudokuKind.Level(2) to listOf(false, false, true, false),
                SudokuKind.Daily(today.toLocalDate()) to listOf(false, false, true, false),
            )

        table.forEach { (kind, expected) ->
            Phase.entries.map { restartPolicy(kind, it) } shouldBe expected
        }
    }

    @Test
    fun `a lost normal sudoku restarts`() {
        val sudoku = lost(testSudoku())

        restart(sudoku).shouldHaveRestarted(sudoku)
    }

    @Test
    fun `a won normal sudoku restarts and loses its win`() {
        val sudoku = testSudoku(completed = true)

        restart(sudoku).shouldHaveRestarted(sudoku)
        sudoku.completed shouldBe false
    }

    @Test
    fun `a normal sudoku in progress does not restart`() {
        val sudoku = testSudoku().apply { errorsMade = 1 }

        restart(sudoku).shouldHaveKept(sudoku, 1)
    }

    @Test
    fun `a lost level restarts`() {
        val sudoku = lost(testSudoku(modeLevel = 3))

        restart(sudoku).shouldHaveRestarted(sudoku)
    }

    @Test
    fun `a level in progress does not restart`() {
        val sudoku = testSudoku(modeLevel = 3).apply { errorsMade = 2 }

        restart(sudoku).shouldHaveKept(sudoku, 2)
    }

    @Test
    fun `a won level does not restart`() {
        val sudoku = testSudoku(modeLevel = 3, completed = true)

        restart(sudoku).shouldHaveKept(sudoku, 0)
        sudoku.completed shouldBe true
    }

    @Test
    fun `a lost daily sudoku of today restarts`() {
        val sudoku = lost(testSudoku(modeLevel = MODE_DAILY, created = today))

        restart(sudoku).shouldHaveRestarted(sudoku)
    }

    @Test
    fun `a won daily sudoku of today does not restart`() {
        val sudoku = testSudoku(modeLevel = MODE_DAILY, created = today, completed = true)

        restart(sudoku).shouldHaveKept(sudoku, 0)
        sudoku.completed shouldBe true
    }

    @Test
    fun `a lost daily sudoku of an earlier day does not restart`() {
        val sudoku = lost(testSudoku(modeLevel = MODE_DAILY, created = today.minusDays(1)))

        restart(sudoku).shouldHaveKept(sudoku, 3)
    }

    @Test
    fun `a won daily sudoku of an earlier day does not restart`() {
        val sudoku = testSudoku(modeLevel = MODE_DAILY, created = today.minusDays(1), completed = true)

        restart(sudoku).shouldHaveKept(sudoku, 0)
        sudoku.completed shouldBe true
    }

    @Test
    fun `a lost daily sudoku of today still restarts after the clock passes midnight`() {
        val sudoku = lost(testSudoku(modeLevel = MODE_DAILY, created = today))
        val viewModel = rig.playing(sudoku)
        rig.clock.advanceTo(Instant.parse("2026-01-16T00:00:01Z"))

        viewModel.onResumed()
        viewModel.onRestart()
        idleMainLooper()

        viewModel.shouldHaveRestarted(sudoku)
    }

    @Test
    fun `a daily sudoku of today in progress does not restart`() {
        val sudoku = testSudoku(modeLevel = MODE_DAILY, created = today).apply { errorsMade = 1 }

        restart(sudoku).shouldHaveKept(sudoku, 1)
    }

    @Test
    fun `a daily sudoku of an earlier day in progress does not restart`() {
        val sudoku = testSudoku(modeLevel = MODE_DAILY, created = today.minusDays(1)).apply { errorsMade = 1 }

        restart(sudoku).shouldHaveKept(sudoku, 1)
    }
}
