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

import android.os.Bundle
import android.os.Looper
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
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
import de.lemke.commonutils.ui.utils.saveSearchAndActionMode
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuListItem.SeparatorItem
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.ui.MainActivity
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
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
 * Covers [TabHistory]'s action-mode/delete flow. Two library seams neither established in this fleet:
 *
 * 1. Entering action mode through the real `onLongClickItem` wrapper drags in
 *    `RecyclerView.seslStartLongPressMultiSelection()`, which NPEs under Robolectric
 *    (`mPenDragSelectedItemArray` is only initialized by a real touch-driven long-press dispatch, which
 *    Robolectric never performs here) — a SESL/Robolectric interaction issue, not a bug in this test or
 *    in [TabHistory]. [TabHistory.launchActionMode] itself doesn't touch that call, so it's widened to
 *    `internal` and invoked directly, exercising 100% real logic minus that one unrelated statement.
 * 2. OneUI's `ToolbarLayout.startActionMode` wraps the fragment's lambdas in an anonymous
 *    `ActionModeListener` with no public getter, so there is no way to invoke
 *    `onMenuItemClicked`/`onSelectAll` through a public API once `launchActionMode()` has registered it.
 *    The pattern here: let production code run for real up to that registration
 *    (`drawerLayout.startActionMode(...)` really executes, inflating the real menu via the fragment's
 *    real `onInflateMenu` lambda), then reflectively read `ToolbarLayout`'s private `actionModeListener`
 *    field to get *that exact real listener instance* — not a test double — and call its interface
 *    methods directly with a real `MenuItem` built from the app's own `R.menu.delete_menu`. Every side
 *    effect this drives (dialog, coroutine, repository delete, `endActionMode()`) is real production
 *    code; only the retrieval of the listener reference uses reflection, because the library exposes no
 *    other seam.
 *
 * Recorded in the status file as the reusable pattern for future OneUI action-mode coverage in this fleet.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class TabHistoryActionModeTest {
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

    @Inject
    lateinit var getAllSudokus: GetAllSudokusUseCase

    @Inject
    lateinit var userSettings: UserSettings

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        // MainActivity talks to PlayGames on launch; the SDK is normally auto-initialized by its
        // manifest-merged ContentProvider, which Robolectric does not run under HiltTestApplication.
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun historySudoku(): Sudoku {
        val size = 4
        return Sudoku.create(
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = MODE_NORMAL,
            fields = MutableList(size * size) { index -> Field(position = Position.create(index, size), solution = 1) },
        )
    }

    private fun launch(block: (TabHistory) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.onTabItemSelected(0) }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<TabHistory>()
                        .first()
                block(fragment)
            }
        }
    }

    /** Retrieves the real [ToolbarLayout.ActionModeListener] registered by [TabHistory.launchActionMode]. */
    private fun actionModeListenerOf(drawerLayout: DrawerLayout): ToolbarLayout.ActionModeListener {
        val field = ToolbarLayout::class.java.getDeclaredField("actionModeListener").apply { isAccessible = true }
        return field.get(drawerLayout) as ToolbarLayout.ActionModeListener
    }

    private fun deleteMenuItem(fragment: TabHistory) =
        MenuBuilder(fragment.requireContext())
            .apply { fragment.requireActivity().menuInflater.inflate(R.menu.delete_menu, this) }
            .findItem(R.id.menuButtonDelete)!!

    private fun otherMenuItem(fragment: TabHistory) =
        MenuBuilder(fragment.requireContext())
            .apply { add(0, 12345, 0, "other") }
            .findItem(12345)!!

    @Test
    fun `long-clicking an item enters action mode and deleting the selection removes it`() {
        val first = historySudoku()
        val second = historySudoku()
        runBlocking {
            saveSudoku(first)
            saveSudoku(second)
        }
        launch { fragment ->
            val drawerLayout = fragment.requireActivity().findViewById<DrawerLayout>(R.id.drawerLayout)
            drawerLayout.isActionMode.shouldBeFalse()

            fragment.launchActionMode()
            shadowOf(Looper.getMainLooper()).idle()
            drawerLayout.isActionMode.shouldBeTrue()
            fragment.sudokuListAdapter.isActionMode.shouldBeTrue()

            val listener = actionModeListenerOf(drawerLayout)
            listener.onSelectAll(true)
            fragment.sudokuListAdapter.getSelectedIds() shouldBe setOf(first, second).map { it.hashCode().toLong() }.toSet()

            listener.onMenuItemClicked(deleteMenuItem(fragment)).shouldBeTrue()
            shadowOf(Looper.getMainLooper()).idle()

            drawerLayout.isActionMode.shouldBeFalse()
            fragment.sudokuListAdapter.isActionMode.shouldBeFalse()
            runBlocking { getAllSudokus() }.shouldBeEmpty()
        }
    }

    @Test
    fun `an unrecognized action mode menu item is ignored`() {
        val sudoku = historySudoku()
        runBlocking { saveSudoku(sudoku) }
        launch { fragment ->
            val drawerLayout = fragment.requireActivity().findViewById<DrawerLayout>(R.id.drawerLayout)
            fragment.launchActionMode()
            shadowOf(Looper.getMainLooper()).idle()

            val listener = actionModeListenerOf(drawerLayout)
            listener.onMenuItemClicked(otherMenuItem(fragment)).shouldBeFalse()
            shadowOf(Looper.getMainLooper()).idle()

            drawerLayout.isActionMode.shouldBeTrue()
            runBlocking { getAllSudokus() } shouldBe listOf(sudoku)
        }
    }

    @Test
    fun `clicking an item outside action mode opens it in SudokuActivity`() {
        val sudoku = historySudoku()
        runBlocking { saveSudoku(sudoku) }
        launch { fragment ->
            val holder = fragment.sudokuListAdapter.onCreateViewHolder(fragment.binding.sudokuHistoryList, SudokuItem.VIEW_TYPE)
            fragment.sudokuListAdapter.onClickItem?.invoke(0, SudokuItem(sudoku, "label"), holder)
            shadowOf(Looper.getMainLooper()).idle()

            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe SudokuActivity::class.java.name
            started.getStringExtra(KEY_SUDOKU_ID) shouldBe sudoku.id.value
        }
    }

    @Test
    fun `clicking an item while in action mode toggles its selection instead of opening it`() {
        val sudoku = historySudoku()
        runBlocking { saveSudoku(sudoku) }
        launch { fragment ->
            fragment.launchActionMode()
            shadowOf(Looper.getMainLooper()).idle()

            val holder = fragment.sudokuListAdapter.onCreateViewHolder(fragment.binding.sudokuHistoryList, SudokuItem.VIEW_TYPE)
            val item = SudokuItem(sudoku, "label")
            fragment.sudokuListAdapter.onClickItem?.invoke(0, item, holder)

            fragment.sudokuListAdapter.getSelectedIds() shouldBe setOf(item.stableId)
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)
        }
    }

    @Test
    fun `onSaveInstanceState returns early when the fragment view was never created`() {
        val fragment = TabHistory()
        fragment.onSaveInstanceState(Bundle())
    }

    @Test
    fun `onSaveInstanceState records the active selection while in action mode`() {
        val sudoku = historySudoku()
        runBlocking { saveSudoku(sudoku) }
        launch { fragment ->
            fragment.launchActionMode()
            shadowOf(Looper.getMainLooper()).idle()
            val listener = actionModeListenerOf(fragment.requireActivity().findViewById(R.id.drawerLayout))
            listener.onSelectAll(true)

            val outState = Bundle()
            fragment.onSaveInstanceState(outState)

            val expected =
                Bundle().apply {
                    saveSearchAndActionMode(isActionMode = true, selectedIds = fragment.sudokuListAdapter.getSelectedIds())
                }
            outState.keySet() shouldBe expected.keySet()
        }
    }

    @Test
    fun `restoring a saved action-mode bundle re-enters action mode with the saved selection`() {
        val sudoku = historySudoku()
        runBlocking { saveSudoku(sudoku) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.onTabItemSelected(0) }
            shadowOf(Looper.getMainLooper()).idle()
            var selectedId = 0L
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<TabHistory>()
                        .first()
                fragment.launchActionMode()
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<TabHistory>()
                        .first()
                val listener = actionModeListenerOf(activity.findViewById(R.id.drawerLayout))
                listener.onSelectAll(true)
                selectedId = fragment.sudokuListAdapter.getSelectedIds().first()
            }

            scenario.recreate()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity { activity ->
                activity.onTabItemSelected(0)
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val drawerLayout = activity.findViewById<DrawerLayout>(R.id.drawerLayout)
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<TabHistory>()
                        .first()
                drawerLayout.isActionMode.shouldBeTrue()
                fragment.sudokuListAdapter.getSelectedIds() shouldBe setOf(selectedId)
            }
        }
    }

    @Test
    fun `onSaveInstanceState returns early when only the drawer layout was never assigned`() {
        launch { fragment ->
            val drawerLayoutField = TabHistory::class.java.getDeclaredField("drawerLayout").apply { isAccessible = true }
            drawerLayoutField.set(fragment, null)

            fragment.onSaveInstanceState(Bundle())
        }
    }

    @Test
    fun `clicking a separator item outside action mode does not open or select anything`() {
        val sudoku = historySudoku()
        runBlocking { saveSudoku(sudoku) }
        launch { fragment ->
            val holder = fragment.sudokuListAdapter.onCreateViewHolder(fragment.binding.sudokuHistoryList, SeparatorItem.VIEW_TYPE)
            fragment.sudokuListAdapter.onClickItem?.invoke(0, SeparatorItem("label"), holder)
            shadowOf(Looper.getMainLooper()).idle()

            fragment.sudokuListAdapter.getSelectedIds().shouldBeEmpty()
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)
        }
    }

    @Test
    fun `the history list keeps a zero error limit without flagging the adapter as out of sync`() {
        userSettings.errorLimit = 0
        launch { fragment ->
            fragment.sudokuListAdapter.errorLimit shouldBe 0
        }
    }
}
