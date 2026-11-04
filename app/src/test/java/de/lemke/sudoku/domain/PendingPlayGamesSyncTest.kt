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
import android.content.SharedPreferences
import de.lemke.commonutils.freshTestPreferences
import de.lemke.sudoku.R
import de.lemke.sudoku.data.PendingPlayGamesWins
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.model.PendingPlayGamesSync
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class PendingPlayGamesSyncTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val preferences: SharedPreferences = freshTestPreferences()
    private val pendingWins = pendingWinsOver(preferences)
    private val repository = FakeSudokusRepository()
    private val queueWin = QueuePlayGamesWinUseCase(pendingWins)
    private val clearWins = ClearPlayGamesWinsUseCase(pendingWins)
    private val getPending =
        GetPendingPlayGamesSyncUseCase(pendingWins, repository, CalculatePlayGamesSyncUseCase(repository, dispatcher), dispatcher)

    private fun pendingWinsOver(preferences: SharedPreferences) =
        PendingPlayGamesWins(UserSettings(preferences, CoroutineScope(dispatcher)))

    private fun won(id: String) = testSudoku(size = SudokuSize.FOUR, sudokuId = SudokuId(id), completed = true)

    @Test
    fun `a queued win survives a new settings instance over the same store`() {
        queueWin(SudokuId("first"))
        queueWin(SudokuId("second"))

        pendingWinsOver(preferences).all() shouldBe setOf(SudokuId("first"), SudokuId("second"))
    }

    @Test
    fun `clearing removes only the given wins`() {
        queueWin(SudokuId("first"))
        queueWin(SudokuId("second"))

        clearWins(setOf(SudokuId("first")))

        pendingWins.all() shouldBe setOf(SudokuId("second"))
    }

    @Test
    fun `nothing is pending when no win is queued`() =
        runTest {
            repository.store(won("first"))

            getPending().shouldBeNull()
        }

    @Test
    fun `a queued win of a completed sudoku yields its sync`() =
        runTest {
            repository.store(won("first"))
            queueWin(SudokuId("first"))

            val pending = getPending().shouldNotBeNull()

            pending.wins shouldBe setOf(SudokuId("first"))
            pending.sync.achievementUnlocks shouldContain R.string.achievement_first_win
        }

    @Test
    fun `two queued wins increment the achievements of both`() =
        runTest {
            repository.store(won("first"), won("second"))
            queueWin(SudokuId("first"))
            queueWin(SudokuId("second"))

            val pending: PendingPlayGamesSync = getPending().shouldNotBeNull()

            pending.wins shouldBe setOf(SudokuId("first"), SudokuId("second"))
            pending.sync.achievementIncrements.count { it.first == R.string.achievement_10_sudokus_44 } shouldBe 2
        }

    @Test
    fun `a queued win that is missing or unfinished is dropped`() =
        runTest {
            repository.store(testSudoku(size = SudokuSize.FOUR, sudokuId = SudokuId("unfinished"), completed = false))
            queueWin(SudokuId("unfinished"))
            queueWin(SudokuId("missing"))

            getPending().shouldBeNull()

            pendingWins.all() shouldBe emptySet()
        }
}
