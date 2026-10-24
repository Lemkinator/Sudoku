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
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SudokuViewModelPhaseTest {
    private val rig = SudokuViewModelRig()
    private val clock = rig.clock
    private val userSettings = rig.userSettings
    private val today = LocalDateTime.of(2026, 1, 15, 0, 0)

    private fun playing(sudoku: Sudoku) = rig.playing(sudoku)

    private fun daily(created: LocalDateTime) = testSudoku(modeLevel = MODE_DAILY, created = created)

    @Test
    fun `a normal sudoku in progress is playing with the limit of the settings`() {
        userSettings.errorLimit = 5

        playing(testSudoku()).game.value.shouldBePlaying(SudokuKind.Normal, 5, Phase.Playing)
    }

    @Test
    fun `a normal sudoku at the limit of the settings is lost`() {
        userSettings.errorLimit = 2
        val sudoku = testSudoku().apply { errorsMade = 2 }

        playing(sudoku).game.value.shouldBePlaying(SudokuKind.Normal, 2, Phase.Lost)
    }

    @Test
    fun `a normal sudoku without an error limit is never lost`() {
        userSettings.errorLimit = 0
        val sudoku = testSudoku().apply { errorsMade = 99 }

        playing(sudoku).game.value.shouldBePlaying(SudokuKind.Normal, 0, Phase.Playing)
    }

    @Test
    fun `a completed sudoku is won`() {
        playing(testSudoku(completed = true)).game.value.shouldBePlaying(SudokuKind.Normal, 3, Phase.Won)
    }

    @Test
    fun `a level sudoku has its level and the level error limit`() {
        val sudoku = testSudoku(modeLevel = 4).apply { errorsMade = 3 }

        playing(sudoku).game.value.shouldBePlaying(SudokuKind.Level(4), 3, Phase.Lost)
    }

    @Test
    fun `a daily sudoku of the clock's date is playing with its date`() {
        playing(daily(today)).game.value.shouldBePlaying(SudokuKind.Daily(LocalDate.of(2026, 1, 15)), 3, Phase.Playing)
    }

    @Test
    fun `a daily sudoku of an earlier date is expired even after it reached its error limit`() {
        val sudoku = daily(today.minusDays(1)).apply { errorsMade = 3 }

        playing(sudoku).game.value.shouldBePlaying(SudokuKind.Daily(LocalDate.of(2026, 1, 14)), 3, Phase.Expired)
    }

    @Test
    fun `a completed daily sudoku of an earlier date is won`() {
        val sudoku = testSudoku(modeLevel = MODE_DAILY, created = today.minusDays(1), completed = true)

        playing(sudoku).game.value.shouldBePlaying(SudokuKind.Daily(LocalDate.of(2026, 1, 14)), 3, Phase.Won)
    }

    @Test
    fun `onErrorMade moves a sudoku that reached its limit to lost`() {
        val sudoku = testSudoku()
        val viewModel = playing(sudoku)
        sudoku.errorsMade = 3

        viewModel.onErrorMade()

        viewModel.game.value.shouldBePlaying(SudokuKind.Normal, 3, Phase.Lost)
    }

    @Test
    fun `onErrorMade saves the sudoku with the error that ended it`() {
        val sudoku = testSudoku()
        val viewModel = playing(sudoku)
        sudoku.errorsMade = 3

        viewModel.onErrorMade()
        idleMainLooper()

        rig.repository.saves.map { it.rows.sudoku.errorsMade to it.onlyUpdate } shouldBe listOf(3 to true)
    }

    @Test
    fun `today's daily sudoku stays playing with a running timer after the clock passes midnight`() {
        val sudoku = daily(today)
        val viewModel = playing(sudoku)
        clock.advanceTo(Instant.parse("2026-01-16T00:00:01Z"))

        viewModel.onResumed()
        sudoku.errorsMade = 1
        viewModel.onErrorMade()

        viewModel.game.value.shouldBePlaying(SudokuKind.Daily(LocalDate.of(2026, 1, 15)), 3, Phase.Playing)
        sudoku.resumed shouldBe true
        sudoku.stopTimer()
    }

    @Test
    fun `onResumed before the sudoku plays starts no timer`() {
        val sudoku = testSudoku()
        val viewModel = rig.ready(sudoku)

        viewModel.onResumed()

        viewModel.game.value shouldBe SudokuGame.Ready(sudoku)
        sudoku.resumed shouldBe false
    }

    @Test
    fun `onCompleted moves a sudoku completed after the start to won`() {
        val sudoku = testSudoku()
        val viewModel = playing(sudoku)
        sudoku.fields.forEach { it.value = it.solution }

        viewModel.onCompleted()

        viewModel.game.value.shouldBePlaying(SudokuKind.Normal, 3, Phase.Won)
    }

    private fun SudokuGame.shouldBePlaying(
        kind: SudokuKind,
        errorLimit: Int,
        phase: Phase,
    ) {
        val playing = this as SudokuGame.Playing
        (playing.kind to playing.errorLimit to playing.phase) shouldBe (kind to errorLimit to phase)
    }
}
