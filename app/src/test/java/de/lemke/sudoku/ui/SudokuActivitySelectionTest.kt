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
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.NoteMask
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.game.Selection
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import de.lemke.commonutils.R as commonutilsR

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuActivitySelectionTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var userSettings: UserSettings

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        userSettings.bypassOobe()
        runBlocking { saveSudoku(formulaicSudoku(size = SudokuSize.FOUR, sudokuId = SUDOKU_ID)) }
    }

    private fun launch(
        id: SudokuId = SUDOKU_ID,
        block: (SudokuActivity) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, id.value)
        ActivityScenario.launch<SudokuActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.userSettings.animationsEnabled = false
                block(activity)
            }
        }
    }

    private fun formulaicSudoku(
        size: SudokuSize,
        sudokuId: SudokuId,
    ): Sudoku = testSudoku(size = size, difficulty = Difficulty.VERY_EASY, sudokuId = sudokuId)

    @Test
    fun `tapping a number, the delete and the hint button selects each control`() =
        launch { activity ->
            activity.tapDigit(2)
            activity.selection shouldBe Selection.Digit(2)
            activity.tapDelete()
            activity.selection shouldBe Selection.Delete
            activity.tapHint()
            activity.selection shouldBe Selection.Hint
        }

    @Test
    fun `entering a digit into a given field changes nothing and keeps the number button selected`() =
        launch { activity ->
            val before = activity.sudoku.board
            activity.tapDigit(1)

            activity.tapCell(0)

            activity.sudoku.board shouldBe before
            activity.selection shouldBe Selection.Digit(1)
        }

    @Test
    fun `a selected number button shows in the accent color and the others stay transparent`() =
        launch { activity ->
            activity.tapDigit(1)
            activity.binding.numberButton1.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary.defaultColor

            activity.tapDigit(2)

            activity.binding.numberButton1.backgroundTintList
                ?.defaultColor shouldBe activity.transparent.defaultColor
            activity.binding.numberButton2.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary.defaultColor
        }

    @Test
    fun `a selected delete button shows in the accent color until another control is selected`() =
        launch { activity ->
            activity.tapDelete()
            activity.binding.deleteButton.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary.defaultColor

            activity.tapDigit(1)

            activity.binding.deleteButton.backgroundTintList
                ?.defaultColor shouldBe activity.transparent.defaultColor
        }

    @Test
    fun `a selected hint button shows in the accent color until another control is selected`() =
        launch { activity ->
            activity.tapHint()
            activity.binding.hintButton.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary.defaultColor

            activity.tapDigit(1)

            activity.binding.hintButton.backgroundTintList
                ?.defaultColor shouldBe activity.transparent.defaultColor
        }

    @Test
    fun `the note button shows the notes mode in the accent color`() =
        launch { activity ->
            activity.binding.noteButton.backgroundTintList
                ?.defaultColor shouldBe activity.transparent.defaultColor

            activity.binding.noteButton.performClick()

            activity.binding.noteButton.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary.defaultColor
        }

    // region cell selected

    @Test
    fun `selecting a cell while nothing is selected sets it as selected`() =
        launch { activity ->
            activity.selection.shouldBeNull()
            activity.tapCell(1)
            activity.selection shouldBe Selection.Cell(1)
        }

    @Test
    fun `selecting the same cell again deselects it`() =
        launch { activity ->
            activity.tapCell(2)
            activity.tapCell(2)
            activity.selection.shouldBeNull()
        }

    @Test
    fun `selecting another cell switches the selection`() =
        launch { activity ->
            activity.tapCell(1)
            activity.tapCell(2)
            activity.selection shouldBe Selection.Cell(2)
        }

    @Test
    fun `a selected cell is drawn selected`() =
        launch { activity ->
            activity.tapCell(2)

            activity.binding.board.render
                .shouldNotBeNull()
                .selected shouldBe 2
        }

    @Test
    fun `a number button in notes mode toggles a note on the selected cell`() =
        launch { activity ->
            activity.binding.noteButton.performClick()
            activity.tapCell(1)
            activity.tapDigit(3)
            activity.sudoku.board[1].notes shouldBe NoteMask.of(3)
            activity.sudoku.board[1]
                .value
                .shouldBeNull()
        }

    @Test
    fun `the delete button in notes mode clears the notes of the selected cell`() =
        launch { activity ->
            activity.binding.noteButton.performClick()
            activity.tapCell(1)
            activity.tapDigit(3)
            activity.tapCell(1)
            activity.tapDelete()
            activity.sudoku.board[1].notes shouldBe NoteMask.EMPTY
        }

    @Test
    fun `a number button while a cell is selected places the move and deselects`() =
        launch { activity ->
            activity.tapCell(11)
            activity.tapDigit(1)
            activity.sudoku.board[11].value shouldBe 1
            activity.selection.shouldBeNull()
        }

    @Test
    fun `the delete button while a cell is selected erases the move and deselects`() =
        launch { activity ->
            activity.tapCell(1)
            activity.tapDigit(2)
            activity.sudoku.board[1].value shouldBe 2

            activity.tapCell(1)
            activity.tapDelete()
            activity.sudoku.board[1]
                .value
                .shouldBeNull()
            activity.selection.shouldBeNull()
        }

    @Test
    fun `the hint button while a cell is selected sets a hint and deselects once hints are exhausted`() =
        launch { activity ->
            activity.tapCell(4)
            activity.tapHint()
            activity.sudoku.board[4].origin shouldBe CellOrigin.HINT
            activity.sudoku.board[4].value shouldBe 3
            activity.sudoku.info.isHintAvailable
                .shouldBeFalse()
            activity.selection.shouldBeNull()
            activity.binding.hintButton.isVisible
                .shouldBeFalse()
        }

    // endregion

    // region number selected

    @Test
    fun `selecting the same number button again deselects it`() =
        launch { activity ->
            activity.tapDigit(1)
            activity.tapDigit(1)
            activity.selection.shouldBeNull()
        }

    @Test
    fun `a cell while a number button is selected places the move and keeps the button selected`() =
        launch { activity ->
            activity.tapDigit(1)
            activity.tapCell(11)
            activity.sudoku.board[11].value shouldBe 1
            activity.selection shouldBe Selection.Digit(1)
        }

    @Test
    fun `a selected number draws its notes emphasized`() =
        launch { activity ->
            activity.tapDigit(3)

            activity.binding.board.render
                .shouldNotBeNull()
                .isEmphasizedNote(3)
                .shouldBeTrue()
        }

    @Test
    fun `a selected number without number highlighting draws its notes plain`() =
        launch { activity ->
            userSettings.highlightNumber = false

            activity.tapDigit(3)

            activity.binding.board.render
                .shouldNotBeNull()
                .isEmphasizedNote(3)
                .shouldBeFalse()
        }

    // endregion

    // region delete selected

    @Test
    fun `a cell while the delete button is selected erases without changing the selection`() =
        launch { activity ->
            activity.tapCell(1)
            activity.tapDigit(2)
            activity.sudoku.board[1].value shouldBe 2

            activity.tapDelete()
            activity.tapCell(1)
            activity.sudoku.board[1]
                .value
                .shouldBeNull()
            activity.selection shouldBe Selection.Delete
        }

    // endregion

    // region hint selected

    @Test
    fun `setting a hint from the hint button keeps it selected while hints remain available`() {
        runBlocking { saveSudoku(formulaicSudoku(size = SudokuSize.NINE, sudokuId = LARGE_SUDOKU_ID)) }
        launch(LARGE_SUDOKU_ID) { activity ->
            activity.tapHint()
            activity.tapCell(1)
            activity.sudoku.board[1].origin shouldBe CellOrigin.HINT
            activity.sudoku.info.isHintAvailable
                .shouldBeTrue()
            activity.selection shouldBe Selection.Hint

            activity.tapCell(2)
            activity.sudoku.board[2].origin shouldBe CellOrigin.HINT
            activity.selection shouldBe Selection.Hint
        }
    }

    // endregion

    // region highlighting

    @Test
    fun `selecting a number button without highlighting the number does not mark it as used`() =
        launch { activity ->
            userSettings.highlightNumber = false
            activity.tapDigit(1)
            activity.sudoku.info.numberHighlightingUsed
                .shouldBeFalse()
        }

    @Test
    fun `selecting a number button with highlighting marks it as used`() =
        launch { activity ->
            activity.tapDigit(1)
            activity.sudoku.info.numberHighlightingUsed
                .shouldBeTrue()
        }

    // endregion

    // region completed numbers

    @Test
    fun `completing a number disables its button and wraps the selection to the next incomplete number`() =
        launch { activity ->
            activity.tapDigit(1)
            activity.tapCell(11)
            activity.tapCell(13)

            activity.sudokuButtons[0].isEnabled.shouldBeFalse()
            activity.sudokuButtons[0].currentTextColor shouldBe
                activity.getColor(commonutilsR.color.commonutils_secondary_text_icon_color)
            activity.sudokuButtons[1].isEnabled.shouldBeTrue()

            activity.selection shouldBe Selection.Digit(2)
        }

    @Test
    fun `completing every number wraps the search around and finally clears the selection`() =
        launch { activity ->
            activity.tapDigit(3)
            activity.tapCell(2)
            activity.tapCell(4)
            activity.selection shouldBe Selection.Digit(4)

            activity.tapCell(5)
            activity.tapCell(10)
            activity.selection shouldBe Selection.Digit(1)

            activity.tapDigit(2)
            activity.tapCell(1)
            activity.tapCell(7)
            activity.tapCell(8)
            activity.tapCell(14)
            activity.selection shouldBe Selection.Digit(1)

            activity.tapCell(11)
            activity.tapCell(13)
            activity.selection.shouldBeNull()
        }

    // endregion

    // region error limit

    @Test
    fun `an incorrect move under the error limit does not block further input`() =
        launch { activity ->
            userSettings.errorLimit = 3
            activity.tapCell(1)
            activity.tapDigit(1)
            activity.sudoku.info.errorsMade shouldBe 1

            activity.tapCell(2)
            activity.selection shouldBe Selection.Cell(2)
        }

    @Test
    fun `reaching the error limit blocks further taps`() =
        launch { activity ->
            userSettings.errorLimit = 1
            activity.tapDigit(1)
            activity.tapCell(1)
            activity.sudoku.info.errorsMade shouldBe 1
            activity.selection shouldBe Selection.Digit(1)
            idleMainLooper()
            (ShadowDialog.getLatestDialog() as AlertDialog)
                .findViewById<TextView>(android.R.id.message)
                ?.text
                ?.toString() shouldBe "Error limit reached (1)."

            activity.tapCell(2)
            activity.selection shouldBe Selection.Digit(1)
        }

    // endregion

    companion object {
        private val SUDOKU_ID = SudokuId("selection-test-sudoku-id")
        private val LARGE_SUDOKU_ID = SudokuId("selection-test-large-sudoku-id")
    }
}
