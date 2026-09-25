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

import android.os.Looper
import androidx.lifecycle.Lifecycle
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
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.di.ClockModule
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.InitDailySudokusUseCase
import de.lemke.sudoku.domain.ObserveDailySudokusUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.ui.utils.awaitSmallText
import de.lemke.sudoku.ui.utils.listSudoku
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
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
@UninstallModules(DispatchersModule::class, ClockModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class DailySudokuListRefreshTest {
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
    lateinit var userSettings: UserSettings

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Inject
    lateinit var initDailySudokus: InitDailySudokusUseCase

    @Inject
    lateinit var observeDailySudokus: ObserveDailySudokusUseCase

    private val sudokuId = SudokuId.generate()

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    private fun todaysSudoku(
        filled: Int,
        errorsMade: Int,
        seconds: Int,
    ): Sudoku =
        listSudoku(
            sudokuId = sudokuId,
            modeLevel = MODE_DAILY,
            filled = filled,
            errorsMade = errorsMade,
            seconds = seconds,
            created = LocalDateTime.now(testClock),
        )

    private fun save(sudoku: Sudoku) = runBlocking { saveSudoku(sudoku) }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `the state carries the new stats when the daily sudoku is saved again under the same id`() {
        save(todaysSudoku(filled = 0, errorsMade = 0, seconds = 0))
        val viewModel = DailySudokuViewModel(userSettings, initDailySudokus, observeDailySudokus, testClock)
        val collection = viewModel.state.launchIn(CoroutineScope(Dispatchers.Main))
        try {
            idle()

            save(todaysSudoku(filled = 16, errorsMade = 2, seconds = 75))
            idle()

            val sudoku = shownSudoku(viewModel)
            sudoku.errorsMade shouldBe 2
            sudoku.seconds shouldBe 75
            sudoku.progress shouldBe 100
            sudoku.completed shouldBe true
        } finally {
            collection.cancel()
        }
    }

    @Test
    fun `resuming the daily list shows the stats saved while it was stopped`() {
        save(todaysSudoku(filled = 0, errorsMade = 0, seconds = 0))
        ActivityScenario.launch(DailySudokuActivity::class.java).use { scenario ->
            idle()
            scenario.onActivity { activity ->
                activity.binding.dailySudokuRecycler.awaitSmallText(1, "00:00 | 0% | Errors: 0/3") shouldBe "00:00 | 0% | Errors: 0/3"
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            save(todaysSudoku(filled = 16, errorsMade = 2, seconds = 75))
            idle()
            scenario.onActivity { activity -> shownSudoku(activity.viewModel).seconds shouldBe 75 }
            scenario.moveToState(Lifecycle.State.RESUMED)
            idle()

            scenario.onActivity { activity ->
                activity.binding.dailySudokuRecycler.awaitSmallText(1, "01:15 | Errors: 2/3") shouldBe "01:15 | Errors: 2/3"
            }
        }
    }

    @Test
    fun `resuming the daily list after the stop timeout queries the stats saved while it was stopped`() {
        save(todaysSudoku(filled = 0, errorsMade = 0, seconds = 0))
        ActivityScenario.launch(DailySudokuActivity::class.java).use { scenario ->
            idle()
            scenario.onActivity { activity ->
                activity.binding.dailySudokuRecycler.awaitSmallText(1, "00:00 | 0% | Errors: 0/3") shouldBe "00:00 | 0% | Errors: 0/3"
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
            save(todaysSudoku(filled = 16, errorsMade = 2, seconds = 75))
            idle()
            scenario.onActivity { activity -> shownSudoku(activity.viewModel).seconds shouldBe 0 }
            scenario.moveToState(Lifecycle.State.RESUMED)
            idle()

            scenario.onActivity { activity ->
                activity.binding.dailySudokuRecycler.awaitSmallText(1, "01:15 | Errors: 2/3") shouldBe "01:15 | Errors: 2/3"
                shownSudoku(activity.viewModel).seconds shouldBe 75
            }
        }
    }

    private fun shownSudoku(viewModel: DailySudokuViewModel): Sudoku =
        viewModel.state.value.sudokus
            .filterIsInstance<SudokuItem>()
            .single()
            .sudoku
}
