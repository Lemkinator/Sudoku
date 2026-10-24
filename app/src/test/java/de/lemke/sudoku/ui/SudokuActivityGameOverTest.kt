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
import android.view.MenuItem
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.AdjustableClock
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.di.ClockModule
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY
import de.lemke.sudoku.testSudoku
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenu
import org.robolectric.shadows.ShadowDialog

@UninstallModules(ClockModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuActivityGameOverTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    private val adjustableClock = AdjustableClock(Instant.parse("2026-01-15T12:00:00Z"))

    @BindValue
    @JvmField
    val testClock: Clock = adjustableClock

    @Inject
    lateinit var userSettings: UserSettings

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Inject
    lateinit var sudokusRepository: SudokusRepository

    private val today = LocalDateTime.of(2026, 1, 15, 0, 0)

    @Before
    fun setup() {
        hiltRule.inject()
        userSettings.bypassOobe()
    }

    private fun launchScenario(
        sudoku: Sudoku,
        block: (ActivityScenario<SudokuActivity>) -> Unit,
    ) {
        runBlocking { saveSudoku(sudoku) }
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { it.userSettings.animationsEnabled = false }
            block(scenario)
        }
    }

    private fun launch(
        sudoku: Sudoku,
        block: (SudokuActivity) -> Unit,
    ) = launchScenario(sudoku) { scenario -> scenario.onActivity(block) }

    private fun SudokuActivity.keepsScreenOn() = window.attributes.flags and FLAG_KEEP_SCREEN_ON != 0

    private fun SudokuActivity.menuItems(): Pair<MenuItem, MenuItem> {
        val menu = RoboMenu(this)
        val pause = menu.add(0, R.id.menu_pause_play, 0, "Pause")
        val reset = menu.add(0, R.id.menu_reset, 0, "Restart")
        onPrepareOptionsMenu(menu)
        return pause to reset
    }

    private fun almostLost(
        modeLevel: Int,
        created: LocalDateTime = today,
    ) = testSudoku(modeLevel = modeLevel, created = created).apply { errorsMade = 2 }

    private fun SudokuActivity.makeAnError() {
        select(1)
        select(sudoku.itemCount)
    }

    private fun restartButtonShown(): Boolean =
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isShown

    @Test
    fun `the game-over dialog of a lost level offers Restart`() =
        launch(almostLost(modeLevel = 2)) { activity ->
            activity.makeAnError()

            restartButtonShown() shouldBe true
        }

    @Test
    fun `the game-over dialog of a lost daily sudoku of today offers Restart`() =
        launch(almostLost(MODE_DAILY)) { activity ->
            activity.makeAnError()

            restartButtonShown() shouldBe true
        }

    @Test
    fun `the game-over dialog of a lost normal sudoku offers Restart`() =
        launch(almostLost(modeLevel = 0)) { activity ->
            activity.makeAnError()

            restartButtonShown() shouldBe true
        }

    @Test
    fun `the Restart button of a lost level restarts it`() =
        launch(almostLost(modeLevel = 2)) { activity ->
            activity.makeAnError()

            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idleMainLooper()

            activity.sudoku.errorsMade shouldBe 0
        }

    @Test
    fun `a second error callback while the game-over dialog shows keeps the one dialog`() =
        launch(almostLost(modeLevel = 2)) { activity ->
            activity.makeAnError()

            activity.sudoku.gameListener?.onError()

            ShadowDialog
                .getShownDialogs()
                .filterIsInstance<AlertDialog>()
                .filter { it.isShowing }
                .size shouldBe 1
        }

    @Test
    fun `a tap on the board of a lost game selects nothing`() =
        launch(almostLost(modeLevel = 2)) { activity ->
            activity.makeAnError()

            activity.select(2)

            activity.selected.shouldBeNull()
        }

    @Test
    fun `pausing a lost game shows no overlay and saves nothing beyond the error`() =
        launchScenario(almostLost(modeLevel = 0)) { scenario ->
            scenario.onActivity { activity ->
                activity.makeAnError()
                activity.sudoku.seconds = 99
            }

            scenario.moveToState(Lifecycle.State.CREATED)

            scenario.onActivity { activity ->
                activity.binding.resumeButton.isVisible shouldBe false
                activity.binding.gameLayout.isVisible shouldBe true
                val saved = runBlocking { sudokusRepository.getSudokuById(activity.sudoku.id) }.shouldNotBeNull()
                saved.errorsMade shouldBe 3
                saved.seconds shouldBe 0
            }
        }

    @Test
    fun `pausing a game in progress saves its time and shows the overlay`() =
        launchScenario(testSudoku()) { scenario ->
            scenario.onActivity { it.sudoku.seconds = 99 }

            scenario.moveToState(Lifecycle.State.CREATED)

            scenario.onActivity { activity ->
                activity.binding.resumeButton.isVisible shouldBe true
                runBlocking { sudokusRepository.getSudokuById(activity.sudoku.id) }.shouldNotBeNull().seconds shouldBe 99
            }
        }

    @Test
    fun `a game in progress keeps the screen on and pausing clears it`() =
        launchScenario(testSudoku()) { scenario ->
            scenario.onActivity { it.keepsScreenOn() shouldBe true }

            scenario.moveToState(Lifecycle.State.CREATED)

            scenario.onActivity { it.keepsScreenOn() shouldBe false }
        }

    @Test
    fun `losing a game clears keep-screen-on`() =
        launch(almostLost(modeLevel = 0)) { activity ->
            activity.keepsScreenOn() shouldBe true

            activity.makeAnError()

            activity.keepsScreenOn() shouldBe false
        }

    @Test
    fun `a completed game never keeps the screen on`() =
        launch(testSudoku(completed = true)) { activity ->
            activity.keepsScreenOn() shouldBe false
        }

    @Test
    fun `resuming an already lost game shows no game-over dialog`() =
        launch(testSudoku().apply { errorsMade = 3 }) { activity ->
            activity.resumeGame()

            ShadowDialog.getLatestDialog().shouldBeNull()
            activity.keepsScreenOn() shouldBe false
        }

    @Test
    fun `a lost game offers Restart but no Pause in the menu`() =
        launch(testSudoku().apply { errorsMade = 3 }) { activity ->
            val (pause, reset) = activity.menuItems()

            pause.isVisible shouldBe false
            reset.isVisible shouldBe true
        }

    @Test
    fun `a past daily sudoku offers neither Pause nor Restart`() =
        launch(testSudoku(modeLevel = MODE_DAILY, created = today.minusDays(1)).apply { errorsMade = 3 }) { activity ->
            val (pause, reset) = activity.menuItems()

            pause.isVisible shouldBe false
            reset.isVisible shouldBe false
        }

    @Test
    fun `a game in progress offers Pause but no Restart`() =
        launch(testSudoku()) { activity ->
            val (pause, reset) = activity.menuItems()

            pause.isVisible shouldBe true
            reset.isVisible shouldBe false
        }

    @Test
    fun `a won level offers no Restart`() =
        launch(testSudoku(modeLevel = 2, completed = true)) { activity ->
            activity.menuItems().second.isVisible shouldBe false
        }

    @Test
    fun `a won normal sudoku offers Restart`() =
        launch(testSudoku(completed = true)) { activity ->
            activity.menuItems().second.isVisible shouldBe true
        }

    @Test
    fun `a lost level offers Restart in the menu`() =
        launch(testSudoku(modeLevel = 2).apply { errorsMade = 3 }) { activity ->
            activity.menuItems().second.isVisible shouldBe true
        }

    @Test
    fun `a lost daily sudoku of today offers Restart in the menu`() =
        launch(testSudoku(modeLevel = MODE_DAILY, created = today).apply { errorsMade = 3 }) { activity ->
            activity.menuItems().second.isVisible shouldBe true
        }

    @Test
    fun `a won daily sudoku of today offers no Restart`() =
        launch(testSudoku(modeLevel = MODE_DAILY, created = today, completed = true)) { activity ->
            activity.menuItems().second.isVisible shouldBe false
        }

    @Test
    fun `a won past daily sudoku offers no Restart`() =
        launch(testSudoku(modeLevel = MODE_DAILY, created = today.minusDays(1), completed = true)) { activity ->
            activity.menuItems().second.isVisible shouldBe false
        }
}
