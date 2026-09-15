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
import androidx.recyclerview.widget.RecyclerView
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
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.ui.MainActivity
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Covers [TabStatistics.updateStatistics] and its `StatisticsListAdapter` against a real
 * [CalculateStatisticsUseCase][de.lemke.sudoku.domain.CalculateStatisticsUseCase] result — an empty repository (every
 * "-"/placeholder branch) and one completed sudoku with a two-hour play time (the non-null/hours branches).
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class TabStatisticsFragmentTest {
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
        // MainActivity talks to PlayGames on launch; the SDK is normally auto-initialized by its
        // manifest-merged ContentProvider, which Robolectric does not run under HiltTestApplication.
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun completedSudoku(seconds: Int): Sudoku {
        val size = 4
        val blockSize = sqrt(size.toDouble()).toInt()
        return Sudoku.create(
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = MODE_NORMAL,
            seconds = seconds,
            fields =
                MutableList(size * size) { index ->
                    val row = index / size
                    val col = index % size
                    val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size + 1
                    Field(position = Position.create(index, size), solution = solution, value = solution, given = true)
                },
        )
    }

    private fun startedSudoku(): Sudoku {
        val size = 4
        return Sudoku.create(
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = MODE_NORMAL,
            fields = MutableList(size * size) { index -> Field(position = Position.create(index, size), solution = 1) },
        )
    }

    private fun launch(block: (TabStatistics) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.onTabItemSelected(2) }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<TabStatistics>()
                        .first()
                block(fragment)
            }
        }
    }

    @Test
    fun `an empty history renders every statistic as its placeholder`() =
        launch { fragment ->
            fragment.statisticsList.isNotEmpty().shouldBeTrue()
            fragment.binding.statisticsListRecycler.adapter
                ?.itemCount shouldBe fragment.statisticsList.size
            val bestTime = fragment.statisticsList.first { it.first == fragment.getString(de.lemke.sudoku.R.string.best_time) }
            bestTime.second.shouldContain("--:--")
        }

    @Test
    fun `a completed two-hour sudoku populates the best-time and total-time entries`() {
        runBlocking { saveSudoku(completedSudoku(seconds = 7200)) }
        launch { fragment ->
            val bestTime = fragment.statisticsList.first { it.first == fragment.getString(de.lemke.sudoku.R.string.best_time) }
            bestTime.second.shouldContain("02:00:00")
            val totalTime = fragment.statisticsList.first { it.first == fragment.getString(de.lemke.sudoku.R.string.total_time_played) }
            totalTime.second.shouldContain("2h 0m")
        }
    }

    @Test
    fun `a completed game populates both the started and won difficulty and size stats`() {
        runBlocking { saveSudoku(completedSudoku(seconds = 60)) }
        launch { fragment ->
            listOf(
                de.lemke.sudoku.R.string.most_games_started,
                de.lemke.sudoku.R.string.most_games_won,
            ).forEach { label ->
                fragment.statisticsList
                    .filter { it.first == fragment.getString(label) }
                    .forEach { it.second shouldNotBe "-" }
            }
        }
    }

    @Test
    fun `the first statistics load inserts rows instead of changing them`() {
        var insertedCount = -1
        var changedCalled = false
        // MainActivity.initFragments() adds all 3 tab fragments in onCreate(); their views (and bindings) exist by
        // the time the host reaches STARTED, but none of them use setMaxLifecycle, so they only reach RESUMED - and
        // collectState's minActiveState=RESUMED starts collecting - once the host itself does. Stopping at start()
        // registers the observer in that window, unlike a fully-launched ActivityScenario (already RESUMED by the
        // time it hands back control, so the first emission has already happened).
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start()
        val fragment =
            controller
                .get()
                .supportFragmentManager.fragments
                .filterIsInstance<TabStatistics>()
                .first()
        fragment.binding.statisticsListRecycler.adapter
            .shouldNotBeNull()
            .registerAdapterDataObserver(
                object : RecyclerView.AdapterDataObserver() {
                    override fun onItemRangeInserted(
                        positionStart: Int,
                        itemCount: Int,
                    ) {
                        insertedCount = itemCount
                    }

                    override fun onItemRangeChanged(
                        positionStart: Int,
                        itemCount: Int,
                    ) {
                        changedCalled = true
                    }
                },
            )

        controller.resume()
        // The state flow's calculation crosses to a real Dispatchers.IO thread (bound above), so this needs real
        // wall-clock polling, not a single idle() - see awaitMainLooperIdleUntil elsewhere in this fleet.
        val deadline = System.currentTimeMillis() + 5000
        while (insertedCount == -1 && !changedCalled && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
        check(insertedCount != -1 || changedCalled) { "TabStatistics's first load did not notify within 5000ms" }

        insertedCount shouldBeGreaterThan 0
        changedCalled.shouldBeFalse()
        controller.pause().stop().destroy()
    }

    @Test
    fun `an unfinished game populates most-started stats but leaves most-won at the placeholder`() {
        runBlocking { saveSudoku(startedSudoku()) }
        launch { fragment ->
            fragment.statisticsList
                .filter { it.first == fragment.getString(de.lemke.sudoku.R.string.most_games_started) }
                .forEach { it.second shouldNotBe "-" }
            fragment.statisticsList
                .filter { it.first == fragment.getString(de.lemke.sudoku.R.string.most_games_won) }
                .forEach { it.second shouldBe "-" }
        }
    }
}
