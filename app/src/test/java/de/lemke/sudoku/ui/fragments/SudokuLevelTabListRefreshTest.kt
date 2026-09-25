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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
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
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.di.SolvedBoardGeneratorModule
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.GetSudokuUseCase
import de.lemke.sudoku.domain.InitSudokuLevelUseCase
import de.lemke.sudoku.domain.ObserveSudokuLevelUseCase
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.SolvedBoardGenerator
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.SIZE_4X4
import de.lemke.sudoku.domain.model.Sudoku.Companion.SIZE_9X9
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.ui.SudokuLevelActivity
import de.lemke.sudoku.ui.utils.awaitSmallText
import de.lemke.sudoku.ui.utils.listSudoku
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.LocalDateTime
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
@UninstallModules(DispatchersModule::class, SolvedBoardGeneratorModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuLevelTabListRefreshTest {
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

    @Inject
    lateinit var initSudokuLevel: InitSudokuLevelUseCase

    @Inject
    lateinit var observeSudokuLevel: ObserveSudokuLevelUseCase

    @Inject
    lateinit var getMaxSudokuLevel: GetMaxSudokuLevelUseCase

    @Inject
    lateinit var generateSudokuLevel: GenerateSudokuLevelUseCase

    @Inject
    lateinit var getSudoku: GetSudokuUseCase

    private val currentLevelId = SudokuId.generate()

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    private fun completedLevelOne(): Sudoku =
        listSudoku(
            sudokuId = SudokuId.generate(),
            modeLevel = 1,
            filled = 16,
            errorsMade = 0,
            seconds = 40,
            created = LocalDateTime.of(2026, 1, 14, 9, 0),
        )

    private fun currentLevelTwo(
        filled: Int,
        errorsMade: Int,
        seconds: Int,
    ): Sudoku =
        listSudoku(
            sudokuId = currentLevelId,
            modeLevel = 2,
            filled = filled,
            errorsMade = errorsMade,
            seconds = seconds,
            created = LocalDateTime.of(2026, 1, 15, 9, 0),
        )

    private fun dailySudoku(): Sudoku =
        Sudoku.create(
            size = SIZE_9X9,
            difficulty = Difficulty.EASY,
            modeLevel = Sudoku.MODE_DAILY,
            fields = MutableList(SIZE_9X9 * SIZE_9X9) { Field(Position.create(it, SIZE_9X9), solution = it % SIZE_9X9 + 1) },
        )

    private fun save(sudoku: Sudoku) = runBlocking { saveSudoku(sudoku) }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `the level list carries the new stats when the current level is saved again under the same id`() {
        save(completedLevelOne())
        save(currentLevelTwo(filled = 0, errorsMade = 0, seconds = 0))
        val viewModel = newViewModel()
        val collection = viewModel.state.launchIn(CoroutineScope(Dispatchers.Main))
        try {
            idle()

            save(currentLevelTwo(filled = 8, errorsMade = 2, seconds = 75))
            idle()

            val sudoku = topSudoku(viewModel)
            sudoku.id shouldBe currentLevelId
            sudoku.errorsMade shouldBe 2
            sudoku.seconds shouldBe 75
            sudoku.progress shouldBe 50
            sudoku.completed shouldBe false
        } finally {
            collection.cancel()
        }
    }

    @Test
    fun `the next level after a daily-only 9x9 history is level 1`() {
        save(dailySudoku())
        val viewModel = newViewModel(SIZE_9X9)
        val collection = viewModel.state.launchIn(CoroutineScope(Dispatchers.Main))
        try {
            idle()

            viewModel.state.value.sudokuLevel
                .first()
                .label shouldBe "1"
        } finally {
            collection.cancel()
        }
    }

    @Test
    fun `confirming a next level whose level is already saved keeps the saved progress`() {
        save(completedLevelOne())
        save(currentLevelTwo(filled = 8, errorsMade = 2, seconds = 75))

        runBlocking { newViewModel().onNextLevelSudokuConfirmed(currentLevelTwo(filled = 0, errorsMade = 0, seconds = 0)) }

        val saved = runBlocking { getSudoku(currentLevelId) }.shouldNotBeNull()
        saved.errorsMade shouldBe 2
        saved.seconds shouldBe 75
        saved.progress shouldBe 50
    }

    @Test
    fun `confirming a next level above the saved max level saves it`() {
        save(completedLevelOne())

        runBlocking { newViewModel().onNextLevelSudokuConfirmed(currentLevelTwo(filled = 0, errorsMade = 0, seconds = 0)) }

        runBlocking { getMaxSudokuLevel(SIZE_4X4) } shouldBe 2
        runBlocking { getSudoku(currentLevelId) }.shouldNotBeNull().modeLevel shouldBe 2
    }

    @Test
    fun `resuming the level tab shows the stats saved while it was stopped`() {
        userSettings.currentLevelTab = 0
        save(completedLevelOne())
        save(currentLevelTwo(filled = 0, errorsMade = 0, seconds = 0))
        ActivityScenario.launch(SudokuLevelActivity::class.java).use { scenario ->
            idle()
            scenario.onActivity { activity ->
                levelList(activity).awaitSmallText(0, "00:00 | 0% | Errors: 0/3") shouldBe "00:00 | 0% | Errors: 0/3"
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            save(currentLevelTwo(filled = 8, errorsMade = 2, seconds = 75))
            idle()
            scenario.onActivity { activity -> topSudoku(levelTab(activity).viewModel).seconds shouldBe 75 }
            scenario.moveToState(Lifecycle.State.RESUMED)
            idle()

            scenario.onActivity { activity ->
                levelList(activity).awaitSmallText(0, "01:15 | 50% | Errors: 2/3") shouldBe "01:15 | 50% | Errors: 2/3"
            }
        }
    }

    @Test
    fun `resuming the level tab after the stop timeout queries the stats saved while it was stopped`() {
        userSettings.currentLevelTab = 0
        save(completedLevelOne())
        save(currentLevelTwo(filled = 0, errorsMade = 0, seconds = 0))
        ActivityScenario.launch(SudokuLevelActivity::class.java).use { scenario ->
            idle()
            scenario.onActivity { activity ->
                levelList(activity).awaitSmallText(0, "00:00 | 0% | Errors: 0/3") shouldBe "00:00 | 0% | Errors: 0/3"
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
            save(currentLevelTwo(filled = 8, errorsMade = 2, seconds = 75))
            idle()
            scenario.onActivity { activity -> topSudoku(levelTab(activity).viewModel).seconds shouldBe 0 }
            scenario.moveToState(Lifecycle.State.RESUMED)
            idle()

            scenario.onActivity { activity ->
                levelList(activity).awaitSmallText(0, "01:15 | 50% | Errors: 2/3") shouldBe "01:15 | 50% | Errors: 2/3"
                topSudoku(levelTab(activity).viewModel).seconds shouldBe 75
            }
        }
    }

    private fun newViewModel(size: Int = SIZE_4X4) =
        SudokuLevelTabViewModel(
            initSudokuLevel,
            observeSudokuLevel,
            getMaxSudokuLevel,
            generateSudokuLevel,
            saveSudoku,
            SavedStateHandle(mapOf("size" to size)),
        )

    private fun topSudoku(viewModel: SudokuLevelTabViewModel): Sudoku =
        viewModel.state.value.sudokuLevel
            .filterIsInstance<SudokuItem>()
            .first()
            .sudoku

    private fun levelTab(activity: SudokuLevelActivity) =
        activity.supportFragmentManager.fragments
            .filterIsInstance<SudokuLevelTab>()
            .first { it.arguments?.getInt("size") == SIZE_4X4 }

    private fun levelList(activity: SudokuLevelActivity) = levelTab(activity).binding.sudokuLevelsRecycler
}
