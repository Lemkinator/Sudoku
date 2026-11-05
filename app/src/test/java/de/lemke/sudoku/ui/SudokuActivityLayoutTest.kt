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
import android.view.View
import android.view.View.MeasureSpec
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.game.Selection
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private val SUDOKU_ID = SudokuId("layout-test-sudoku-id")

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SudokuActivityLayoutTest {
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
        userSettings.animationsEnabled = false
        runBlocking { saveSudoku(testSudoku(size = SudokuSize.NINE, sudokuId = SUDOKU_ID)) }
    }

    private fun launch(block: (ActivityScenario<SudokuActivity>) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, SUDOKU_ID.value)
        ActivityScenario.launch<SudokuActivity>(intent).use(block)
    }

    @Test
    fun `a portrait window puts the controls below the board`() =
        launch { scenario ->
            scenario.onActivity { activity ->
                val board = activity.binding.board
                val controls = activity.binding.gameButtons
                (controls.top >= board.bottom).shouldBeTrue()
                board.width shouldBe board.height
                controls.width shouldBe activity.binding.gameLayout.width
            }
        }

    @Test
    @Config(qualifiers = "+land")
    fun `a landscape window puts the controls right of the board`() =
        launch { scenario ->
            scenario.onActivity { activity ->
                val board = activity.binding.board
                val controls = activity.binding.gameButtons
                (controls.left >= board.right).shouldBeTrue()
                board.width shouldBe board.height
                controls.height shouldBe activity.binding.gameLayout.height
            }
        }

    @Test
    @Config(qualifiers = "+land")
    fun `a right-to-left landscape window puts the controls left of the board`() =
        launch { scenario ->
            scenario.onActivity { activity ->
                val layout = activity.binding.gameLayout
                layout.layoutDirection = View.LAYOUT_DIRECTION_RTL
                layout.measure(
                    MeasureSpec.makeMeasureSpec(layout.width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(layout.height, MeasureSpec.EXACTLY),
                )
                layout.layout(layout.left, layout.top, layout.right, layout.bottom)

                val board = activity.binding.board
                val controls = activity.binding.gameButtons
                (controls.right <= board.left).shouldBeTrue()
            }
        }

    @Test
    fun `recreation keeps the selection and the notes mode and the timer ticks once per second`() =
        launch { scenario ->
            scenario.onActivity {
                it.binding.noteButton.performClick()
                it.tapCell(1)
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                activity.selection shouldBe Selection.Cell(1)
                activity.viewModel.input.value.notesMode shouldBe true
                activity.binding.board.render
                    ?.selected shouldBe 1
                val before = activity.sudoku.info.seconds
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
                activity.sudoku.info.seconds shouldBe before + 3
            }
        }
}
