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

import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.di.MainDispatcher
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.SIZE_4X4
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuLevelActivity
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Covers [SudokuLevelTab] through [SudokuLevelActivity]'s size-4 subtab (position 0 of its `ViewPager2`): the
 * "confirm and start the newly generated next level" click branch (`position == 0 &&
 * viewModel.state.value.hasNextLevelToStart`) against an empty level list (so the view model auto-generates level
 * 1), and the plain "start an existing level" click branch against a pre-existing, incomplete level.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuLevelTabTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @DefaultDispatcher
    @JvmField
    val testDefaultDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()

    @BindValue
    @IoDispatcher
    @JvmField
    val testIoDispatcher: CoroutineDispatcher = Dispatchers.IO

    @BindValue
    @MainDispatcher
    @JvmField
    val testMainDispatcher: CoroutineDispatcher = Dispatchers.Main

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    private fun launch(block: (SudokuLevelTab) -> Unit) {
        ActivityScenario.launch(SudokuLevelActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<SudokuLevelTab>()
                        .first()
                block(fragment)
            }
        }
    }

    private fun clickItem(
        fragment: SudokuLevelTab,
        position: Int,
        sudoku: Sudoku,
    ) {
        val holder = fragment.sudokuListAdapter.onCreateViewHolder(fragment.binding.sudokuLevelsRecycler, SudokuItem.VIEW_TYPE)
        fragment.sudokuListAdapter.onClickItem?.invoke(position, SudokuItem(sudoku, sudoku.modeLevel.toString()), holder)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun levelSudoku(
        level: Int,
        completed: Boolean,
    ): Sudoku {
        val size = SIZE_4X4
        val blockSize = 2
        return Sudoku.create(
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = level,
            fields =
                MutableList(size * size) { index ->
                    val row = index / size
                    val col = index % size
                    val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size + 1
                    Field(
                        position = Position.create(index, size),
                        solution = solution,
                        value = if (completed) solution else null,
                        given = completed,
                    )
                },
        )
    }

    @Test
    fun `clicking the auto-generated next level at position 0 confirms it and starts the game`() =
        launch { fragment ->
            shadowOf(Looper.getMainLooper()).idle()
            fragment.viewModel.state.value.hasNextLevelToStart
                .shouldBeTrue()
            val nextLevel =
                (
                    fragment.viewModel.state.value.sudokuLevel
                        .first() as SudokuItem
                ).sudoku

            clickItem(fragment, 0, nextLevel)

            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe SudokuActivity::class.java.name
        }

    @Test
    fun `clicking a level at a non-zero position starts the game without confirming a new one`() {
        runBlocking { saveSudoku(levelSudoku(level = 1, completed = false)) }
        launch { fragment ->
            shadowOf(Looper.getMainLooper()).idle()
            val level =
                (
                    fragment.viewModel.state.value.sudokuLevel
                        .first() as SudokuItem
                ).sudoku

            // The onClickItem handler only confirms/generates the next level when position == 0; a non-zero
            // position (a real click always targets an actual list position, but the branch itself only checks
            // this literal comparison) skips straight to starting the game.
            clickItem(fragment, 1, level)

            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe SudokuActivity::class.java.name
        }
    }
}
