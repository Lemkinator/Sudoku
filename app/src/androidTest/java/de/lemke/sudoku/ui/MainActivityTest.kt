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
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.testLevelSudoku
import de.lemke.sudoku.ui.fragments.TabHistory
import de.lemke.sudoku.ui.fragments.TabHistoryViewModel
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settings: SettingsRepository

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
            .launch<MainActivity>(
                Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
            ).use { scenario ->
                scenario.state shouldBe Lifecycle.State.RESUMED
            }
    }

    @Test
    fun sudokuSavedWhileStoppedAtTheTopOfALongHistoryShowsInTheFirstRows() {
        runBlocking { repeat(SEEDED_COUNT) { saveSudoku(historySudoku(seconds = 0, updated = SEEDED_DAY.plusMinutes(it.toLong()))) } }
        ActivityScenario
            .launch<MainActivity>(
                Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
            ).use { scenario ->
                scenario.onActivity { it.onTabItemSelected(0) }
                scenario.waitUntil { historyList().adapter?.itemCount == SEEDED_COUNT + 1 }
                scenario.moveToState(Lifecycle.State.CREATED)
                runBlocking { saveSudoku(historySudoku(seconds = 75, updated = NEXT_DAY)) }
                scenario.waitUntil { historyViewModel().sudokuHistory.value.size == SEEDED_COUNT + 3 }
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.waitUntil { historyList().adapter?.itemCount == SEEDED_COUNT + 3 }
                scenario.waitUntil { historyList().isSettled() }
                scenario.onActivity { activity ->
                    (activity.historyList().layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() shouldBe 0
                    activity.historyList().smallTextAt(1) shouldBe "01:15 | 0% | Errors: 0/3 | Hints: 0"
                }
            }
    }

    private fun historySudoku(
        seconds: Int,
        updated: LocalDateTime,
    ): Sudoku = testLevelSudoku(size = 4, level = MODE_NORMAL).copy(seconds = seconds, created = updated, updated = updated)

    private fun MainActivity.historyTab(): TabHistory = supportFragmentManager.fragments.filterIsInstance<TabHistory>().first()

    private fun MainActivity.historyList(): RecyclerView = historyTab().requireView().findViewById(R.id.sudokuHistoryList)

    private fun MainActivity.historyViewModel(): TabHistoryViewModel = ViewModelProvider(historyTab())[TabHistoryViewModel::class.java]

    private fun RecyclerView.isSettled(): Boolean = !isLayoutRequested && !hasPendingAdapterUpdates()

    private fun RecyclerView.smallTextAt(position: Int): String? =
        findViewHolderForAdapterPosition(position)
            ?.itemView
            ?.findViewById<TextView>(R.id.item_text_small)
            ?.text
            ?.toString()

    private fun ActivityScenario<MainActivity>.waitUntil(condition: MainActivity.() -> Boolean) {
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
        const val SEEDED_COUNT = 30
        const val WAIT_ATTEMPTS = 500
        const val WAIT_STEP_MS = 10L
        val SEEDED_DAY: LocalDateTime = LocalDateTime.of(2026, 1, 15, 9, 0)
        val NEXT_DAY: LocalDateTime = LocalDateTime.of(2026, 1, 16, 10, 0)
    }
}
