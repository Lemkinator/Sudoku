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
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.sudoku.data.UserSettings
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private val BLOCK_0 = setOf(0, 1, 2, 9, 10, 11, 18, 19, 20)
private val ROW_1 = (9..17).toSet()
private val COLUMN_5 = (0..8).map { it * 9 + 5 }.toSet()
private val ROW_0 = (0..8).toSet()
private val ROWS_3_AND_4 = (27..44).toSet()
private val BLOCK_2 = setOf(6, 7, 8, 15, 16, 17, 24, 25, 26)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class IntroActivityAnimationTest {
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
    }

    private fun launch(
        animationsEnabled: Boolean = false,
        block: (IntroActivity) -> Unit,
    ) {
        userSettings.animationsEnabled = animationsEnabled
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        ActivityScenario.launch<IntroActivity>(Intent(context, IntroActivity::class.java)).use { scenario ->
            scenario.onActivity { activity -> block(activity) }
        }
    }

    private fun idle(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private val IntroActivity.highlighted get() = checkNotNull(binding.board.render).highlighted

    private val IntroActivity.selected get() = checkNotNull(binding.board.render).selected

    private fun IntroActivity.next() = binding.introNextButton.performClick()

    private fun IntroActivity.tapCell(index: Int) = checkNotNull(binding.board.onCellClick)(index)

    private fun IntroActivity.tapDigit(digit: Int) = (if (digit == 2) binding.numberButton2 else binding.numberButton5).performClick()

    private fun IntroActivity.toStep(step: IntroStep) {
        while (viewModel.state.value.step != step) {
            when (viewModel.state.value.step) {
                IntroStep.SELECT_CELL -> tapCell(DEMO_CELL_INDEX_4)
                IntroStep.ENTER_NUMBER -> tapDigit(DEMO_ENTER_DIGIT)
                IntroStep.SELECT_NUMBER -> tapDigit(DEMO_SELECT_DIGIT)
                IntroStep.ENTER_ROW_COLUMN -> tapCell(DEMO_CELL_INDEX_49)
                IntroStep.ENTER_BLOCK -> tapCell(DEMO_CELL_INDEX_24)
                else -> next()
            }
        }
    }

    @Test
    fun `the first lesson tints block 0, then row 1, then column 5, and starts over`() =
        launch { activity ->
            activity.highlighted shouldBe emptySet()

            idle(900)
            activity.highlighted shouldBe BLOCK_0
            idle(900)
            activity.highlighted shouldBe ROW_1
            idle(900)
            activity.highlighted shouldBe COLUMN_5
            idle(900)
            activity.highlighted shouldBe BLOCK_0
        }

    @Test
    fun `the select-cell lesson tints row 0 while it pulses the target`() =
        launch { activity ->
            activity.toStep(IntroStep.SELECT_CELL)
            idle(50)

            activity.highlighted shouldBe ROW_0
        }

    @Test
    fun `the row-and-column lesson tints rows 3 and 4`() =
        launch { activity ->
            activity.toStep(IntroStep.ENTER_ROW_COLUMN)
            idle(50)

            activity.highlighted shouldBe ROWS_3_AND_4
        }

    @Test
    fun `the block lesson tints block 2`() =
        launch { activity ->
            activity.toStep(IntroStep.ENTER_BLOCK)
            idle(50)

            activity.highlighted shouldBe BLOCK_2
        }

    @Test
    fun `the caution lesson shows cell 4, a number, number 5, cell 21 and number 2 in turn`() =
        launch { activity ->
            activity.toStep(IntroStep.NUMBER_ENTRY_DEMO)

            idle(1200)
            activity.selected shouldBe DEMO_CELL_INDEX_4
            idle(1200)
            activity.selected.shouldBeNull()
            activity.binding.numberButton8.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary
            idle(1200)
            activity.binding.numberButton5.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary
            idle(1200)
            activity.selected shouldBe 21
            idle(1200)
            activity.binding.numberButton2.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary
            idle(1200)
            activity.selected shouldBe DEMO_CELL_INDEX_4
        }

    @Test
    fun `the select-cell lesson ends its loop once the cell is tapped`() =
        launch { activity ->
            activity.toStep(IntroStep.SELECT_CELL)
            idle(50)

            activity.tapCell(DEMO_CELL_INDEX_4)
            idle(2100)

            activity.animation
                .shouldNotBeNull()
                .isCompleted
                .shouldBeTrue()
        }

    @Test
    fun `the caution lesson ends its loop once the lesson changes`() =
        launch { activity ->
            activity.toStep(IntroStep.NUMBER_ENTRY_DEMO)
            idle(1200)

            activity.next()
            repeat(6) { idle(1200) }

            activity.animation
                .shouldNotBeNull()
                .isCompleted
                .shouldBeTrue()
        }

    @Test
    fun `leaving a lesson ends its animation and clears the tint`() =
        launch { activity ->
            idle(900)
            activity.highlighted shouldBe BLOCK_0

            activity.next()
            idle(1800)

            activity.highlighted shouldBe emptySet()
            activity.animation
                .shouldNotBeNull()
                .isCompleted
                .shouldBeTrue()
        }

    @Test
    fun `a lesson pulses its target cell when animations are enabled`() =
        launch(animationsEnabled = true) { activity ->
            activity.toStep(IntroStep.SELECT_CELL)

            activity.binding.board.isAnimating
                .shouldBeTrue()
        }

    @Test
    fun `a lesson does not pulse when animations are disabled`() =
        launch { activity ->
            activity.toStep(IntroStep.SELECT_CELL)

            activity.binding.board.isAnimating
                .shouldBeFalse()
        }

    @Test
    fun `a lesson without an animation ends its job at once`() =
        launch { activity ->
            activity.next()
            idle(50)

            activity.animation
                .shouldNotBeNull()
                .isCompleted
                .shouldBeTrue()
        }
}
