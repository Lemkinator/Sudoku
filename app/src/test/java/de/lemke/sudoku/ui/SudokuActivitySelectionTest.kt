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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
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
            val cellCount = activity.sudoku.info.size.cellCount

            activity.sudokuButtons[1].performClick()
            activity.selected shouldBe cellCount + 1
            activity.binding.deleteButton.performClick()
            activity.selected shouldBe cellCount + 4
            activity.binding.hintButton.performClick()
            activity.selected shouldBe cellCount + 5
        }

    @Test
    fun `entering a digit into a given field changes nothing and keeps the number button selected`() =
        launch { activity ->
            val cellCount = activity.sudoku.info.size.cellCount
            val before = activity.sudoku.board
            activity.select(cellCount)

            activity.select(0)

            activity.sudoku.board shouldBe before
            activity.selected shouldBe cellCount
        }

    // region selectFromNothing

    @Test
    fun `selecting a field while nothing is selected sets it as selected`() =
        launch { activity ->
            activity.selected.shouldBeNull()
            activity.select(1)
            activity.selected shouldBe 1
        }

    @Test
    fun `selecting a number button while nothing is selected highlights it`() =
        launch { activity ->
            val buttonIndex = activity.sudoku.info.size.cellCount
            activity.select(buttonIndex)
            activity.selected shouldBe buttonIndex
            activity.binding.numberButton1.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary.defaultColor
        }

    @Test
    fun `selecting the delete button while nothing is selected highlights it`() =
        launch { activity ->
            val deleteIndex = activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value
            activity.select(deleteIndex)
            activity.selected shouldBe deleteIndex
            activity.binding.deleteButton.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary.defaultColor
        }

    @Test
    fun `selecting the hint button while nothing is selected highlights it`() =
        launch { activity ->
            val hintIndex = activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value + 1
            activity.select(hintIndex)
            activity.selected shouldBe hintIndex
            activity.binding.hintButton.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary.defaultColor
        }

    @Test
    fun `selecting nothing while nothing is selected is a no-op`() =
        launch { activity ->
            activity.select(null)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting an out-of-range index while nothing is selected is a no-op`() =
        launch { activity ->
            activity.select(9999)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting a negative index while nothing is selected is a no-op`() =
        launch { activity ->
            activity.select(-1)
            activity.selected.shouldBeNull()
        }

    // endregion

    // region selectFromField

    @Test
    fun `selecting a number button in notes mode toggles a note on the selected field`() =
        launch { activity ->
            activity.binding.noteButton.performClick()
            activity.select(1)
            activity.select(activity.sudoku.info.size.cellCount + 2)
            activity.sudoku.board[1].notes shouldBe NoteMask.of(3)
            activity.sudoku.board[1]
                .value
                .shouldBeNull()
        }

    @Test
    fun `selecting the delete button in notes mode clears the notes of the selected field`() =
        launch { activity ->
            activity.binding.noteButton.performClick()
            activity.select(1)
            activity.select(activity.sudoku.info.size.cellCount + 2)
            activity.select(1)
            activity.select(activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value)
            activity.sudoku.board[1].notes shouldBe NoteMask.EMPTY
        }

    @Test
    fun `selecting the same field again deselects it`() =
        launch { activity ->
            activity.select(2)
            activity.select(2)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting nothing while a field is selected deselects it`() =
        launch { activity ->
            activity.select(2)
            activity.select(null)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting another field switches the selection`() =
        launch { activity ->
            activity.select(1)
            activity.select(2)
            activity.selected shouldBe 2
        }

    @Test
    fun `selecting a number button while a field is selected places the move and deselects`() =
        launch { activity ->
            activity.select(11)
            activity.select(activity.sudoku.info.size.cellCount)
            activity.sudoku.board[11].value shouldBe 1
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting the delete button while a field is selected erases the move and deselects`() =
        launch { activity ->
            activity.select(1)
            activity.select(activity.sudoku.info.size.cellCount + 1)
            activity.sudoku.board[1].value shouldBe 2

            activity.select(1)
            activity.select(activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value)
            activity.sudoku.board[1]
                .value
                .shouldBeNull()
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting the hint button while a field is selected sets a hint and deselects once hints are exhausted`() =
        launch { activity ->
            activity.select(4)
            activity.select(activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value + 1)
            activity.sudoku.board[4].origin shouldBe CellOrigin.HINT
            activity.sudoku.board[4].value shouldBe 3
            activity.sudoku.info.isHintAvailable
                .shouldBeFalse()
            activity.selected.shouldBeNull()
            activity.binding.hintButton.isVisible
                .shouldBeFalse()
        }

    @Test
    fun `selecting a negative index while a field is selected leaves the selection unchanged`() =
        launch { activity ->
            activity.select(2)
            activity.select(-1)
            activity.selected shouldBe 2
        }

    // endregion

    // region selectFromNumberButton

    @Test
    fun `selecting the same number button again deselects it`() =
        launch { activity ->
            val buttonIndex = activity.sudoku.info.size.cellCount
            activity.select(buttonIndex)
            activity.select(buttonIndex)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting nothing while a number button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.info.size.cellCount)
            activity.select(null)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting a field while a number button is selected places the move and keeps the button selected`() =
        launch { activity ->
            val buttonIndex = activity.sudoku.info.size.cellCount
            activity.select(buttonIndex)
            activity.select(11)
            activity.sudoku.board[11].value shouldBe 1
            activity.selected shouldBe buttonIndex
        }

    @Test
    fun `selecting another number button switches the highlight`() =
        launch { activity ->
            activity.select(activity.sudoku.info.size.cellCount)
            activity.select(activity.sudoku.info.size.cellCount + 1)
            activity.selected shouldBe activity.sudoku.info.size.cellCount + 1
            activity.binding.numberButton1.backgroundTintList
                ?.defaultColor shouldBe activity.transparent.defaultColor
            activity.binding.numberButton2.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary.defaultColor
        }

    @Test
    fun `selecting an out-of-range index while a number button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.info.size.cellCount)
            activity.select(9999)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting a negative index while a number button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.info.size.cellCount)
            activity.select(-1)
            activity.selected.shouldBeNull()
        }

    // endregion

    // region selectFromDeleteButton

    @Test
    fun `selecting the delete button again deselects it`() =
        launch { activity ->
            val deleteIndex = activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value
            activity.select(deleteIndex)
            activity.select(deleteIndex)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting nothing while the delete button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value)
            activity.select(null)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting a field while the delete button is selected erases without changing the selection`() =
        launch { activity ->
            activity.select(1)
            activity.select(activity.sudoku.info.size.cellCount + 1)
            activity.sudoku.board[1].value shouldBe 2

            val deleteIndex = activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value
            activity.select(deleteIndex)
            activity.select(1)
            activity.sudoku.board[1]
                .value
                .shouldBeNull()
            activity.selected shouldBe deleteIndex
        }

    @Test
    fun `selecting another button while the delete button is selected switches the highlight`() =
        launch { activity ->
            activity.select(activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value)
            activity.select(activity.sudoku.info.size.cellCount)
            activity.selected shouldBe activity.sudoku.info.size.cellCount
            activity.binding.deleteButton.backgroundTintList
                ?.defaultColor shouldBe activity.transparent.defaultColor
        }

    @Test
    fun `selecting an out-of-range index while the delete button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value)
            activity.select(9999)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting a negative index while the delete button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value)
            activity.select(-1)
            activity.selected.shouldBeNull()
        }

    // endregion

    // region selectFromHintButton

    @Test
    fun `selecting the hint button again deselects it`() =
        launch { activity ->
            val hintIndex = activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value + 1
            activity.select(hintIndex)
            activity.select(hintIndex)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting nothing while the hint button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value + 1)
            activity.select(null)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting another button while the hint button is selected switches the highlight`() =
        launch { activity ->
            activity.select(activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value + 1)
            activity.select(activity.sudoku.info.size.cellCount)
            activity.selected shouldBe activity.sudoku.info.size.cellCount
            activity.binding.hintButton.backgroundTintList
                ?.defaultColor shouldBe activity.transparent.defaultColor
        }

    @Test
    fun `selecting an out-of-range index while the hint button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value + 1)
            activity.select(9999)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting a negative index while the hint button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value + 1)
            activity.select(-1)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `setting a hint from the hint button keeps it selected while hints remain available`() {
        runBlocking { saveSudoku(formulaicSudoku(size = SudokuSize.NINE, sudokuId = LARGE_SUDOKU_ID)) }
        launch(LARGE_SUDOKU_ID) { activity ->
            val hintIndex = activity.sudoku.info.size.cellCount + activity.sudoku.info.size.value + 1
            activity.select(hintIndex)
            activity.select(1)
            activity.sudoku.board[1].origin shouldBe CellOrigin.HINT
            activity.sudoku.info.isHintAvailable
                .shouldBeTrue()
            activity.selected shouldBe hintIndex

            activity.select(2)
            activity.sudoku.board[2].origin shouldBe CellOrigin.HINT
            activity.selected shouldBe hintIndex
        }
    }

    // endregion

    // region selectButton

    @Test
    fun `selecting a number button without highlighting the number does not mark it as used`() =
        launch { activity ->
            userSettings.highlightNumber = false
            activity.select(activity.sudoku.info.size.cellCount)
            activity.sudoku.info.numberHighlightingUsed
                .shouldBeFalse()
        }

    // endregion

    // region checkAnyNumberCompleted / highlightCurrentNumber / selectNextButton

    @Test
    fun `completing a number disables its button and wraps the highlight to the next incomplete number`() =
        launch { activity ->
            val numberOneButton = activity.sudoku.info.size.cellCount
            activity.select(numberOneButton)
            activity.select(11)
            activity.select(13)

            activity.sudokuButtons[0].isEnabled.shouldBeFalse()
            activity.sudokuButtons[0].currentTextColor shouldBe
                activity.getColor(commonutilsR.color.commonutils_secondary_text_icon_color)
            activity.sudokuButtons[1].isEnabled.shouldBeTrue()

            activity.selected shouldBe activity.sudoku.info.size.cellCount + 1
        }

    @Test
    fun `completing every number wraps the search around and finally clears the selection`() =
        launch { activity ->
            val n1 = activity.sudoku.info.size.cellCount
            val n2 = n1 + 1
            val n3 = n1 + 2
            val n4 = n1 + 3

            activity.select(n3)
            activity.select(2)
            activity.select(4)
            activity.selected shouldBe n4

            activity.select(5)
            activity.select(10)
            activity.selected shouldBe n1

            activity.select(n2)
            activity.select(1)
            activity.select(7)
            activity.select(8)
            activity.select(14)
            activity.selected shouldBe n1

            activity.select(11)
            activity.select(13)
            activity.selected.shouldBeNull()
        }

    // endregion

    // region checkErrorLimit guarding select()

    @Test
    fun `an incorrect move under the error limit does not block further input`() =
        launch { activity ->
            userSettings.errorLimit = 3
            activity.select(1)
            activity.select(activity.sudoku.info.size.cellCount)
            activity.sudoku.info.errorsMade shouldBe 1

            activity.select(2)
            activity.selected shouldBe 2
        }

    @Test
    fun `reaching the error limit blocks further select calls`() =
        launch { activity ->
            userSettings.errorLimit = 1
            val numberOneButton = activity.sudoku.info.size.cellCount
            activity.select(numberOneButton)
            activity.select(1)
            activity.sudoku.info.errorsMade shouldBe 1
            activity.selected shouldBe numberOneButton
            idleMainLooper()
            (ShadowDialog.getLatestDialog() as AlertDialog)
                .findViewById<TextView>(android.R.id.message)
                ?.text
                ?.toString() shouldBe "Error limit reached (1)."

            activity.select(2)
            activity.selected shouldBe numberOneButton
        }

    // endregion

    companion object {
        private val SUDOKU_ID = SudokuId("selection-test-sudoku-id")
        private val LARGE_SUDOKU_ID = SudokuId("selection-test-large-sudoku-id")
    }
}
