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

import android.content.Intent
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.game.Selection
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenu
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuActivityLifecycleTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var userSettings: UserSettings

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Inject
    lateinit var sudokusRepository: SudokusRepository

    @Before
    fun setup() {
        hiltRule.inject()
        userSettings.bypassOobe()
    }

    private fun launch(
        sudoku: Sudoku,
        block: (SudokuActivity) -> Unit,
    ) {
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.info.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.userSettings.animationsEnabled = false
                block(activity)
            }
        }
    }

    private fun formulaicSudoku(
        size: SudokuSize = SudokuSize.FOUR,
        mode: GameMode = GameMode.Normal,
        created: LocalDateTime = LocalDateTime.now(),
        allCorrect: Boolean = false,
        sudokuId: SudokuId = SudokuId.generate(),
    ): Sudoku =
        testSudoku(
            size = size,
            mode = mode,
            difficulty = Difficulty.VERY_EASY,
            sudokuId = sudokuId,
            created = created,
            completed = allCorrect,
        )

    // region resumeGame

    @Test
    fun `resumeGame starts the timer for a normal in-progress sudoku`() =
        launch(formulaicSudoku()) { activity ->
            activity.playing.running.shouldBeTrue()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            activity.sudoku.info.seconds shouldBe 3
        }

    @Test
    fun `resumeGame does not start the timer for an already completed sudoku`() =
        launch(formulaicSudoku(allCorrect = true)) { activity ->
            activity.sudoku.completed.shouldBeTrue()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            activity.sudoku.info.seconds shouldBe 0
        }

    @Test
    fun `resumeGame does not start the timer for a stale daily sudoku`() =
        launch(
            formulaicSudoku(mode = GameMode.Daily(LocalDate.now().minusDays(2)), created = LocalDateTime.now().minusDays(2)),
        ) { activity ->
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            activity.sudoku.info.seconds shouldBe 0
        }

    @Test
    fun `resumeGame does not add the keep-screen-on flag when the setting is disabled`() =
        launch(formulaicSudoku()) { activity ->
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.userSettings.keepScreenOn = false

            activity.resumeGame()

            (activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) shouldBe 0
        }

    // endregion

    // region pauseGame

    @Test
    fun `pausing the activity stops the timer`() {
        val sudoku = formulaicSudoku()
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.info.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            var secondsAtPause = 0
            scenario.onActivity {
                it.userSettings.animationsEnabled = false
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
                secondsAtPause = it.sudoku.info.seconds
            }
            secondsAtPause shouldBe 3
            scenario.moveToState(Lifecycle.State.CREATED)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            scenario.onActivity { activity ->
                activity.playing.running.shouldBeFalse()
                activity.sudoku.info.seconds shouldBe secondsAtPause
            }
        }
    }

    @Test
    fun `pausing an already completed sudoku returns early without crashing`() {
        val sudoku = formulaicSudoku(allCorrect = true)
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.info.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { it.userSettings.animationsEnabled = false }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity -> activity.playing.running.shouldBeFalse() }
        }
    }

    private fun launchScenario(
        sudoku: Sudoku,
        block: (ActivityScenario<SudokuActivity>) -> Unit,
    ) {
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.info.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { it.userSettings.animationsEnabled = false }
            block(scenario)
        }
    }

    @Test
    fun `rotating a running game keeps the timer running and the board visible`() =
        launchScenario(formulaicSudoku()) { scenario ->
            scenario.recreate()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))

            scenario.onActivity { activity ->
                activity.playing.running.shouldBeTrue()
                activity.binding.board.isVisible.shouldBeTrue()
                activity.sudoku.info.seconds shouldBe 3
            }
        }

    @Test
    fun `rotating an explicitly paused game keeps it paused until Resume`() =
        launchScenario(formulaicSudoku()) { scenario ->
            scenario.onActivity { it.onOptionsItemSelected(RoboMenuItem(R.id.menu_pause_play)) }
            scenario.recreate()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))

            scenario.onActivity { activity ->
                activity.playing.running.shouldBeFalse()
                activity.binding.board.isVisible.shouldBeFalse()
                activity.binding.resumeButton.isVisible.shouldBeTrue()
                activity.sudoku.info.seconds shouldBe 0

                activity.binding.resumeButton.performClick()
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

            scenario.onActivity { activity ->
                activity.playing.running.shouldBeTrue()
                activity.binding.board.isVisible.shouldBeTrue()
            }
        }

    @Test
    fun `recreating a finished game that stopped running shows the board`() =
        launchScenario(formulaicSudoku(allCorrect = true)) { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { it.playing.running.shouldBeFalse() }

            scenario.recreate()

            scenario.onActivity { activity ->
                activity.binding.board.isVisible.shouldBeTrue()
                activity.binding.resumeButton.isVisible.shouldBeFalse()
            }
        }

    @Test
    fun `pausing does not clear the keep-screen-on flag when the setting is disabled`() {
        val sudoku = formulaicSudoku()
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.info.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity {
                it.userSettings.animationsEnabled = false
                it.userSettings.keepScreenOn = false
                it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity ->
                (
                    activity.window.attributes.flags and
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                ) shouldNotBe 0
            }
        }
    }

    // endregion

    // region saving

    @Test
    fun `placing a value saves it`() =
        launch(formulaicSudoku()) { activity ->
            activity.tapCell(1)
            activity.tapDigit(1)

            runBlocking { sudokusRepository.getSudokuById(activity.sudoku.info.id) }.shouldNotBeNull().board[1].value shouldBe 1
        }

    @Test
    fun `pausing the activity saves the elapsed time`() {
        val sudoku = formulaicSudoku()
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.info.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            var secondsAtPause = 0
            scenario.onActivity {
                it.userSettings.animationsEnabled = false
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
                secondsAtPause = it.sudoku.info.seconds
            }

            scenario.moveToState(Lifecycle.State.CREATED)

            secondsAtPause shouldBeGreaterThan 0
            runBlocking { sudokusRepository.getSudokuById(sudoku.info.id) }.shouldNotBeNull().info.seconds shouldBe secondsAtPause
        }
    }

    // endregion

    // region onKeyUp

    private fun keyUp(
        activity: SudokuActivity,
        keyCode: Int,
    ): Boolean = activity.onKeyUp(keyCode, KeyEvent(KeyEvent.ACTION_UP, keyCode))

    private fun pressKey(
        activity: SudokuActivity,
        keyCode: Int,
    ) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode)).shouldBeFalse()
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode)).shouldBeTrue()
    }

    @Test
    fun `a key press enters a digit into the selected cell`() =
        launch(formulaicSudoku()) { activity ->
            activity.tapCell(1)
            activity.binding.board
                .requestFocusFromTouch()
                .shouldBeTrue()

            pressKey(activity, KeyEvent.KEYCODE_2)

            activity.sudoku.board[1].value shouldBe 2
        }

    @Test
    fun `an arrow key on the focused board neither selects a cell nor is consumed`() =
        launch(formulaicSudoku()) { activity ->
            activity.tapCell(1)
            activity.binding.board
                .requestFocusFromTouch()
                .shouldBeTrue()
            val before = activity.sudoku.board

            listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER).forEach { keyCode ->
                activity.binding.board
                    .dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    .shouldBeFalse()
                activity.binding.board
                    .dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                    .shouldBeFalse()
            }

            activity.selection shouldBe Selection.Cell(1)
            activity.sudoku.board shouldBe before
            listOf(View.FOCUS_DOWN, View.FOCUS_RIGHT).forEach { direction ->
                val next = checkNotNull(activity.binding.board.focusSearch(direction))
                next shouldNotBe activity.binding.board
                next.rootView shouldBe activity.window.decorView.rootView
            }
        }

    @Test
    fun `a digit key enabled for the board size selects the matching number button`() =
        launch(formulaicSudoku()) { activity ->
            keyUp(activity, KeyEvent.KEYCODE_1).shouldBeTrue()
            activity.selection shouldBe Selection.Digit(1)
        }

    @Test
    fun `a digit key above the board size is ignored`() =
        launch(formulaicSudoku()) { activity ->
            keyUp(activity, KeyEvent.KEYCODE_5).shouldBeFalse()
            activity.selection.shouldBeNull()
        }

    @Test
    fun `KEYCODE_9 selects the last number button on a 9x9 board`() =
        launch(formulaicSudoku(size = SudokuSize.NINE)) { activity ->
            keyUp(activity, KeyEvent.KEYCODE_9).shouldBeTrue()
            activity.selection shouldBe Selection.Digit(9)
        }

    @Test
    fun `KEYCODE_A is ignored on a 9x9 board`() =
        launch(formulaicSudoku(size = SudokuSize.NINE)) { activity ->
            keyUp(activity, KeyEvent.KEYCODE_A).shouldBeFalse()
            activity.selection.shouldBeNull()
        }

    @Test
    fun `KEYCODE_G selects the last number button on a 16x16 board`() =
        launch(formulaicSudoku(size = SudokuSize.SIXTEEN)) { activity ->
            keyUp(activity, KeyEvent.KEYCODE_G).shouldBeTrue()
            activity.selection shouldBe Selection.Digit(16)
        }

    @Test
    fun `KEYCODE_DEL selects the delete button`() =
        launch(formulaicSudoku()) { activity ->
            keyUp(activity, KeyEvent.KEYCODE_DEL).shouldBeTrue()
            activity.selection shouldBe Selection.Delete
        }

    @Test
    fun `KEYCODE_H selects the hint button while a hint is available and is ignored once exhausted`() =
        launch(formulaicSudoku()) { activity ->
            keyUp(activity, KeyEvent.KEYCODE_H).shouldBeTrue()
            activity.selection shouldBe Selection.Hint

            activity.tapCell(4)
            activity.sudoku.info.isHintAvailable
                .shouldBeFalse()

            keyUp(activity, KeyEvent.KEYCODE_H).shouldBeFalse()
        }

    @Test
    fun `KEYCODE_N toggles note mode`() =
        launch(formulaicSudoku()) { activity ->
            activity.viewModel.input.value.notesMode
                .shouldBeFalse()
            keyUp(activity, KeyEvent.KEYCODE_N).shouldBeTrue()
            activity.viewModel.input.value.notesMode
                .shouldBeTrue()
            keyUp(activity, KeyEvent.KEYCODE_N).shouldBeTrue()
            activity.viewModel.input.value.notesMode
                .shouldBeFalse()
        }

    @Test
    fun `KEYCODE_ESCAPE deselects`() =
        launch(formulaicSudoku()) { activity ->
            activity.tapCell(1)
            keyUp(activity, KeyEvent.KEYCODE_ESCAPE).shouldBeTrue()
            activity.selection.shouldBeNull()
        }

    @Test
    fun `an unhandled key code falls through to the default behavior`() =
        launch(formulaicSudoku()) { activity ->
            keyUp(activity, KeyEvent.KEYCODE_VOLUME_UP).shouldBeFalse()
        }

    // endregion

    // region onPrepareOptionsMenu

    @Test
    fun `onPrepareOptionsMenu with a null menu is a safe no-op once the sudoku is initialized`() =
        launch(formulaicSudoku()) { activity ->
            activity.onPrepareOptionsMenu(null).shouldBeTrue()
        }

    @Test
    fun `onPrepareOptionsMenu offers share while a sudoku is playing`() =
        launch(formulaicSudoku()) { activity ->
            val menu = RoboMenu(activity)
            val share = menu.add(0, R.id.menu_share, 0, "Share").apply { isVisible = false }

            activity.onPrepareOptionsMenu(menu).shouldBeTrue()

            share.isVisible.shouldBeTrue()
        }

    // endregion

    // region onOptionsItemSelected

    @Test
    fun `menu_pause_play toggles between pausing and resuming`() =
        launch(formulaicSudoku()) { activity ->
            activity.playing.running.shouldBeTrue()
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_pause_play)).shouldBeTrue()
            activity.playing.running.shouldBeFalse()
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_pause_play)).shouldBeTrue()
            activity.playing.running.shouldBeTrue()
        }

    @Test
    fun `menu_reset restarts the game and clears progress`() =
        launch(formulaicSudoku()) { activity ->
            activity.userSettings.errorLimit = 1
            activity.tapCell(1)
            activity.tapDigit(1)
            activity.sudoku.info.errorsMade shouldBe 1

            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_reset)).shouldBeTrue()
            idleMainLooper()

            activity.sudoku.info.errorsMade shouldBe 0
            activity.sudoku.board[1]
                .value
                .shouldBeNull()
        }

    @Test
    fun `menu_share shows the share dialog and pauses the game`() =
        launch(formulaicSudoku()) { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_share)).shouldBeTrue()
            activity.playing.running.shouldBeFalse()
            val dialog = ShadowDialog.getLatestDialog()
            dialog.shouldNotBeNull()
            dialog!!.isShowing.shouldBeTrue()
        }

    @Test
    fun `an unknown menu item falls through to the default behavior`() =
        launch(formulaicSudoku()) { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(-12345)).shouldBeFalse()
        }

    // endregion

    private fun placeThreeWrongMoves(activity: SudokuActivity) {
        listOf(1, 2, 4).forEach { fieldIndex ->
            activity.tapCell(fieldIndex)
            activity.tapDigit(1)
        }
    }

    @Test
    fun `checkErrorLimit uses the fixed level limit and guards further input once reached`() =
        launch(formulaicSudoku(mode = GameMode.Level(1))) { activity ->
            placeThreeWrongMoves(activity)
            activity.sudoku.info.errorsMade shouldBe 3

            activity.tapDigit(2)
            activity.selection.shouldBeNull()
        }

    @Test
    fun `checkErrorLimit uses the fixed daily limit and guards further input once reached`() =
        launch(formulaicSudoku(mode = GameMode.Daily(LocalDate.now()))) { activity ->
            placeThreeWrongMoves(activity)
            activity.sudoku.info.errorsMade shouldBe 3

            activity.tapDigit(2)
            activity.selection.shouldBeNull()
        }

    // region not-found / not-yet-initialized

    @Test
    fun `missing the sudoku id extra finishes and destroys the activity`() {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.state shouldBe Lifecycle.State.DESTROYED
        }
    }

    @Test
    fun `a sudoku id that does not exist finishes and destroys the activity`() {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, "does-not-exist")
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.state shouldBe Lifecycle.State.DESTROYED
        }
    }

    @Test
    fun `resumeGame is a no-op before the sudoku ever initializes`() {
        val activity = Robolectric.buildActivity(SudokuActivity::class.java).get()

        activity.resumeGame()
    }

    @Test
    fun `pausing an activity whose sudoku never initialized is a no-op`() {
        Robolectric.buildActivity(SudokuActivity::class.java).create().pause()
    }

    // endregion

    // region board callbacks

    @Test
    fun `a click on a field routes through select the same way a tap on the board would`() =
        launch(formulaicSudoku()) { activity ->
            activity.tapCell(1)
            activity.selection shouldBe Selection.Cell(1)
        }

    @Test
    fun `a long click on a player field erases it`() =
        launch(formulaicSudoku()) { activity ->
            activity.tapCell(1)
            activity.tapDigit(2)
            activity.sudoku.board[1].value shouldBe 2

            activity.longPressCell(1)

            activity.sudoku.board[1]
                .value
                .shouldBeNull()
            activity.sudoku.info.eraserUsed
                .shouldBeTrue()
        }

    @Test
    fun `a long click on a given field changes nothing`() =
        launch(formulaicSudoku()) { activity ->
            activity.longPressCell(0)

            activity.sudoku.board[0].origin shouldBe CellOrigin.GIVEN

            activity.sudoku.info.eraserUsed
                .shouldBeFalse()
        }

    @Test
    fun `the subtitle shows the elapsed time`() =
        launch(formulaicSudoku()) { activity ->
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(65))
            idleMainLooper()
            activity.sudoku.info.seconds shouldBe 65

            val subtitle =
                activity.binding.sudokuToolbarLayout.expandedSubtitle
                    .toString()
            subtitle shouldBe
                activity.binding.sudokuToolbarLayout.collapsedSubtitle
                    .toString()
            subtitle shouldBe "01:05 | 0% | Errors: 0/3 | Hints: 0"
        }

    // endregion
}
