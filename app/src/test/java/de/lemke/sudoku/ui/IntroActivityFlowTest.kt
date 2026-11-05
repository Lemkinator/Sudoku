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

import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.ui.game.BoardInput
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class IntroActivityFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var userSettings: UserSettings

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        userSettings.animationsEnabled = false
    }

    private fun launch(
        openedFromSettings: Boolean = false,
        block: (IntroActivity) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent =
            Intent(context, IntroActivity::class.java)
                .putExtra(IntroActivity.KEY_OPENED_FROM_SETTINGS, openedFromSettings)
        ActivityScenario.launch<IntroActivity>(intent).use { scenario ->
            scenario.onActivity { activity -> block(activity) }
        }
    }

    @Config(application = HiltTestApplication::class, sdk = [33])
    @Test
    fun `onCreate does not override the activity transition below API 34`() =
        launch { activity ->
            activity.viewModel.state.value.step shouldBe IntroStep.BLOCKS
        }

    private fun IntroActivity.tapCell(index: Int) = checkNotNull(binding.board.onCellClick)(index)

    private fun IntroActivity.longPressCell(index: Int) = checkNotNull(binding.board.onCellLongClick)(index)

    private fun IntroActivity.tapDigit(digit: Int) =
        with(binding) {
            listOf(
                numberButton1,
                numberButton2,
                numberButton3,
                numberButton4,
                numberButton5,
                numberButton6,
                numberButton7,
                numberButton8,
                numberButton9,
            )
        }[digit - 1].performClick()

    private val IntroActivity.state get() = viewModel.state.value

    private fun IntroActivity.advanceTo(target: IntroStep) {
        while (state.step != target) {
            when (state.step) {
                IntroStep.SELECT_CELL -> tapCell(DEMO_CELL_INDEX_4)

                IntroStep.ENTER_NUMBER -> tapDigit(DEMO_ENTER_DIGIT)

                IntroStep.SELECT_NUMBER -> tapDigit(DEMO_SELECT_DIGIT)

                IntroStep.ENTER_ROW_COLUMN -> tapCell(DEMO_CELL_INDEX_49)

                IntroStep.ENTER_BLOCK -> tapCell(DEMO_CELL_INDEX_24)

                IntroStep.BLOCKS,
                IntroStep.SELECTION,
                IntroStep.NUMBER_ENTRY,
                IntroStep.NUMBER_ENTRY_DEMO,
                IntroStep.TOOLS,
                IntroStep.NOTES,
                -> binding.introNextButton.performClick()
            }
        }
    }

    @Test
    fun `the tutorial starts with the rules`() =
        launch { activity ->
            activity.state.step shouldBe IntroStep.BLOCKS
            activity.binding.introTitleText.text
                .toString() shouldBe "Rules"
            activity.binding.introTextText.text
                .toString() shouldBe
                "A Sudoku grid consists of 4×4, 9×9 or 16×16 squares. Depending on the size, only the numbers from 1 to 4, " +
                "9 or 16 can be used. No numbers may be missing or duplicated in each block, row or column."
            activity.binding.gameButtons.isVisible
                .shouldBeFalse()
        }

    @Test
    fun `a tap on an unrelated cell in the first lesson changes nothing`() =
        launch { activity ->
            activity.tapCell(0)

            activity.state shouldBe IntroState()
        }

    @Test
    fun `the next button moves through the text lessons and hides the title for the first task`() =
        launch { activity ->
            activity.binding.introNextButton.performClick()
            activity.state.step shouldBe IntroStep.SELECTION
            activity.binding.introTextText.text
                .toString() shouldBe
                "The game is finished when all numbers have been correctly entered into the Sudoku grid. \nThe amount of given " +
                "numbers depends on the difficulty of the Sudoku. \nFor more difficult Sudokus, fewer numbers are given and often " +
                "more complex strategies must be used."

            activity.binding.introNextButton.performClick()

            activity.state.step shouldBe IntroStep.SELECT_CELL
            activity.binding.introTitle.isVisible
                .shouldBeFalse()
            activity.binding.introTextText.text
                .toString() shouldBe
                "In the first row the last number missing is 5. Tap on the empty field to select it."
        }

    @Test
    fun `tapping the highlighted cell selects it, shows the number buttons and moves on`() =
        launch { activity ->
            activity.advanceTo(IntroStep.SELECT_CELL)

            activity.tapCell(DEMO_CELL_INDEX_4)

            activity.state.step shouldBe IntroStep.ENTER_NUMBER
            activity.binding.board.render
                ?.selected shouldBe DEMO_CELL_INDEX_4
            activity.binding.gameButtons.isVisible
                .shouldBeTrue()
            activity.binding.numberButtons.isVisible
                .shouldBeTrue()
            activity.binding.otherButtons.isVisible
                .shouldBeFalse()
        }

    @Test
    fun `tapping another cell in the select-cell lesson selects nothing`() =
        launch { activity ->
            activity.advanceTo(IntroStep.SELECT_CELL)

            activity.tapCell(0)

            activity.state.step shouldBe IntroStep.SELECT_CELL
            activity.state.input shouldBe BoardInput()
        }

    @Test
    fun `entering 5 into the selected cell places it and moves on`() =
        launch { activity ->
            activity.advanceTo(IntroStep.ENTER_NUMBER)

            activity.tapDigit(DEMO_ENTER_DIGIT)

            activity.state.step shouldBe IntroStep.SELECT_NUMBER
            activity.binding.board.render
                ?.board
                ?.get(DEMO_CELL_INDEX_4)
                ?.value shouldBe DEMO_ENTER_DIGIT
            activity.binding.board.render
                ?.selected
                .shouldBeNull()
        }

    @Test
    fun `tapping another number in the enter-number lesson places nothing`() =
        launch { activity ->
            activity.advanceTo(IntroStep.ENTER_NUMBER)

            activity.tapDigit(1)

            activity.state.step shouldBe IntroStep.ENTER_NUMBER
            activity.binding.board.render
                ?.board
                ?.get(DEMO_CELL_INDEX_4)
                ?.value
                .shouldBeNull()
        }

    @Test
    fun `selecting number 2 shows it selected and moves on`() =
        launch { activity ->
            activity.advanceTo(IntroStep.SELECT_NUMBER)

            activity.tapDigit(DEMO_SELECT_DIGIT)

            activity.state.step shouldBe IntroStep.ENTER_ROW_COLUMN
            activity.binding.numberButton2.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary
            activity.binding.board.render
                ?.highlightedDigit shouldBe DEMO_SELECT_DIGIT
        }

    @Test
    fun `entering the selected number into the two highlighted cells finishes the tasks`() =
        launch { activity ->
            activity.advanceTo(IntroStep.ENTER_ROW_COLUMN)

            activity.tapCell(DEMO_CELL_INDEX_49)
            activity.state.step shouldBe IntroStep.ENTER_BLOCK
            activity.tapCell(DEMO_CELL_INDEX_24)

            activity.state.step shouldBe IntroStep.NUMBER_ENTRY
            val board = checkNotNull(activity.binding.board.render).board
            listOf(board[DEMO_CELL_INDEX_49].value, board[DEMO_CELL_INDEX_24].value) shouldBe listOf(DEMO_SELECT_DIGIT, DEMO_SELECT_DIGIT)
        }

    @Test
    fun `the delete and hint buttons select nothing during the tutorial`() =
        launch { activity ->
            activity.advanceTo(IntroStep.SELECT_NUMBER)
            val before = activity.state

            activity.binding.deleteButton.performClick()
            activity.binding.hintButton.performClick()

            activity.state shouldBe before
        }

    @Test
    fun `the tools lesson shows the tools and hides the numbers`() =
        launch { activity ->
            activity.advanceTo(IntroStep.TOOLS)

            activity.binding.otherButtons.isVisible
                .shouldBeTrue()
            activity.binding.numberButtons.isVisible
                .shouldBeFalse()
            activity.binding.introTitleText.text
                .toString() shouldBe "Delete and hints"
            activity.binding.hintButton.text
                .toString() shouldBe "Hint (3)"
        }

    @Test
    fun `the notes lesson shows the continue button and lets the notes button toggle the mode`() =
        launch { activity ->
            activity.advanceTo(IntroStep.NOTES)

            activity.binding.introContinueLayout.isVisible
                .shouldBeTrue()
            activity.binding.introTitle.isVisible
                .shouldBeFalse()
            activity.binding.noteButton.performClick()
            activity.state.input.notesMode shouldBe true
            activity.binding.noteButton.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary
            activity.binding.noteButton.performClick()
            activity.state.input.notesMode shouldBe false
        }

    @Test
    fun `the notes button does nothing before the notes lesson`() =
        launch { activity ->
            activity.binding.noteButton.performClick()

            activity.state.input.notesMode shouldBe false
        }

    @Test
    fun `a long press on a number the player entered erases it`() =
        launch { activity ->
            activity.advanceTo(IntroStep.SELECT_NUMBER)

            activity.longPressCell(DEMO_CELL_INDEX_4)

            activity.binding.board.render
                ?.board
                ?.get(DEMO_CELL_INDEX_4)
                ?.value
                .shouldBeNull()
        }

    @Test
    fun `a long press on a given number keeps it`() =
        launch { activity ->
            activity.longPressCell(0)

            checkNotNull(activity.binding.board.render).board[0].value shouldBe 3
        }

    @Test
    fun `a tap collapses an expanded toolbar`() =
        launch { activity ->
            activity.binding.sudokuToolbarLayout.setExpanded(expanded = true, animate = false)
            idleMainLooper()
            activity.binding.sudokuToolbarLayout.isExpanded
                .shouldBeTrue()

            activity.tapCell(0)
            idleMainLooper()

            activity.binding.sudokuToolbarLayout.isExpanded
                .shouldBeFalse()
        }

    @Test
    fun `recreation keeps the lesson and the entered numbers`() {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        ActivityScenario.launch<IntroActivity>(Intent(context, IntroActivity::class.java)).use { scenario ->
            scenario.onActivity { it.advanceTo(IntroStep.ENTER_ROW_COLUMN) }

            scenario.recreate()

            scenario.onActivity { activity ->
                activity.state.step shouldBe IntroStep.ENTER_ROW_COLUMN
                activity.binding.introTextText.text
                    .toString() shouldBe
                    "In the middle block, the 2 is missing. Due to the fact that the 2 is already present in row 4 and 5, only the " +
                    "empty field in the bottom row of the middle block remains. To enter the 2 tap on this field."
                checkNotNull(activity.binding.board.render).board[DEMO_CELL_INDEX_4].value shouldBe DEMO_ENTER_DIGIT
            }
        }
    }

    // region menu

    @Test
    fun `menu_skip shows the notifications dialog`() =
        launch { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_skip)).shouldBeTrue()
            val dialog = ShadowDialog.getLatestDialog()
            dialog.shouldNotBeNull()
            dialog.isShowing.shouldBeTrue()
        }

    @Test
    fun `an unknown menu item falls through to the default behavior`() =
        launch { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(-12345)).shouldBeFalse()
        }

    // endregion

    // region continue button / notifications dialog

    @Test
    fun `continue button finishes directly when opened from settings`() =
        launch(openedFromSettings = true) { activity ->
            activity.binding.introContinueButton.performClick()
            activity.isFinishing.shouldBeTrue()
        }

    @Test
    fun `continue button shows a notifications dialog when not opened from settings`() =
        launch { activity ->
            activity.binding.introContinueButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog?
            dialog.shouldNotBeNull()
            dialog.isShowing.shouldBeTrue()
        }

    @Test
    fun `declining notifications advances onboarding, finishes and marks the choice handled`() =
        launch { activity ->
            activity.binding.introContinueButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(BUTTON_NEGATIVE).performClick()
            idleMainLooper()
            activity.isFinishing.shouldBeTrue()
            activity.viewModel.notificationChoice.value shouldBe NotificationChoice.Pending
        }

    @Test
    fun `accepting notifications with permission already granted advances onboarding and finishes`() =
        launch { activity ->
            shadowOf(ApplicationProvider.getApplicationContext<HiltTestApplication>())
                .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            activity.binding.introContinueButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(BUTTON_POSITIVE).performClick()
            idleMainLooper()
            activity.isFinishing.shouldBeTrue()
        }

    @Test
    fun `accepting notifications without permission requests it instead of finishing and marks the choice handled`() =
        launch { activity ->
            shadowOf(ApplicationProvider.getApplicationContext<HiltTestApplication>())
                .denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            activity.binding.introContinueButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(BUTTON_POSITIVE).performClick()
            idleMainLooper()
            activity.isFinishing.shouldBeFalse()
            shadowOf(activity)
                .lastRequestedPermission
                .shouldNotBeNull()
                .requestedPermissions
                .toList() shouldBe
                listOf("android.permission.POST_NOTIFICATIONS")
            activity.viewModel.notificationChoice.value shouldBe NotificationChoice.Pending
        }

    @Test
    fun `granting the requested permission finishes onboarding`() =
        launch { activity ->
            shadowOf(ApplicationProvider.getApplicationContext<HiltTestApplication>())
                .denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            activity.binding.introContinueButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(BUTTON_POSITIVE).performClick()
            idleMainLooper()

            val request = shadowOf(activity).lastRequestedPermission.shouldNotBeNull()
            activity.onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                intArrayOf(android.content.pm.PackageManager.PERMISSION_GRANTED),
            )
            idleMainLooper()

            activity.isFinishing.shouldBeTrue()
        }

    @Test
    fun `denying the requested permission still finishes onboarding`() =
        launch { activity ->
            shadowOf(ApplicationProvider.getApplicationContext<HiltTestApplication>())
                .denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            activity.binding.introContinueButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(BUTTON_POSITIVE).performClick()
            idleMainLooper()

            val request = shadowOf(activity).lastRequestedPermission.shouldNotBeNull()
            activity.onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                intArrayOf(android.content.pm.PackageManager.PERMISSION_DENIED),
            )
            idleMainLooper()

            activity.isFinishing.shouldBeTrue()
        }

    // endregion
}
