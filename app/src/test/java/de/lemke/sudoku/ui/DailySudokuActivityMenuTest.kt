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

import android.widget.PopupMenu
import androidx.test.core.app.ActivityScenario
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
import de.lemke.sudoku.R
import de.lemke.sudoku.di.ClockModule
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuListItem.SeparatorItem
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.sqrt
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
import org.robolectric.fakes.RoboMenuItem

/**
 * Covers [DailySudokuActivity.onOptionsItemSelected]/`onPrepareOptionsMenu` and the `SudokuListAdapter` click wiring
 * set up in `setupOnClickListeners()`, against a real daily sudoku seeded through [SaveSudokuUseCase] and a fixed
 * [Clock] (matching [DailySudokuActivityScreenshotTest]'s pattern) rather than a mocked list.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class, ClockModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class DailySudokuActivityMenuTest {
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

    @BindValue
    @JvmField
    val testClock: Clock = Clock.fixed(ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    private fun dailySudoku(sudokuId: SudokuId = SudokuId.generate()): Sudoku {
        val size = 4
        val blockSize = sqrt(size.toDouble()).toInt()
        return Sudoku.create(
            sudokuId = sudokuId,
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = MODE_DAILY,
            created = LocalDateTime.now(testClock),
            fields =
                MutableList(size * size) { index ->
                    val row = index / size
                    val col = index % size
                    val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size + 1
                    Field(position = Position.create(index, size), solution = solution, value = solution, given = true)
                },
        )
    }

    private fun launch(block: (DailySudokuActivity) -> Unit) {
        ActivityScenario.launch(DailySudokuActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> block(activity) }
        }
    }

    private fun menuFor(activity: DailySudokuActivity) = PopupMenu(activity, activity.binding.root).menu

    // region onPrepareOptionsMenu / onOptionsItemSelected

    @Test
    fun `onPrepareOptionsMenu shows only the show-only-completed item while dailyShowUncompleted is true`() =
        launch { activity ->
            activity.viewModel.dailyShowUncompleted = true
            val menu = menuFor(activity)
            activity.onCreateOptionsMenu(menu)
            activity.onPrepareOptionsMenu(menu)
            menu.findItem(R.id.menuitem_show_all_sudokus).isVisible.shouldBeFalse()
            menu.findItem(R.id.menuitem_show_only_completed_sudokus).isVisible.shouldBeTrue()
        }

    @Test
    fun `onPrepareOptionsMenu shows only the show-all item while dailyShowUncompleted is false`() =
        launch { activity ->
            activity.viewModel.dailyShowUncompleted = false
            val menu = menuFor(activity)
            activity.onCreateOptionsMenu(menu)
            activity.onPrepareOptionsMenu(menu)
            menu.findItem(R.id.menuitem_show_all_sudokus).isVisible.shouldBeTrue()
            menu.findItem(R.id.menuitem_show_only_completed_sudokus).isVisible.shouldBeFalse()
        }

    @Test
    fun `menuitem_daily_sudoku_info shows the info bottom sheet`() =
        launch { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menuitem_daily_sudoku_info)).shouldBeTrue()
        }

    @Test
    fun `menuitem_show_all_sudokus enables showing uncompleted sudokus`() =
        launch { activity ->
            activity.viewModel.dailyShowUncompleted = false
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menuitem_show_all_sudokus)).shouldBeTrue()
            activity.viewModel.dailyShowUncompleted.shouldBeTrue()
        }

    @Test
    fun `menuitem_show_only_completed_sudokus disables showing uncompleted sudokus`() =
        launch { activity ->
            activity.viewModel.dailyShowUncompleted = true
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menuitem_show_only_completed_sudokus)).shouldBeTrue()
            activity.viewModel.dailyShowUncompleted.shouldBeFalse()
        }

    @Test
    fun `an unknown menu item falls through to the default behavior`() =
        launch { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(-12345)).shouldBeFalse()
        }

    @Test
    fun `onPrepareOptionsMenu is a no-op when the framework passes a null menu`() =
        launch { activity ->
            activity.onPrepareOptionsMenu(null)
        }

    // endregion

    // region recycler click wiring

    @Test
    fun `clicking a sudoku item starts SudokuActivity for its id`() =
        launch { activity ->
            val sudoku = dailySudoku()
            runBlocking { saveSudoku(sudoku) }
            val holder = activity.sudokuListAdapter.onCreateViewHolder(activity.binding.dailySudokuRecycler, SudokuItem.VIEW_TYPE)
            val item = SudokuItem(sudoku, label = "label")

            activity.sudokuListAdapter.onClickItem.shouldNotBeNull()
            activity.sudokuListAdapter.onClickItem?.invoke(0, item, holder)

            val started = shadowOf(activity).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe SudokuActivity::class.java.name
            started.getStringExtra(KEY_SUDOKU_ID) shouldBe sudoku.id.value
        }

    @Test
    fun `clicking a separator item does not start anything`() =
        launch { activity ->
            val holder = activity.sudokuListAdapter.onCreateViewHolder(activity.binding.dailySudokuRecycler, SeparatorItem.VIEW_TYPE)
            activity.sudokuListAdapter.onClickItem?.invoke(0, SeparatorItem("label"), holder)
            shadowOf(activity).nextStartedActivity.shouldBe(null)
        }

    // endregion
}
