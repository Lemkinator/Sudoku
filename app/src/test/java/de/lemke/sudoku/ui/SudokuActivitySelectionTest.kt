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
import androidx.core.view.isVisible
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
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.testLevelSudoku
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import de.lemke.commonutils.R as commonutilsR

/**
 * Drives [SudokuActivity.select] (and the `selectFrom*` delegates in `SudokuActivitySelection.kt`) through the real
 * dispatcher, the way [SudokuActivity.SudokuGameListener.onFieldClicked] and the button click listeners
 * (`initSudokuButtons()`) do in production. A size-4 board keeps field/button indices small enough to reason about:
 * `itemCount` = 16, number buttons = 16..19, delete = 20, hint = 21.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuActivitySelectionTest {
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
        runBlocking { saveSudoku(testLevelSudoku(size = 4, level = MODE_NORMAL, sudokuId = SUDOKU_ID)) }
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
            val buttonIndex = activity.sudoku.itemCount
            activity.select(buttonIndex)
            activity.selected shouldBe buttonIndex
            activity.binding.numberButton1.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary.defaultColor
        }

    @Test
    fun `selecting the delete button while nothing is selected highlights it`() =
        launch { activity ->
            val deleteIndex = activity.sudoku.itemCount + activity.sudoku.size
            activity.select(deleteIndex)
            activity.selected shouldBe deleteIndex
            activity.binding.deleteButton.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary.defaultColor
        }

    @Test
    fun `selecting the hint button while nothing is selected highlights it`() =
        launch { activity ->
            val hintIndex = activity.sudoku.itemCount + activity.sudoku.size + 1
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

    // endregion

    // region selectFromField

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
            // index 11 is empty (not given) with solution 1 on the size-4 formulaic board
            activity.select(11)
            activity.select(activity.sudoku.itemCount) // number button "1"
            activity.sudoku[11].value shouldBe 1
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting the delete button while a field is selected erases the move and deselects`() =
        launch { activity ->
            // index 1 is empty (not given) with solution 2
            activity.select(1)
            activity.select(activity.sudoku.itemCount + 1) // number button "2"
            activity.sudoku[1].value shouldBe 2

            activity.select(1)
            activity.select(activity.sudoku.itemCount + activity.sudoku.size) // delete
            activity.sudoku[1].value.shouldBeNull()
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting the hint button while a field is selected sets a hint and deselects once hints are exhausted`() =
        launch { activity ->
            // index 4 is empty (not given) with solution 3; size-4 boards have a hint limit of 1
            activity.select(4)
            activity.select(activity.sudoku.itemCount + activity.sudoku.size + 1) // hint
            activity.sudoku[4].hint.shouldBeTrue()
            activity.sudoku[4].value shouldBe 3
            activity.sudoku.isHintAvailable.shouldBeFalse()
            activity.selected.shouldBeNull()
            activity.binding.hintButton.isVisible
                .shouldBeFalse()
        }

    // endregion

    // region selectFromNumberButton

    @Test
    fun `selecting the same number button again deselects it`() =
        launch { activity ->
            val buttonIndex = activity.sudoku.itemCount
            activity.select(buttonIndex)
            activity.select(buttonIndex)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting nothing while a number button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.itemCount)
            activity.select(null)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting a field while a number button is selected places the move and keeps the button selected`() =
        launch { activity ->
            val buttonIndex = activity.sudoku.itemCount // number button "1"
            activity.select(buttonIndex)
            activity.select(11) // empty field, solution 1
            activity.sudoku[11].value shouldBe 1
            activity.selected shouldBe buttonIndex
        }

    @Test
    fun `selecting another number button switches the highlight`() =
        launch { activity ->
            activity.select(activity.sudoku.itemCount)
            activity.select(activity.sudoku.itemCount + 1)
            activity.selected shouldBe activity.sudoku.itemCount + 1
            activity.binding.numberButton1.backgroundTintList
                ?.defaultColor shouldBe activity.transparent.defaultColor
            activity.binding.numberButton2.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary.defaultColor
        }

    @Test
    fun `selecting an out-of-range index while a number button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.itemCount)
            activity.select(9999)
            activity.selected.shouldBeNull()
        }

    // endregion

    // region selectFromDeleteButton

    @Test
    fun `selecting the delete button again deselects it`() =
        launch { activity ->
            val deleteIndex = activity.sudoku.itemCount + activity.sudoku.size
            activity.select(deleteIndex)
            activity.select(deleteIndex)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting nothing while the delete button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.itemCount + activity.sudoku.size)
            activity.select(null)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting a field while the delete button is selected erases without changing the selection`() =
        launch { activity ->
            // fill index 1 first so there is something to erase
            activity.select(1)
            activity.select(activity.sudoku.itemCount + 1) // number button "2"
            activity.sudoku[1].value shouldBe 2

            val deleteIndex = activity.sudoku.itemCount + activity.sudoku.size
            activity.select(deleteIndex)
            activity.select(1)
            activity.sudoku[1].value.shouldBeNull()
            // selectFromDeleteButton's field branch never reassigns `selected`, unlike selectFromField's delete branch
            activity.selected shouldBe deleteIndex
        }

    @Test
    fun `selecting another button while the delete button is selected switches the highlight`() =
        launch { activity ->
            activity.select(activity.sudoku.itemCount + activity.sudoku.size)
            activity.select(activity.sudoku.itemCount)
            activity.selected shouldBe activity.sudoku.itemCount
            activity.binding.deleteButton.backgroundTintList
                ?.defaultColor shouldBe activity.transparent.defaultColor
        }

    @Test
    fun `selecting an out-of-range index while the delete button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.itemCount + activity.sudoku.size)
            activity.select(9999)
            activity.selected.shouldBeNull()
        }

    // endregion

    // region selectFromHintButton

    @Test
    fun `selecting the hint button again deselects it`() =
        launch { activity ->
            val hintIndex = activity.sudoku.itemCount + activity.sudoku.size + 1
            activity.select(hintIndex)
            activity.select(hintIndex)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting nothing while the hint button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.itemCount + activity.sudoku.size + 1)
            activity.select(null)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `selecting another button while the hint button is selected switches the highlight`() =
        launch { activity ->
            activity.select(activity.sudoku.itemCount + activity.sudoku.size + 1)
            activity.select(activity.sudoku.itemCount)
            activity.selected shouldBe activity.sudoku.itemCount
            activity.binding.hintButton.backgroundTintList
                ?.defaultColor shouldBe activity.transparent.defaultColor
        }

    @Test
    fun `selecting an out-of-range index while the hint button is selected deselects it`() =
        launch { activity ->
            activity.select(activity.sudoku.itemCount + activity.sudoku.size + 1)
            activity.select(9999)
            activity.selected.shouldBeNull()
        }

    @Test
    fun `setting a hint from the hint button keeps it selected while hints remain available`() {
        // size-9 formulaic board has a hint limit of 3, so a single hint doesn't exhaust it
        runBlocking { saveSudoku(formulaicSudoku(size = 9, sudokuId = LARGE_SUDOKU_ID)) }
        launch(LARGE_SUDOKU_ID) { activity ->
            val hintIndex = activity.sudoku.itemCount + activity.sudoku.size + 1
            activity.select(hintIndex)
            activity.select(1) // empty (not given) field
            activity.sudoku[1].hint.shouldBeTrue()
            activity.sudoku.isHintAvailable.shouldBeTrue()
            activity.selected shouldBe hintIndex

            activity.select(2) // another empty (not given) field, still selected on the hint button
            activity.sudoku[2].hint.shouldBeTrue()
            activity.selected shouldBe hintIndex
        }
    }

    // endregion

    // region checkAnyNumberCompleted / highlightCurrentNumber / selectNextButton

    @Test
    fun `completing a number disables its button and wraps the highlight to the next incomplete number`() =
        launch { activity ->
            val numberOneButton = activity.sudoku.itemCount
            activity.select(numberOneButton)
            activity.select(11) // solution 1, 3rd of 4 correct cells (0 and 6 are given)
            activity.select(13) // solution 1, completes number 1

            activity.sudokuButtons[0].isEnabled.shouldBeFalse()
            activity.sudokuButtons[0].currentTextColor shouldBe
                activity.getColor(commonutilsR.color.commonutils_secondary_text_icon_color)
            activity.sudokuButtons[1].isEnabled.shouldBeTrue()

            // highlightCurrentNumber found number 1 already complete and selectNextButton wrapped to number 2
            activity.selected shouldBe activity.sudoku.itemCount + 1
        }

    // endregion

    // region checkErrorLimit guarding select()

    @Test
    fun `an incorrect move under the error limit does not block further input`() =
        launch { activity ->
            userSettings.errorLimit = 3
            // index 1 has solution 2; placing 1 is an error but stays under the limit
            activity.select(1)
            activity.select(activity.sudoku.itemCount) // number button "1"
            activity.sudoku.errorsMade shouldBe 1

            activity.select(2)
            activity.selected shouldBe 2
        }

    @Test
    fun `reaching the error limit blocks further select calls`() =
        launch { activity ->
            userSettings.errorLimit = 1
            val numberOneButton = activity.sudoku.itemCount
            activity.select(numberOneButton)
            // index 1 has solution 2; placing 1 is a wrong move that reaches the limit of 1.
            // selectFromNumberButton's field branch never reassigns `selected`, so it stays on the button.
            activity.select(1)
            activity.sudoku.errorsMade shouldBe 1
            activity.selected shouldBe numberOneButton

            // checkErrorLimit() now short-circuits select() before the when-dispatch runs
            activity.select(2)
            activity.selected shouldBe numberOneButton
        }

    // endregion

    private fun formulaicSudoku(
        size: Int,
        sudokuId: SudokuId,
    ): Sudoku {
        val blockSize = sqrt(size.toDouble()).toInt()
        return Sudoku.create(
            sudokuId = sudokuId,
            size = size,
            difficulty = Difficulty.VERY_EASY,
            modeLevel = MODE_NORMAL,
            fields =
                MutableList(size * size) { index ->
                    val row = index / size
                    val col = index % size
                    val solution = (blockSize * (row % blockSize) + row / blockSize + col) % size + 1
                    val given = index % 3 == 0
                    Field(
                        position = Position.create(index, size),
                        solution = solution,
                        value = if (given) solution else null,
                        given = given,
                    )
                },
        )
    }

    companion object {
        private val SUDOKU_ID = SudokuId("selection-test-sudoku-id")
        private val LARGE_SUDOKU_ID = SudokuId("selection-test-large-sudoku-id")
    }
}
