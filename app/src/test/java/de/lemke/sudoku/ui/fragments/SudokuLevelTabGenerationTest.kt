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

import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.awaitMainLooperUntil
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.read
import de.lemke.sudoku.TestDispatchers
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.di.SolvedBoardGeneratorModule
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.SolvedBoardGenerator
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.ui.SudokuLevelActivity
import de.lemke.sudoku.ui.utils.listSudoku
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** sdk = 36: Robolectric's max supported SDK. */
@UninstallModules(SolvedBoardGeneratorModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuLevelTabGenerationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    private var generationGate = CountDownLatch(0)

    @BindValue
    @JvmField
    val solvedBoardGenerator: SolvedBoardGenerator =
        SolvedBoardGenerator { schema ->
            generationGate.await()
            PatternSolvedBoardGenerator().generate(schema)
        }

    @Inject
    lateinit var testDispatchers: TestDispatchers

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var userSettings: UserSettings

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    private val levelOneId = SudokuId.generate()

    @Before
    fun setup() {
        hiltRule.inject()
        testDispatchers.default.redirectTo(Dispatchers.Default)
        settings.bypassOobe()
        userSettings.currentLevelTab = 0
    }

    @After
    fun resetDispatchers() {
        testDispatchers.reset()
    }

    @Test
    fun `generating the next level after returning from a finished level keeps the level list at the top`() {
        save(levelOne(filled = 0))
        ActivityScenario.launch(SudokuLevelActivity::class.java).use { scenario ->
            awaitMainLooperUntil { scenario.read { it.levelState().sudokuLevel.size == 1 && !it.progressBarShown() } }
            generationGate = CountDownLatch(1)
            try {
                scenario.moveToState(Lifecycle.State.CREATED)
                save(levelOne(filled = 16))
                scenario.moveToState(Lifecycle.State.RESUMED)
                awaitMainLooperUntil { scenario.read { it.progressBarShown() && !it.levelList().isLayoutRequested } }

                scenario.onActivity { activity ->
                    activity.levelState().isGeneratingNextLevel.shouldBeTrue()
                    activity.levelList().isVisible.shouldBeTrue()
                    activity.levelList().top shouldBe 0
                }
            } finally {
                generationGate.countDown()
            }
            awaitMainLooperUntil { scenario.read { it.levelState().hasNextLevelToStart && !it.progressBarShown() } }
            scenario.onActivity { activity -> activity.levelList().top shouldBe 0 }
        }
    }

    private fun levelOne(filled: Int): Sudoku =
        listSudoku(
            sudokuId = levelOneId,
            modeLevel = 1,
            filled = filled,
            errorsMade = 0,
            seconds = 40,
            created = LocalDateTime.of(2026, 1, 15, 9, 0),
        )

    private fun save(sudoku: Sudoku) = runBlocking { saveSudoku(sudoku) }

    private fun SudokuLevelActivity.levelTab(): SudokuLevelTab =
        supportFragmentManager.fragments
            .filterIsInstance<SudokuLevelTab>()
            .first { it.arguments?.getInt(SudokuLevelTab.KEY_SIZE) == SudokuSize.FOUR.value }

    private fun SudokuLevelActivity.levelState(): SudokuLevelTabUiState = levelTab().viewModel.state.value

    private fun SudokuLevelActivity.levelList(): RecyclerView = levelTab().binding.sudokuLevelsRecycler

    private fun SudokuLevelActivity.progressBarShown(): Boolean = levelTab().binding.tabLevelProgressBar.isVisible
}
