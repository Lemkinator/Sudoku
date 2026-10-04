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

import android.content.Intent
import android.os.Looper
import android.view.View
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
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
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.di.SolvedBoardGeneratorModule
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.SolvedBoardGenerator
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.ui.DailySudokuActivity
import de.lemke.sudoku.ui.MainActivity
import de.lemke.sudoku.ui.PausableDispatcher
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.SudokuLevelActivity
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** sdk = 36: Robolectric's max supported SDK. */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class, SolvedBoardGeneratorModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class TabSudokuFragmentTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    private val pausableDefaultDispatcher = PausableDispatcher(Dispatchers.Main)

    @BindValue
    @DefaultDispatcher
    @JvmField
    val testDefaultDispatcher: CoroutineDispatcher = pausableDefaultDispatcher

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
    lateinit var saveSudoku: SaveSudokuUseCase

    @Inject
    lateinit var getAllSudokus: GetAllSudokusUseCase

    @Inject
    lateinit var userSettings: UserSettings

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun formulaicSudoku(
        size: SudokuSize = SudokuSize.FOUR,
        modeLevel: Int = MODE_NORMAL,
        allCorrect: Boolean = false,
    ): Sudoku {
        val blockSize = size.blockSize
        return Sudoku.create(
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = modeLevel,
            fields =
                MutableList(size.cellCount) { index ->
                    val row = index / size.value
                    val col = index % size.value
                    val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size.value + 1
                    val given = index % 3 == 0 || allCorrect
                    Field(
                        position = Position.create(index, size),
                        solution = solution,
                        value = if (given) solution else null,
                        given = given,
                    )
                },
        )
    }

    private fun launch(block: (TabSudoku) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<TabSudoku>()
                        .first()
                block(fragment)
            }
        }
    }

    private fun failOnUncaughtException(block: () -> Unit) {
        val thread = Thread.currentThread()
        val previous = thread.uncaughtExceptionHandler
        val uncaught = mutableListOf<Throwable>()
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e -> uncaught += e }
        try {
            block()
        } finally {
            thread.uncaughtExceptionHandler = previous
        }
        uncaught.shouldBeEmpty()
    }

    private fun click(view: View) {
        view.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun doubleClick(view: View) {
        view.performClick()
        view.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `newGameButton generates and starts a new sudoku`() =
        launch { fragment ->
            click(fragment.requireView().findViewById(R.id.newGameButton))
            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe SudokuActivity::class.java.name
        }

    @Test
    fun `a sudoku generated across a recreation opens from the recreated activity`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            pausableDefaultDispatcher.pause()
            scenario.onActivity { activity -> activity.findViewById<View>(R.id.newGameButton).performClick() }
            scenario.recreate()
            pausableDefaultDispatcher.resume()
            shadowOf(Looper.getMainLooper()).idle()
            val generated = runBlocking { getAllSudokus() }.single()
            scenario.onActivity { activity ->
                activity.supportFragmentManager.fragments.size shouldBe 3
                val started = shadowOf(activity).nextStartedActivity
                started.component?.className shouldBe SudokuActivity::class.java.name
                started.getStringExtra(KEY_SUDOKU_ID) shouldBe generated.id.value
                shadowOf(activity).nextStartedActivity shouldBe null
            }
        }
    }

    @Test
    fun `a new sudoku created while a launch is pending opens once the activity resumes again`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            lateinit var viewModel: TabSudokuViewModel
            scenario.onActivity { activity ->
                activity.singleLaunchActivity(Intent(activity, SudokuLevelActivity::class.java)) shouldBe true
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<TabSudoku>()
                        .first()
                viewModel = ViewModelProvider(fragment)[TabSudokuViewModel::class.java]
                viewModel.onNewGame(SudokuSize.FOUR, Difficulty.VERY_EASY)
            }
            shadowOf(Looper.getMainLooper()).idle()
            val generated = runBlocking { getAllSudokus() }.single()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity.component?.className shouldBe SudokuLevelActivity::class.java.name
                shadowOf(activity).nextStartedActivity shouldBe null
            }
            viewModel.newGame.value shouldBe NewGame.Created(generated.id)

            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity { activity ->
                val started = shadowOf(activity).nextStartedActivity
                started.component?.className shouldBe SudokuActivity::class.java.name
                started.getStringExtra(KEY_SUDOKU_ID) shouldBe generated.id.value
                shadowOf(activity).nextStartedActivity shouldBe null
            }
            viewModel.newGame.value shouldBe NewGame.Idle
        }
    }

    @Test
    fun `the progress bar shows while a new sudoku generates`() =
        launch { fragment ->
            val progressBar = fragment.requireView().findViewById<View>(R.id.newSudokuProgressBar)
            pausableDefaultDispatcher.pause()
            fragment.requireView().findViewById<View>(R.id.newGameButton).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            progressBar.isVisible.shouldBeTrue()
            pausableDefaultDispatcher.resume()
            shadowOf(Looper.getMainLooper()).idle()
            progressBar.isVisible.shouldBeFalse()
        }

    @Test
    fun `dailyButton opens the daily sudoku activity`() =
        launch { fragment ->
            click(fragment.requireView().findViewById(R.id.dailyButton))
            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe DailySudokuActivity::class.java.name
        }

    @Test
    fun `dailyAvailableButton opens the daily sudoku activity`() =
        launch { fragment ->
            click(fragment.requireView().findViewById(R.id.dailyAvailableButton))
            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe DailySudokuActivity::class.java.name
        }

    @Test
    fun `levelsButton opens the sudoku level activity`() =
        launch { fragment ->
            click(fragment.requireView().findViewById(R.id.levelsButton))
            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe SudokuLevelActivity::class.java.name
        }

    @Test
    fun `changing the difficulty seekbar persists the slider value`() =
        launch { fragment ->
            val seekBar =
                fragment.requireView().findViewById<SeslSeekBar>(
                    R.id.difficulty_seekbar,
                )
            seekBar.progress = 3
            shadowOf(Looper.getMainLooper()).idle()
            userSettings.difficultySliderValue shouldBe 3
        }

    @Test
    fun `the size seekbar offers one step per sudoku size`() =
        launch { fragment ->
            fragment.requireView().findViewById<SeslSeekBar>(R.id.size_seekbar).max shouldBe 2
        }

    @Test
    fun `changing the size seekbar persists the slider value`() =
        launch { fragment ->
            val seekBar = fragment.requireView().findViewById<SeslSeekBar>(R.id.size_seekbar)
            seekBar.progress = 2
            shadowOf(Looper.getMainLooper()).idle()
            userSettings.sizeSliderValue shouldBe 2
        }

    @Test
    fun `onResume shows the continue button when a continuable sudoku exists`() {
        runBlocking { saveSudoku(formulaicSudoku()) }
        launch { fragment ->
            fragment
                .requireView()
                .findViewById<View>(R.id.continueGameButton)
                .isVisible
                .shouldBeTrue()
        }
    }

    @Test
    fun `continue button starts the continuable sudoku`() {
        runBlocking { saveSudoku(formulaicSudoku()) }
        launch { fragment ->
            click(fragment.requireView().findViewById(R.id.continueGameButton))
            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe SudokuActivity::class.java.name
        }
    }

    @Test
    fun `draining the main looper before MainActivity starts leaves the continue button to the resumed view`() =
        failOnUncaughtException {
            runBlocking { saveSudoku(formulaicSudoku()) }
            Robolectric.buildActivity(MainActivity::class.java).use { controller ->
                controller.create()
                shadowOf(Looper.getMainLooper()).idle()
                controller.start().resume()
                shadowOf(Looper.getMainLooper()).idle()
                controller
                    .get()
                    .findViewById<View>(R.id.continueGameButton)
                    .isVisible
                    .shouldBeTrue()
            }
        }

    @Test
    fun `showing the sudoku tab before its view exists refreshes it once the view is resumed`() =
        failOnUncaughtException {
            runBlocking { saveSudoku(formulaicSudoku()) }
            Robolectric.buildActivity(MainActivity::class.java).use { controller ->
                controller.create()
                controller.get().onTabItemSelected(0)
                controller.get().onTabItemSelected(1)
                shadowOf(Looper.getMainLooper()).idle()
                controller.start().resume()
                shadowOf(Looper.getMainLooper()).idle()
                controller
                    .get()
                    .findViewById<View>(R.id.continueGameButton)
                    .isVisible
                    .shouldBeTrue()
            }
        }

    @Test
    fun `switching back to the sudoku tab refreshes the continue button`() =
        launch { fragment ->
            val activity = fragment.requireActivity() as MainActivity
            activity.onTabItemSelected(0)
            shadowOf(Looper.getMainLooper()).idle()
            runBlocking { saveSudoku(formulaicSudoku()) }
            activity.onTabItemSelected(1)
            shadowOf(Looper.getMainLooper()).idle()
            fragment
                .requireView()
                .findViewById<View>(R.id.continueGameButton)
                .isVisible
                .shouldBeTrue()
        }

    @Test
    fun `onResume hides the continue button when there is nothing to continue`() =
        launch { fragment ->
            fragment
                .requireView()
                .findViewById<View>(R.id.continueGameButton)
                .isVisible
                .shouldBeFalse()
        }

    @Test
    fun `onResume shows the daily button when today's daily sudoku is already completed`() {
        runBlocking { saveSudoku(formulaicSudoku(modeLevel = MODE_DAILY, allCorrect = true)) }
        launch { fragment ->
            fragment
                .requireView()
                .findViewById<View>(R.id.dailyButton)
                .isVisible
                .shouldBeTrue()
            fragment
                .requireView()
                .findViewById<View>(R.id.dailyAvailableButton)
                .isVisible
                .shouldBeFalse()
        }
    }

    @Test
    fun `onResume shows the daily-available button when today's daily sudoku is unfinished`() {
        runBlocking { saveSudoku(formulaicSudoku(modeLevel = MODE_DAILY, allCorrect = false)) }
        launch { fragment ->
            fragment
                .requireView()
                .findViewById<View>(R.id.dailyButton)
                .isVisible
                .shouldBeFalse()
            fragment
                .requireView()
                .findViewById<View>(R.id.dailyAvailableButton)
                .isVisible
                .shouldBeTrue()
        }
    }

    @Test
    fun `double-clicking newGameButton starts a single game`() =
        launch { fragment ->
            doubleClick(fragment.requireView().findViewById(R.id.newGameButton))
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldNotBeNull()
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)
            runBlocking { getAllSudokus() }.size shouldBe 1
        }

    @Test
    fun `double-clicking dailyButton navigates once`() =
        launch { fragment ->
            doubleClick(fragment.requireView().findViewById(R.id.dailyButton))
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldNotBeNull()
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)
        }

    @Test
    fun `double-clicking dailyAvailableButton navigates once`() =
        launch { fragment ->
            doubleClick(fragment.requireView().findViewById(R.id.dailyAvailableButton))
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldNotBeNull()
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)
        }

    @Test
    fun `double-clicking levelsButton navigates once`() =
        launch { fragment ->
            doubleClick(fragment.requireView().findViewById(R.id.levelsButton))
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldNotBeNull()
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)
        }

    @Test
    fun `double-clicking continueGameButton navigates once`() {
        runBlocking { saveSudoku(formulaicSudoku()) }
        launch { fragment ->
            doubleClick(fragment.requireView().findViewById(R.id.continueGameButton))
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldNotBeNull()
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)
        }
    }

    @Test
    fun `newGameButton with the size seekbar at its minimum creates a 4x4 sudoku`() =
        launch { fragment ->
            fragment.requireView().findViewById<SeslSeekBar>(R.id.size_seekbar).progress = 0
            click(fragment.requireView().findViewById(R.id.newGameButton))
            runBlocking { getAllSudokus() }.single().size shouldBe SudokuSize.FOUR
        }

    @Test
    fun `newGameButton with the size seekbar at its maximum creates a 16x16 sudoku`() =
        launch { fragment ->
            fragment.requireView().findViewById<SeslSeekBar>(R.id.size_seekbar).progress = 2
            click(fragment.requireView().findViewById(R.id.newGameButton))
            runBlocking { getAllSudokus() }.single().size shouldBe SudokuSize.SIXTEEN
        }
}
