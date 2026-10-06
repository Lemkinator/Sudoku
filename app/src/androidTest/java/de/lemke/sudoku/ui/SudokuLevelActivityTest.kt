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

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.sudoku.HiltTestRule
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.di.SolvedBoardGeneratorModule
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.SolvedBoardGenerator
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testLevelSudoku
import de.lemke.sudoku.ui.fragments.SudokuLevelTab
import de.lemke.sudoku.ui.fragments.SudokuLevelTabViewModel
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@UninstallModules(SolvedBoardGeneratorModule::class)
@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class SudokuLevelActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

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
    fun setUp() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    @Test
    fun activityLaunchesWithoutCrash() {
        ActivityScenario
            .launch<SudokuLevelActivity>(
                Intent(ApplicationProvider.getApplicationContext(), SudokuLevelActivity::class.java),
            ).use { scenario ->
                scenario.state shouldBe Lifecycle.State.RESUMED
            }
    }

    @Test
    fun levelSolvedWhileTheLevelFlowStillRanShowsItsNextLevelAtTheTopAfterReturning() {
        userSettings.currentLevelTab = 0
        runBlocking { (1..COMPLETED_LEVELS).forEach { saveSudoku(completedLevel(it, SudokuId.generate())) } }
        ActivityScenario
            .launch<SudokuLevelActivity>(
                Intent(ApplicationProvider.getApplicationContext(), SudokuLevelActivity::class.java),
            ).use { scenario ->
                scenario.waitUntil { levelList().adapter?.itemCount == COMPLETED_LEVELS + 1 && levelList().isSettled() }
                var nextLevelId: SudokuId? = null
                scenario.onActivity { nextLevelId = it.topLevelItem().sudoku.id }
                scenario.moveToState(Lifecycle.State.CREATED)
                runBlocking { saveSudoku(completedLevel(COMPLETED_LEVELS + 1, checkNotNull(nextLevelId))) }
                scenario.waitUntil { topLevelItem().label == "${COMPLETED_LEVELS + 2}" }
                SystemClock.sleep(IN_GAME_MS)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.waitUntil { levelList().adapter?.itemCount == COMPLETED_LEVELS + 2 }
                scenario.waitUntil { levelList().isSettled() }
                scenario.onActivity { activity ->
                    (activity.levelList().layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() shouldBe 0
                    activity.levelList().levelTextAt(0) shouldBe "Level ${COMPLETED_LEVELS + 2}"
                    activity.progressBarShown() shouldBe false
                }
            }
    }

    private fun completedLevel(
        level: Int,
        sudokuId: SudokuId,
    ): Sudoku =
        testLevelSudoku(size = SudokuSize.FOUR, level = level, sudokuId = sudokuId).apply {
            fields.forEach { it.value = it.solution }
        }

    private fun SudokuLevelActivity.levelTab(): SudokuLevelTab =
        supportFragmentManager.fragments
            .filterIsInstance<SudokuLevelTab>()
            .first { it.arguments?.getInt(SudokuLevelTab.KEY_SIZE) == SudokuSize.FOUR.value }

    private fun SudokuLevelActivity.progressBarShown(): Boolean =
        levelTab().requireView().findViewById<View>(R.id.tabLevelProgressBar).isVisible

    private fun SudokuLevelActivity.levelList(): RecyclerView = levelTab().requireView().findViewById(R.id.sudokuLevelsRecycler)

    private fun SudokuLevelActivity.topLevelItem(): SudokuItem =
        ViewModelProvider(levelTab())[SudokuLevelTabViewModel::class.java]
            .state.value.sudokuLevel
            .first() as SudokuItem

    private fun RecyclerView.isSettled(): Boolean =
        layoutManager?.isSmoothScrolling == false && scrollState == RecyclerView.SCROLL_STATE_IDLE && !isLayoutRequested

    private fun RecyclerView.levelTextAt(position: Int): String? =
        findViewHolderForAdapterPosition(position)
            ?.itemView
            ?.findViewById<TextView>(R.id.item_text)
            ?.text
            ?.toString()

    private fun ActivityScenario<SudokuLevelActivity>.waitUntil(condition: SudokuLevelActivity.() -> Boolean) {
        repeat(WAIT_ATTEMPTS) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var met = false
            onActivity { met = it.condition() }
            if (met) return
            Thread.sleep(WAIT_STEP_MS)
        }
        error("condition not met within ${WAIT_ATTEMPTS * WAIT_STEP_MS} ms")
    }

    private companion object {
        const val COMPLETED_LEVELS = 20
        const val IN_GAME_MS = 5_500L
        const val WAIT_ATTEMPTS = 500
        const val WAIT_STEP_MS = 10L
    }
}
