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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.di.MainDispatcher
import de.lemke.sudoku.HiltTestRule
import de.lemke.sudoku.R
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.DeleteSudokusUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.ui.MainActivity
import de.lemke.sudoku.ui.utils.listSudoku
import io.kotest.matchers.collections.shouldBeIn
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** sdk = 36: Robolectric's max supported SDK. */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class TabHistoryRevealTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

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
    lateinit var deleteSudokus: DeleteSudokusUseCase

    private val seededIds = List(SEEDED_COUNT) { SudokuId.generate() }

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        // Robolectric skips the Play Games SDK's auto-init ContentProvider under HiltTestApplication.
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
        seededIds.indices.forEach { save(seededSudoku(it, seconds = 0, updated = SEEDED_DAY.plusMinutes(it.toLong()))) }
    }

    @Test
    fun `a sudoku played while the history was stopped moves to the top and is revealed`() {
        launchHistory { scenario ->
            scenario.scrollHistoryToBottom()
            scenario.moveToState(Lifecycle.State.CREATED)
            save(seededSudoku(0, seconds = 75, updated = SEEDED_DAY.plusHours(2)))
            awaitUntil { scenario.read { it.firstHistorySudokuId() } == seededIds[0] }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil { scenario.read { it.adapterHoldsViewModelHistory() } }
            scenario.onActivity { activity ->
                activity.historyList().firstVisiblePosition() shouldBe 0
                activity.historyList().smallTextAt(1) shouldBe "01:15 | 0% | Errors: 0/3 | Hints: 0"
            }
        }
    }

    @Test
    fun `a sudoku played after the history flow stopped moves to the top and is revealed`() {
        launchHistory { scenario ->
            scenario.scrollHistoryToBottom()
            scenario.moveToState(Lifecycle.State.CREATED)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
            save(seededSudoku(0, seconds = 75, updated = SEEDED_DAY.plusHours(2)))
            idle()
            scenario.read { it.firstHistorySudokuId() } shouldBe seededIds.last()
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil { scenario.read { it.firstHistorySudokuId() == seededIds[0] && it.adapterHoldsViewModelHistory() } }
            scenario.onActivity { activity ->
                activity.historyList().firstVisiblePosition() shouldBe 0
                activity.historyList().smallTextAt(1) shouldBe "01:15 | 0% | Errors: 0/3 | Hints: 0"
            }
        }
    }

    @Test
    fun `an older sudoku imported below the viewport is revealed with its row visible`() {
        repeat(OLDER_COUNT) { save(newSudoku(seconds = 0, updated = OLDER_DAY.plusMinutes(it.toLong()))) }
        launchHistory(rows = SEEDED_ROWS + OLDER_COUNT + 1) { scenario ->
            scenario.read { it.historyList().firstVisiblePosition() } shouldBe 0
            scenario.moveToState(Lifecycle.State.CREATED)
            save(newSudoku(seconds = 75, updated = IMPORTED_DAY))
            awaitUntil { scenario.read { it.historySize() } == SEEDED_ROWS + OLDER_COUNT + 3 }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil { scenario.read { it.adapterHoldsViewModelHistory() } }
            scenario.onActivity { activity ->
                val layoutManager = activity.historyList().layoutManager as LinearLayoutManager
                val completelyVisible =
                    (layoutManager.findFirstCompletelyVisibleItemPosition()..layoutManager.findLastCompletelyVisibleItemPosition()).toList()
                IMPORTED_ROW shouldBeIn completelyVisible
                activity.historyList().smallTextAt(IMPORTED_ROW) shouldBe "01:15 | 0% | Errors: 0/3 | Hints: 0"
            }
        }
    }

    @Test
    fun `a sudoku event handled before the history commits the sudoku reveals it`() {
        launchHistory { scenario ->
            scenario.read { it.historyList().firstVisiblePosition() } shouldBe 0
            scenario.moveToState(Lifecycle.State.CREATED)
            save(newSudoku(seconds = 75, updated = NEXT_DAY))
            awaitUntil { scenario.read { it.historySize() } == SEEDED_ROWS + 2 }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil { scenario.read { it.adapterHoldsViewModelHistory() } }
            scenario.onActivity { activity ->
                activity.historyList().firstVisiblePosition() shouldBe 0
                activity.historyList().smallTextAt(1) shouldBe "01:15 | 0% | Errors: 0/3 | Hints: 0"
            }
        }
    }

    @Test
    fun `a sudoku event handled after the history committed the sudoku reveals it`() {
        launchHistory { scenario ->
            scenario.scrollHistoryToBottom()
            scenario.moveToState(Lifecycle.State.CREATED)
            save(newSudoku(seconds = 75, updated = NEXT_DAY))
            awaitUntil { scenario.read { it.historySize() } == SEEDED_ROWS + 2 }
            scenario.onActivity { it.recreateHistoryView() }
            awaitUntil { scenario.read { it.adapterHoldsViewModelHistory() } }
            scenario.read { it.historyList().firstVisiblePosition() } shouldNotBe 0
            scenario.moveToState(Lifecycle.State.RESUMED)
            idle()
            scenario.onActivity { activity ->
                activity.historyList().firstVisiblePosition() shouldBe 0
                activity.historyList().smallTextAt(1) shouldBe "01:15 | 0% | Errors: 0/3 | Hints: 0"
            }
        }
    }

    @Test
    fun `two sudokus saved in a row reveal the newer one`() {
        launchHistory { scenario ->
            scenario.scrollHistoryToBottom()
            scenario.moveToState(Lifecycle.State.CREATED)
            save(newSudoku(seconds = 75, updated = NEXT_DAY))
            awaitUntil { scenario.read { it.historySize() } == SEEDED_ROWS + 2 }
            save(newSudoku(seconds = 90, updated = NEXT_DAY.plusHours(1)))
            awaitUntil { scenario.read { it.historySize() } == SEEDED_ROWS + 3 }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil { scenario.read { it.adapterHoldsViewModelHistory() } }
            scenario.onActivity { activity ->
                activity.historyList().firstVisiblePosition() shouldBe 0
                activity.historyList().smallTextAt(1) shouldBe "01:30 | 0% | Errors: 0/3 | Hints: 0"
                activity.historyList().smallTextAt(2) shouldBe "01:15 | 0% | Errors: 0/3 | Hints: 0"
            }
        }
    }

    @Test
    fun `a sudoku deleted before the history shows it leaves the scroll position alone`() {
        launchHistory { scenario ->
            val scrolledTo = scenario.scrollHistoryToBottom()
            scenario.moveToState(Lifecycle.State.CREATED)
            val deleted = newSudoku(seconds = 75, updated = NEXT_DAY)
            save(deleted)
            awaitUntil { scenario.read { it.historySize() } == SEEDED_ROWS + 2 }
            runBlocking { deleteSudokus(listOf(deleted)) }
            awaitUntil { scenario.read { it.historySize() } == SEEDED_ROWS }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil { scenario.read { it.adapterHoldsViewModelHistory() } }
            scenario.onActivity { activity ->
                activity.historyList().adapter?.itemCount shouldBe SEEDED_ROWS
                activity.historyList().firstVisiblePosition() shouldBe scrolledTo
            }
        }
    }

    private fun seededSudoku(
        index: Int,
        seconds: Int,
        updated: LocalDateTime,
    ): Sudoku =
        listSudoku(
            sudokuId = seededIds[index],
            modeLevel = MODE_NORMAL,
            filled = 0,
            errorsMade = 0,
            seconds = seconds,
            created = SEEDED_DAY,
            updated = updated,
        )

    private fun newSudoku(
        seconds: Int,
        updated: LocalDateTime,
    ): Sudoku =
        listSudoku(
            sudokuId = SudokuId.generate(),
            modeLevel = MODE_NORMAL,
            filled = 0,
            errorsMade = 0,
            seconds = seconds,
            created = updated,
        )

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

    private fun launchHistory(
        rows: Int = SEEDED_ROWS,
        block: (ActivityScenario<MainActivity>) -> Unit,
    ) = ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        scenario.onActivity { it.onTabItemSelected(0) }
        awaitUntil { scenario.read { it.historyList().adapter?.itemCount } == rows }
        block(scenario)
    }

    private fun ActivityScenario<MainActivity>.scrollHistoryToBottom(): Int {
        onActivity { it.historyList().scrollToPosition(SEEDED_ROWS - 1) }
        idle()
        return read { it.historyList().firstVisiblePosition() }.also { it shouldNotBe 0 }
    }

    private fun <T> ActivityScenario<MainActivity>.read(block: (MainActivity) -> T): T {
        val result = mutableListOf<T>()
        onActivity { result += block(it) }
        return result.single()
    }

    private fun MainActivity.historyTab(): TabHistory = supportFragmentManager.fragments.filterIsInstance<TabHistory>().first()

    private fun MainActivity.historyViewModel(): TabHistoryViewModel = ViewModelProvider(historyTab())[TabHistoryViewModel::class.java]

    private fun MainActivity.historyList(): RecyclerView = historyTab().binding.sudokuHistoryList

    private fun MainActivity.historySize(): Int = historyViewModel().sudokuHistory.value.size

    private fun MainActivity.firstHistorySudokuId(): SudokuId? =
        historyViewModel()
            .sudokuHistory.value
            .filterIsInstance<SudokuItem>()
            .firstOrNull()
            ?.sudoku
            ?.id

    private fun MainActivity.adapterHoldsViewModelHistory(): Boolean {
        val history = historyViewModel().sudokuHistory.value
        val committed = historyTab().sudokuListAdapter.currentList
        return committed.size == history.size && history.indices.all { committed[it] === history[it] }
    }

    private fun MainActivity.recreateHistoryView() {
        val tab = historyTab()
        supportFragmentManager.beginTransaction().detach(tab).commitNowAllowingStateLoss()
        supportFragmentManager.beginTransaction().attach(tab).commitNowAllowingStateLoss()
    }

    private fun RecyclerView.firstVisiblePosition(): Int = (layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()

    private fun RecyclerView.smallTextAt(position: Int): String? =
        findViewHolderForAdapterPosition(position)
            ?.itemView
            ?.findViewById<TextView>(R.id.item_text_small)
            ?.text
            ?.toString()

    private companion object {
        const val SEEDED_COUNT = 30
        const val SEEDED_ROWS = SEEDED_COUNT + 1
        const val OLDER_COUNT = 10
        const val IMPORTED_ROW = SEEDED_ROWS + 1
        const val AWAIT_ATTEMPTS = 200
        const val AWAIT_STEP_MS = 10L
        val SEEDED_DAY: LocalDateTime = LocalDateTime.of(2026, 1, 15, 9, 0)
        val NEXT_DAY: LocalDateTime = LocalDateTime.of(2026, 1, 16, 10, 0)
        val IMPORTED_DAY: LocalDateTime = LocalDateTime.of(2026, 1, 12, 10, 0)
        val OLDER_DAY: LocalDateTime = LocalDateTime.of(2026, 1, 5, 9, 0)
    }
}
