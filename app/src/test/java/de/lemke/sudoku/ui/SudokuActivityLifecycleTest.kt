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
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.di.MainDispatcher
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog

/** sdk = 36: Robolectric's max supported SDK. */
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuActivityLifecycleTest {
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
    lateinit var userSettings: UserSettings

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

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
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.userSettings.animationsEnabled = false
                block(activity)
            }
        }
    }

    private fun formulaicSudoku(
        size: Int = 4,
        modeLevel: Int = MODE_NORMAL,
        created: LocalDateTime = LocalDateTime.now(),
        allCorrect: Boolean = false,
        sudokuId: SudokuId = SudokuId.generate(),
    ): Sudoku {
        val blockSize = sqrt(size.toDouble()).toInt()
        return Sudoku.create(
            sudokuId = sudokuId,
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = modeLevel,
            created = created,
            fields =
                MutableList(size * size) { index ->
                    val row = index / size
                    val col = index % size
                    val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size + 1
                    val given = index % 3 == 0
                    Field(
                        position = Position.create(index, size),
                        solution = solution,
                        value = if (given || allCorrect) solution else null,
                        given = given,
                    )
                },
        )
    }

    // region resumeGame

    @Test
    fun `resumeGame starts the timer for a normal in-progress sudoku`() =
        launch(formulaicSudoku()) { activity ->
            activity.sudoku.resumed.shouldBeTrue()
        }

    @Test
    fun `resumeGame does not start the timer for an already completed sudoku`() =
        launch(formulaicSudoku(allCorrect = true)) { activity ->
            activity.sudoku.completed.shouldBeTrue()
            activity.sudoku.resumed.shouldBeFalse()
        }

    @Test
    fun `resumeGame does not start the timer for a stale daily sudoku`() =
        launch(formulaicSudoku(modeLevel = MODE_DAILY, created = LocalDateTime.now().minusDays(2))) { activity ->
            activity.sudoku.resumed.shouldBeFalse()
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
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { it.userSettings.animationsEnabled = false }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity -> activity.sudoku.resumed.shouldBeFalse() }
        }
    }

    @Test
    fun `pausing an already completed sudoku returns early without crashing`() {
        val sudoku = formulaicSudoku(allCorrect = true)
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { it.userSettings.animationsEnabled = false }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity -> activity.sudoku.resumed.shouldBeFalse() }
        }
    }

    @Test
    fun `pausing does not clear the keep-screen-on flag when the setting is disabled`() {
        val sudoku = formulaicSudoku()
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
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

    // region onKeyUp

    private fun keyUp(
        activity: SudokuActivity,
        keyCode: Int,
    ): Boolean = activity.onKeyUp(keyCode, KeyEvent(KeyEvent.ACTION_UP, keyCode))

    @Test
    fun `a digit key enabled for the board size selects the matching number button`() =
        launch(formulaicSudoku()) { activity ->
            keyUp(activity, KeyEvent.KEYCODE_1).shouldBeTrue()
            activity.selected shouldBe activity.sudoku.itemCount
        }

    @Test
    fun `a digit key above the board size is ignored`() =
        launch(formulaicSudoku()) { activity ->
            keyUp(activity, KeyEvent.KEYCODE_5).shouldBeFalse()
            activity.selected.shouldBeNull()
        }

    @Test
    fun `KEYCODE_DEL selects the delete button`() =
        launch(formulaicSudoku()) { activity ->
            keyUp(activity, KeyEvent.KEYCODE_DEL).shouldBeTrue()
            activity.selected shouldBe activity.sudoku.itemCount + activity.sudoku.size
        }

    @Test
    fun `KEYCODE_H selects the hint button while a hint is available and is ignored once exhausted`() =
        launch(formulaicSudoku()) { activity ->
            keyUp(activity, KeyEvent.KEYCODE_H).shouldBeTrue()
            activity.selected shouldBe activity.sudoku.itemCount + activity.sudoku.size + 1

            activity.select(4)
            activity.sudoku.isHintAvailable.shouldBeFalse()

            keyUp(activity, KeyEvent.KEYCODE_H).shouldBeFalse()
        }

    @Test
    fun `KEYCODE_N toggles note mode`() =
        launch(formulaicSudoku()) { activity ->
            activity.notesEnabled.shouldBeFalse()
            keyUp(activity, KeyEvent.KEYCODE_N).shouldBeTrue()
            activity.notesEnabled.shouldBeTrue()
            keyUp(activity, KeyEvent.KEYCODE_N).shouldBeTrue()
            activity.notesEnabled.shouldBeFalse()
        }

    @Test
    fun `KEYCODE_ESCAPE deselects`() =
        launch(formulaicSudoku()) { activity ->
            activity.select(1)
            keyUp(activity, KeyEvent.KEYCODE_ESCAPE).shouldBeTrue()
            activity.selected.shouldBeNull()
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

    // endregion

    // region onOptionsItemSelected

    @Test
    fun `menu_pause_play toggles between pausing and resuming`() =
        launch(formulaicSudoku()) { activity ->
            activity.sudoku.resumed.shouldBeTrue()
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_pause_play)).shouldBeTrue()
            activity.sudoku.resumed.shouldBeFalse()
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_pause_play)).shouldBeTrue()
            activity.sudoku.resumed.shouldBeTrue()
        }

    @Test
    fun `menu_reset restarts the game and clears progress`() =
        launch(formulaicSudoku()) { activity ->
            activity.select(1)
            activity.select(activity.sudoku.itemCount)
            activity.sudoku.errorsMade shouldBe 1

            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_reset)).shouldBeTrue()

            activity.sudoku.errorsMade shouldBe 0
            activity.sudoku[1].value.shouldBeNull()
        }

    @Test
    fun `menu_share shows the share dialog and pauses the game`() =
        launch(formulaicSudoku()) { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_share)).shouldBeTrue()
            activity.sudoku.resumed.shouldBeFalse()
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
            activity.select(fieldIndex)
            activity.select(activity.sudoku.itemCount)
        }
    }

    @Test
    fun `checkErrorLimit uses the fixed level limit and guards further input once reached`() =
        launch(formulaicSudoku(modeLevel = 1)) { activity ->
            placeThreeWrongMoves(activity)
            activity.sudoku.errorsMade shouldBe 3

            activity.select(activity.sudoku.itemCount + 1)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `checkErrorLimit uses the fixed daily limit and guards further input once reached`() =
        launch(formulaicSudoku(modeLevel = MODE_DAILY)) { activity ->
            placeThreeWrongMoves(activity)
            activity.sudoku.errorsMade shouldBe 3

            activity.select(activity.sudoku.itemCount + 1)
            activity.selected.shouldBeNull()
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
    fun `onPrepareOptionsMenu and resumeGame are no-ops before the sudoku ever initializes`() {
        val activity = Robolectric.buildActivity(SudokuActivity::class.java).get()

        activity.onPrepareOptionsMenu(null).shouldBeFalse()
        activity.resumeGame()
    }

    @Test
    fun `pausing an activity whose sudoku never initialized is a no-op`() {
        Robolectric.buildActivity(SudokuActivity::class.java).create().pause()
    }

    // endregion

    // region SudokuGameListener glue

    @Test
    fun `onFieldClicked routes through select the same way a tap on the board would`() =
        launch(formulaicSudoku()) { activity ->
            activity.sudoku.gameListener?.onFieldClicked(Position.create(1, activity.sudoku.size))
            activity.selected shouldBe 1
        }

    @Test
    fun `onTimeChanged refreshes the subtitle`() =
        launch(formulaicSudoku()) { activity ->
            activity.sudoku.seconds = 65
            activity.sudoku.gameListener?.onTimeChanged()
            shadowOf(Looper.getMainLooper()).idle()
            activity.binding.sudokuToolbarLayout.expandedSubtitle
                .toString() shouldBe
                activity.binding.sudokuToolbarLayout.collapsedSubtitle
                    .toString()
            activity.binding.sudokuToolbarLayout.expandedSubtitle
                .toString()
                .contains("01:05")
                .shouldBeTrue()
        }

    // endregion
}
