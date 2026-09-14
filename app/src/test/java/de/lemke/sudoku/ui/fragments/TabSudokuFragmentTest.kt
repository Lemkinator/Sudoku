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
import android.os.SystemClock
import android.view.View
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.view.isVisible
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
import de.lemke.sudoku.R
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.ui.DailySudokuActivity
import de.lemke.sudoku.ui.MainActivity
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuLevelActivity
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Covers [TabSudoku] through [MainActivity] (its default tab): the new-game/daily/levels click handlers, the
 * difficulty/size seekbar listeners, and `onResume`'s continuable-sudoku and daily-completed branches.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class TabSudokuFragmentTest {
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

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun formulaicSudoku(
        size: Int = 4,
        modeLevel: Int = MODE_NORMAL,
        allCorrect: Boolean = false,
    ): Sudoku {
        val blockSize = sqrt(size.toDouble()).toInt()
        return Sudoku.create(
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = modeLevel,
            fields =
                MutableList(size * size) { index ->
                    val row = index / size
                    val col = index % size
                    val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size + 1
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

    private fun click(view: View) {
        SystemClock.sleep(601L)
        view.performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Two clicks with no gap in between: the second falls inside `onSingleClick`'s debounce window and is dropped. */
    private fun doubleClick(view: View) {
        SystemClock.sleep(601L)
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
            seekBar.progress shouldBe 3
        }

    @Test
    fun `changing the size seekbar persists the slider value`() =
        launch { fragment ->
            val seekBar = fragment.requireView().findViewById<SeslSeekBar>(R.id.size_seekbar)
            seekBar.progress = 2
            shadowOf(Looper.getMainLooper()).idle()
            seekBar.progress shouldBe 2
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
    fun `double-clicking newGameButton is debounced to a single game`() =
        launch { fragment ->
            doubleClick(fragment.requireView().findViewById(R.id.newGameButton))
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldNotBeNull()
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)
            runBlocking { getAllSudokus() }.size shouldBe 1
        }

    @Test
    fun `double-clicking dailyButton is debounced to a single navigation`() =
        launch { fragment ->
            doubleClick(fragment.requireView().findViewById(R.id.dailyButton))
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldNotBeNull()
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)
        }

    @Test
    fun `double-clicking dailyAvailableButton is debounced to a single navigation`() =
        launch { fragment ->
            doubleClick(fragment.requireView().findViewById(R.id.dailyAvailableButton))
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldNotBeNull()
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)
        }

    @Test
    fun `double-clicking levelsButton is debounced to a single navigation`() =
        launch { fragment ->
            doubleClick(fragment.requireView().findViewById(R.id.levelsButton))
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldNotBeNull()
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBe(null)
        }

    @Test
    fun `double-clicking continueGameButton is debounced to a single navigation`() {
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
            runBlocking { getAllSudokus() }.single().size shouldBe Sudoku.SIZE_4X4
        }

    @Test
    fun `newGameButton with the size seekbar at its maximum creates a 16x16 sudoku`() =
        launch { fragment ->
            fragment.requireView().findViewById<SeslSeekBar>(R.id.size_seekbar).progress = 2
            click(fragment.requireView().findViewById(R.id.newGameButton))
            runBlocking { getAllSudokus() }.single().size shouldBe Sudoku.SIZE_16X16
        }
}
