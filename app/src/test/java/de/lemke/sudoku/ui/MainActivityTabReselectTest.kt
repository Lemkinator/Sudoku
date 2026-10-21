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

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import dev.oneuiproject.oneui.layout.DrawerLayout
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class MainActivityTabReselectTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun launch(block: (MainActivity) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity(block)
        }
    }

    private fun reselect(
        activity: MainActivity,
        position: Int,
    ): TabLayout.Tab {
        val tab =
            activity.binding.bottomTab
                .getTabAt(position)
                .shouldNotBeNull()
        tab.select()
        idleMainLooper()
        tab.select()
        idleMainLooper()
        return tab
    }

    @Test
    fun `reselecting the history tab does not crash`() =
        launch { activity ->
            val tab = reselect(activity, 0)
            activity.binding.bottomTab.selectedTabPosition shouldBe tab.position
        }

    @Test
    fun `reselecting the sudoku tab does not crash`() =
        launch { activity ->
            val tab = reselect(activity, 1)
            activity.binding.bottomTab.selectedTabPosition shouldBe tab.position
        }

    @Test
    fun `reselecting the statistics tab does not crash`() =
        launch { activity ->
            val tab = reselect(activity, 2)
            activity.binding.bottomTab.selectedTabPosition shouldBe tab.position
        }

    private fun historySudoku(): Sudoku {
        val size = SudokuSize.FOUR
        return Sudoku.create(
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = Sudoku.MODE_NORMAL,
            fields = MutableList(size.cellCount) { index -> Field(position = Position.create(index, size), solution = 1) },
        )
    }

    @Test
    fun `reselecting the history tab scrolls to top when the list is scrolled down`() {
        runBlocking { repeat(30) { saveSudoku(historySudoku()) } }
        launch { activity ->
            val tab = activity.binding.bottomTab.getTabAt(0)
            tab?.select()
            idleMainLooper()

            val historyRecyclerView = activity.findViewById<RecyclerView>(R.id.sudokuHistoryList)
            historyRecyclerView.scrollBy(0, 3000)
            idleMainLooper()
            historyRecyclerView.canScrollVertically(-1).shouldBeTrue()

            tab?.select()
            repeat(5) { idleMainLooper() }
            historyRecyclerView.canScrollVertically(-1).shouldBeFalse()
        }
    }

    @Test
    fun `reselecting the statistics tab collapses an expanded drawer`() =
        launch { activity ->
            val tab = activity.binding.bottomTab.getTabAt(2)
            tab?.select()
            idleMainLooper()

            val drawerLayout = activity.findViewById<DrawerLayout>(R.id.drawerLayout)
            drawerLayout.setExpanded(true, false)
            idleMainLooper()
            drawerLayout.isExpanded.shouldBeTrue()

            tab?.select()
            repeat(5) { idleMainLooper() }
            drawerLayout.isExpanded.shouldBeFalse()
        }

    @Test
    fun `reselecting the statistics tab scrolls to top when the drawer is collapsed and the list is scrolled down`() =
        launch { activity ->
            val tab = activity.binding.bottomTab.getTabAt(2)
            tab?.select()
            idleMainLooper()

            val drawerLayout = activity.findViewById<DrawerLayout>(R.id.drawerLayout)
            drawerLayout.isExpanded.shouldBeFalse()

            val statisticsRecyclerView = activity.findViewById<RecyclerView>(R.id.statisticsListRecycler)
            statisticsRecyclerView.scrollBy(0, 3000)
            idleMainLooper()
            statisticsRecyclerView.canScrollVertically(-1).shouldBeTrue()

            tab?.select()
            repeat(5) { idleMainLooper() }
            statisticsRecyclerView.canScrollVertically(-1).shouldBeFalse()
        }
}
