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
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.viewpager2.widget.ViewPager2
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
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.di.SolvedBoardGeneratorModule
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.SolvedBoardGenerator
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.SIZE_16X16
import de.lemke.sudoku.domain.model.Sudoku.Companion.SIZE_4X4
import de.lemke.sudoku.domain.model.Sudoku.Companion.SIZE_9X9
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.ui.SudokuLevelActivity
import de.lemke.sudoku.ui.utils.listSudoku
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration
import java.time.LocalDateTime
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** sdk = 36: Robolectric's max supported SDK. */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class, SolvedBoardGeneratorModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuLevelTabRevealTest {
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
    val solvedBoardGenerator: SolvedBoardGenerator = PatternSolvedBoardGenerator()

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var userSettings: UserSettings

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        userSettings.currentLevelTab = 0
        (1..COMPLETED_LEVELS).forEach { save(completedLevel(SudokuId.generate(), it)) }
    }

    @Test
    fun `a level solved while the level flow still ran shows its next level at the top after returning`() {
        launchLevels { scenario ->
            scenario.read { it.levelList().firstVisiblePosition() } shouldBe 0
            scenario.moveToState(Lifecycle.State.CREATED)
            solveTopLevel { scenario.read { it } }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil { scenario.read { it.adapterHoldsViewModelLevels() && it.levelList().isSettled() } }
            scenario.onActivity { activity ->
                activity.levelList().firstVisiblePosition() shouldBe 0
                activity.levelList().levelTextAt(0) shouldBe "Level ${COMPLETED_LEVELS + 2}"
                activity.progressBarShown(SIZE_4X4) shouldBe false
            }
        }
    }

    @Test
    fun `a level solved after the level flow stopped shows its next level at the top after returning`() {
        launchLevels { scenario ->
            val solvedId = scenario.read { it.topLevelId() }
            scenario.moveToState(Lifecycle.State.CREATED)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
            save(completedLevel(solvedId, COMPLETED_LEVELS + 1))
            idle()
            scenario.read { it.topLevelLabel() } shouldBe "${COMPLETED_LEVELS + 1}"
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil {
                scenario.read {
                    it.topLevelLabel() == "${COMPLETED_LEVELS + 2}" && it.adapterHoldsViewModelLevels() && it.levelList().isSettled()
                }
            }
            val nextLevelId = scenario.read { it.topLevelId() }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            scenario.onActivity { activity ->
                activity.levelList().firstVisiblePosition() shouldBe 0
                activity.levelList().levelTextAt(0) shouldBe "Level ${COMPLETED_LEVELS + 2}"
                activity.topLevelId() shouldBe nextLevelId
                activity.committedLevelId(1) shouldBe solvedId
                activity.progressBarShown(SIZE_4X4) shouldBe false
            }
        }
    }

    @Test
    fun `a next level event handled before the list commits the level reveals it`() {
        launchLevels { scenario ->
            scenario.scrollLevelsToBottom()
            scenario.moveToState(Lifecycle.State.CREATED)
            solveTopLevel { scenario.read { it } }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil { scenario.read { it.adapterHoldsViewModelLevels() && it.levelList().isSettled() } }
            scenario.onActivity { activity ->
                activity.levelList().firstVisiblePosition() shouldBe 0
                activity.levelList().levelTextAt(0) shouldBe "Level ${COMPLETED_LEVELS + 2}"
            }
        }
    }

    @Test
    fun `a next level event handled after the list committed the level reveals it`() {
        val controller = Robolectric.buildActivity(SudokuLevelActivity::class.java).setup()
        try {
            awaitUntil { controller.get().adapterHoldsViewModelLevels() && controller.get().levelList().isSettled() }
            controller.get().levelList().scrollToPosition(COMPLETED_LEVELS)
            idle()
            controller.get().levelList().firstVisiblePosition() shouldNotBe 0
            controller.pause().stop()
            solveTopLevel { controller.get() }
            controller.start()
            awaitUntil { controller.get().adapterHoldsViewModelLevels() }
            controller.get().levelList().firstVisiblePosition() shouldNotBe 0
            controller.resume()
            awaitUntil { controller.get().levelList().isSettled() }
            controller.get().levelList().firstVisiblePosition() shouldBe 0
            controller.get().levelList().levelTextAt(0) shouldBe "Level ${COMPLETED_LEVELS + 2}"
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `returning from an older completed level keeps the position and the next level`() {
        launchLevels { scenario ->
            val nextLevelId = scenario.read { it.topLevelId() }
            val scrolledTo = scenario.scrollLevelsToBottom()
            scenario.moveToState(Lifecycle.State.CREATED)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil { scenario.read { it.adapterHoldsViewModelLevels() } }
            scenario.onActivity { activity ->
                activity.levelList().firstVisiblePosition() shouldBe scrolledTo
                activity.topLevelId() shouldBe nextLevelId
                activity.levelList().adapter?.itemCount shouldBe COMPLETED_LEVELS + 1
                activity.progressBarShown(SIZE_4X4) shouldBe false
            }
        }
    }

    @Test
    fun `recreating the activity twice and swiping the size tabs keeps the position and the next levels`() {
        launchLevels { scenario ->
            val nextLevelId = scenario.read { it.topLevelId() }
            val scrolledTo = scenario.scrollLevelsToBottom()

            repeat(2) {
                scenario.recreate()
                awaitUntil { scenario.read { it.adapterHoldsViewModelLevels() } }
            }
            listOf(1 to SIZE_9X9, 2 to SIZE_16X16, 0 to SIZE_4X4).forEach { (page, size) ->
                scenario.onActivity { it.findViewById<ViewPager2>(R.id.viewPagerLevel).currentItem = page }
                awaitUntil { scenario.read { it.levelTab(size).lifecycle.currentState == Lifecycle.State.RESUMED } }
                scenario.read { it.levelList(size).firstVisiblePosition() } shouldBe if (size == SIZE_4X4) scrolledTo else 0
            }

            scenario.onActivity { activity ->
                activity.levelList().firstVisiblePosition() shouldBe scrolledTo
                activity.topLevelId() shouldBe nextLevelId
                activity.levelList().adapter?.itemCount shouldBe COMPLETED_LEVELS + 1
                listOf(SIZE_4X4, SIZE_9X9, SIZE_16X16).forEach { size ->
                    activity.progressBarShown(size) shouldBe false
                }
                activity.levelTab(SIZE_9X9).sudokuListAdapter.itemCount shouldBe 1
                activity.levelTab(SIZE_16X16).sudokuListAdapter.itemCount shouldBe 1
            }
        }
    }

    private fun completedLevel(
        sudokuId: SudokuId,
        level: Int,
    ): Sudoku =
        listSudoku(
            sudokuId = sudokuId,
            modeLevel = level,
            filled = 16,
            errorsMade = 0,
            seconds = 40,
            created = LocalDateTime.of(2026, 1, 15, 9, 0).plusMinutes(level.toLong()),
        )

    private fun solveTopLevel(activity: () -> SudokuLevelActivity) {
        save(completedLevel(activity().topLevelId(), COMPLETED_LEVELS + 1))
        awaitUntil { activity().topLevelLabel() == "${COMPLETED_LEVELS + 2}" }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
    }

    private fun save(sudoku: Sudoku) = runBlocking { saveSudoku(sudoku) }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    // AsyncListDiffer diffs on a background executor that idle() does not wait for.
    private fun awaitUntil(condition: () -> Boolean) {
        repeat(AWAIT_ATTEMPTS) {
            idle()
            if (condition()) {
                idle()
                return
            }
            Thread.sleep(AWAIT_STEP_MS)
        }
        error("condition not met within ${AWAIT_ATTEMPTS * AWAIT_STEP_MS} ms")
    }

    private fun launchLevels(block: (ActivityScenario<SudokuLevelActivity>) -> Unit) =
        ActivityScenario.launch(SudokuLevelActivity::class.java).use { scenario ->
            awaitUntil { scenario.read { it.levelList().adapter?.itemCount } == COMPLETED_LEVELS + 1 }
            awaitUntil { scenario.read { it.adapterHoldsViewModelLevels() && it.levelList().isSettled() } }
            block(scenario)
        }

    private fun ActivityScenario<SudokuLevelActivity>.scrollLevelsToBottom(): Int {
        onActivity { it.levelList().scrollToPosition(COMPLETED_LEVELS) }
        idle()
        return read { it.levelList().firstVisiblePosition() }.also { it shouldNotBe 0 }
    }

    private fun <T> ActivityScenario<SudokuLevelActivity>.read(block: (SudokuLevelActivity) -> T): T {
        val result = mutableListOf<T>()
        onActivity { result += block(it) }
        return result.single()
    }

    private fun SudokuLevelActivity.levelTab(size: Int): SudokuLevelTab =
        supportFragmentManager.fragments
            .filterIsInstance<SudokuLevelTab>()
            .first { it.arguments?.getInt("size") == size }

    private fun SudokuLevelActivity.progressBarShown(size: Int): Boolean = levelTab(size).binding.tabLevelProgressBar.isVisible

    private fun SudokuLevelActivity.levelList(size: Int = SIZE_4X4): RecyclerView = levelTab(size).binding.sudokuLevelsRecycler

    private fun SudokuLevelActivity.committedLevelId(position: Int): SudokuId =
        (levelTab(SIZE_4X4).sudokuListAdapter.currentList[position] as SudokuItem).sudoku.id

    private fun SudokuLevelActivity.topLevelItem(): SudokuItem =
        levelTab(SIZE_4X4)
            .viewModel.state.value.sudokuLevel
            .first() as SudokuItem

    private fun SudokuLevelActivity.topLevelId(): SudokuId = topLevelItem().sudoku.id

    private fun SudokuLevelActivity.topLevelLabel(): String = topLevelItem().label

    private fun SudokuLevelActivity.adapterHoldsViewModelLevels(): Boolean {
        val tab = levelTab(SIZE_4X4)
        val levels = tab.viewModel.state.value.sudokuLevel
        val committed = tab.sudokuListAdapter.currentList
        return committed.size == levels.size && levels.indices.all { committed[it] === levels[it] }
    }

    private fun RecyclerView.isSettled(): Boolean =
        layoutManager?.isSmoothScrolling == false && scrollState == RecyclerView.SCROLL_STATE_IDLE && !isLayoutRequested

    private fun RecyclerView.firstVisiblePosition(): Int = (layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()

    private fun RecyclerView.levelTextAt(position: Int): String? =
        findViewHolderForAdapterPosition(position)
            ?.itemView
            ?.findViewById<TextView>(R.id.item_text)
            ?.text
            ?.toString()

    private companion object {
        const val COMPLETED_LEVELS = 20
        const val AWAIT_ATTEMPTS = 200
        const val AWAIT_STEP_MS = 10L
    }
}
