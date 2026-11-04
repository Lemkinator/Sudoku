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

package de.lemke.sudoku.ui.fragments

import android.app.Application
import android.database.sqlite.SQLiteException
import android.util.Log
import de.lemke.commonutils.freshTestPreferences
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.GenerateFieldsUseCase
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetRecentlyUpdatedNormalSudokuUseCase
import de.lemke.sudoku.domain.IsDailySudokuCompletedUseCase
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.SaveSudokuUseCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class TabSudokuViewModelLoadFailureTest {
    private val repository = FakeSudokusRepository()
    private val dispatcher = UnconfinedTestDispatcher()
    private val viewModel =
        TabSudokuViewModel(
            UserSettings(freshTestPreferences(), CoroutineScope(dispatcher)),
            GenerateSudokuUseCase(GenerateFieldsUseCase(dispatcher, PatternSolvedBoardGenerator()), dispatcher),
            SaveSudokuUseCase(repository, dispatcher),
            GetRecentlyUpdatedNormalSudokuUseCase(repository, dispatcher),
            IsDailySudokuCompletedUseCase(repository, Clock.systemUTC(), dispatcher),
        )

    @Test
    fun `returns null and logs the error when the database read fails`() =
        runTest {
            repository.beforeGetRecentlyUpdatedNormalSummary = { throw SQLiteException("no such table: game") }

            viewModel.loadStartTab() shouldBe null

            ShadowLog.getLogsForTag("TabSudokuViewModel").map { it.type to it.msg } shouldBe
                listOf(Log.ERROR to "Failed to load the sudoku tab")
        }

    @Test
    fun `lets a failure that is no database failure through`() =
        runTest {
            repository.beforeGetRecentlyUpdatedNormalSummary = { throw IllegalStateException("bug") }

            shouldThrow<IllegalStateException> { viewModel.loadStartTab() }.message shouldBe "bug"
        }
}
