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
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.core.view.descendants
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.awaitMainLooperUntil
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.idleMainLooper
import de.lemke.commonutils.ui.utils.saveSearchAndActionMode
import de.lemke.sudoku.R
import de.lemke.sudoku.TestPersistenceModule
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.DefaultSudokusRepository
import de.lemke.sudoku.data.database.GameDao
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.di.SudokusRepositoryModule
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuListItem.SeparatorItem
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import de.lemke.sudoku.ui.MainActivity
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.layout.DrawerLayout
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import dev.oneuiproject.oneui.design.R as designR

/**
 * `seslStartLongPressMultiSelection()` NPEs under Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(SudokusRepositoryModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class TabHistoryActionModeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    private var deleteGate = CompletableDeferred(Unit)
    private var deleteCalls = 0
    private val database = TestPersistenceModule.provideTestAppDatabase(ApplicationProvider.getApplicationContext())
    private val gameDao = database.gameDao()

    @BindValue
    @JvmField
    val sudokusRepository: SudokusRepository =
        DefaultSudokusRepository(
            object : GameDao by gameDao {
                override suspend fun deleteByIds(ids: List<String>) {
                    deleteCalls++
                    deleteGate.await()
                    gameDao.deleteByIds(ids)
                }
            },
            Clock.systemDefaultZone(),
        )

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Inject
    lateinit var userSettings: UserSettings

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        // Robolectric skips the Play Games SDK's auto-init ContentProvider under HiltTestApplication.
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun historySudoku(): Sudoku = testSudoku(size = SudokuSize.FOUR, difficulty = Difficulty.VERY_EASY)

    private fun launch(block: (TabHistory) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.onTabItemSelected(0) }
            idleMainLooper()
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<TabHistory>()
                        .first()
                block(fragment)
            }
        }
    }

    private fun TabHistory.clickSelectAll() {
        requireActivity().findViewById<View>(designR.id.toolbarlayout_selectall).performClick()
        awaitMainLooperUntil {
            requireActivity()
                .findViewById<ViewGroup>(designR.id.tbl_footer_parent)
                .descendants
                .filterIsInstance<BottomNavigationView>()
                .any { it.menu.findItem(R.id.menuButtonDelete) != null }
        }
    }

    private fun TabHistory.bottomActionModeBar(): BottomNavigationView {
        val footer = requireActivity().findViewById<ViewGroup>(designR.id.tbl_footer_parent)
        return footer.descendants.filterIsInstance<BottomNavigationView>().single()
    }

    private fun TabHistory.clickDelete() {
        bottomActionModeBar().menu.performIdentifierAction(R.id.menuButtonDelete, 0)
    }

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
            idleMainLooper()
            drawerLayout.isActionMode.shouldBeTrue()
            fragment.sudokuListAdapter.isActionMode.shouldBeTrue()

            fragment.clickSelectAll()
            fragment.sudokuListAdapter.getSelectedIds() shouldBe
                setOf(first, second)
                    .map {
                        it.info.id
                            .hashCode()
                            .toLong()
                    }.toSet()

            fragment.clickDelete()
            idleMainLooper()

            drawerLayout.isActionMode.shouldBeFalse()
            fragment.sudokuListAdapter.isActionMode.shouldBeFalse()
            runBlocking { sudokusRepository.observeAllSummaries().first() }.shouldBeEmpty()
        }
    }

    @Test
    fun `tapping delete twice deletes the selection once and ends action mode`() {
        val first = historySudoku()
        val second = historySudoku()
        runBlocking {
            saveSudoku(first)
            saveSudoku(second)
        }
        launch { fragment ->
            val drawerLayout = fragment.requireActivity().findViewById<DrawerLayout>(R.id.drawerLayout)
            fragment.launchActionMode()
            idleMainLooper()
            fragment.clickSelectAll()
            deleteGate = CompletableDeferred()
            deleteCalls = 0

            fragment.clickDelete()
            fragment.clickDelete()
            idleMainLooper()

            drawerLayout.isActionMode.shouldBeTrue()
            val progressDialog = ShadowDialog.getLatestDialog() as ProgressDialog
            progressDialog.isShowing.shouldBeTrue()

            deleteGate.complete(Unit)
            idleMainLooper()

            deleteCalls shouldBe 1
            drawerLayout.isActionMode.shouldBeFalse()
            fragment.sudokuListAdapter.isActionMode.shouldBeFalse()
            progressDialog.isShowing.shouldBeFalse()
            runBlocking { sudokusRepository.observeAllSummaries().first() }.shouldBeEmpty()
        }
    }

    @Test
    fun `leaving and returning to the app during a deletion keeps one progress dialog and dismisses it when the deletion ends`() {
        runBlocking { saveSudoku(historySudoku()) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.onTabItemSelected(0) }
            idleMainLooper()
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<TabHistory>()
                        .first()
                fragment.launchActionMode()
                idleMainLooper()
                fragment.clickSelectAll()
                deleteGate = CompletableDeferred()
                fragment.clickDelete()
            }
            idleMainLooper()

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            idleMainLooper()
            deleteGate.complete(Unit)
            idleMainLooper()

            val progressDialogs = ShadowDialog.getShownDialogs().filterIsInstance<ProgressDialog>()
            progressDialogs.size shouldBe 1
            progressDialogs.single().isShowing.shouldBeFalse()
            runBlocking { sudokusRepository.observeAllSummaries().first() }.shouldBeEmpty()
        }
    }

    @Test
    fun `an unrecognized action mode menu item is ignored`() {
        val sudoku = historySudoku()
        runBlocking { saveSudoku(sudoku) }
        launch { fragment ->
            val drawerLayout = fragment.requireActivity().findViewById<DrawerLayout>(R.id.drawerLayout)
            fragment.launchActionMode()
            idleMainLooper()

            fragment.clickSelectAll()
            val menu = fragment.bottomActionModeBar().menu
            menu.add(Menu.NONE, 12345, Menu.NONE, "other")
            menu.performIdentifierAction(12345, 0).shouldBeTrue()
            idleMainLooper()

            drawerLayout.isActionMode.shouldBeTrue()
            runBlocking { sudokusRepository.observeAllSummaries().first() } shouldBe listOf(sudoku.summary())
        }
    }

    @Test
    fun `clicking an item outside action mode opens it in SudokuActivity`() {
        val sudoku = historySudoku()
        runBlocking { saveSudoku(sudoku) }
        launch { fragment ->
            val holder = fragment.sudokuListAdapter.onCreateViewHolder(fragment.binding.sudokuHistoryList, SudokuItem.VIEW_TYPE)
            fragment.sudokuListAdapter.onClickItem?.invoke(0, SudokuItem(sudoku.summary(), "label"), holder)
            idleMainLooper()

            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe SudokuActivity::class.java.name
            started.getStringExtra(KEY_SUDOKU_ID) shouldBe sudoku.info.id.value
        }
    }

    @Test
    fun `clicking an item twice outside action mode opens one SudokuActivity`() {
        val sudoku = historySudoku()
        runBlocking { saveSudoku(sudoku) }
        launch { fragment ->
            val holder = fragment.sudokuListAdapter.onCreateViewHolder(fragment.binding.sudokuHistoryList, SudokuItem.VIEW_TYPE)
            fragment.sudokuListAdapter.onClickItem?.invoke(0, SudokuItem(sudoku.summary(), "label"), holder)
            fragment.sudokuListAdapter.onClickItem?.invoke(0, SudokuItem(sudoku.summary(), "label"), holder)
            idleMainLooper()

            val activity = shadowOf(fragment.requireActivity())
            activity.nextStartedActivity?.getStringExtra(KEY_SUDOKU_ID) shouldBe sudoku.info.id.value
            activity.nextStartedActivity shouldBe null
        }
    }

    @Test
    fun `clicking an item while in action mode toggles its selection instead of opening it`() {
        val sudoku = historySudoku()
        runBlocking { saveSudoku(sudoku) }
        launch { fragment ->
            fragment.launchActionMode()
            idleMainLooper()

            val holder = fragment.sudokuListAdapter.onCreateViewHolder(fragment.binding.sudokuHistoryList, SudokuItem.VIEW_TYPE)
            val item = SudokuItem(sudoku.summary(), "label")
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
            idleMainLooper()
            fragment.clickSelectAll()

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
            idleMainLooper()
            var selectedId = 0L
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<TabHistory>()
                        .first()
                fragment.launchActionMode()
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<TabHistory>()
                        .first()
                fragment.clickSelectAll()
                selectedId = fragment.sudokuListAdapter.getSelectedIds().first()
            }

            scenario.recreate()
            idleMainLooper()

            scenario.onActivity { activity ->
                activity.onTabItemSelected(0)
            }
            idleMainLooper()
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
    fun `clicking a separator item outside action mode does not open or select anything`() {
        val sudoku = historySudoku()
        runBlocking { saveSudoku(sudoku) }
        launch { fragment ->
            val holder = fragment.sudokuListAdapter.onCreateViewHolder(fragment.binding.sudokuHistoryList, SeparatorItem.VIEW_TYPE)
            fragment.sudokuListAdapter.onClickItem?.invoke(0, SeparatorItem("label"), holder)
            idleMainLooper()

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
