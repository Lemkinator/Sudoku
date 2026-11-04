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

package de.lemke.sudoku.ui.utils

import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import de.lemke.commonutils.awaitMainLooperUntil
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testLevelSudoku
import io.kotest.matchers.shouldBe
import java.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private class PulseRecordingTextView(context: Context) : TextView(context) {
    var animateCalls = 0

    override fun animate(): android.view.ViewPropertyAnimator {
        animateCalls++
        return super.animate()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FieldAnimationsPulseTest {
    private val scope = TestScope()
    private lateinit var fieldViews: List<FieldView>
    private lateinit var pulseViews: List<PulseRecordingTextView>
    private val origin get() = fieldViews.first().position

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val board = testLevelSudoku(size = SudokuSize.FOUR).board
        val adapter =
            SudokuViewAdapter(activity, board, onFieldClicked = {}, onFieldLongClicked = { false }, onHighlightingUsed = {})
        for (index in board.cells.indices) {
            adapter.onBindViewHolder(adapter.onCreateViewHolder(FrameLayout(activity), 0), index)
        }
        fieldViews = adapter.fieldViews
        val container = LinearLayout(activity)
        activity.setContentView(container)
        fieldViews.forEach(container::addView)
        awaitMainLooperUntil { fieldViews.all { it.fieldViewValue != null } }
        pulseViews =
            fieldViews.map { fieldView ->
                PulseRecordingTextView(activity).also {
                    fieldView.addView(it)
                    fieldView.fieldViewValue = it
                }
            }
        settleViewAnimations()
    }

    private fun pulsedIndices() = pulseViews.indices.filter { pulseViews[it].animateCalls > 0 }.toSet()

    private fun settleViewAnimations() {
        repeat(SETTLE_FRAMES) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MILLIS)) }
    }

    private fun assertAllFieldsAtRest() {
        for (view in pulseViews) {
            view.scaleX shouldBe 1f
            view.scaleY shouldBe 1f
            view.alpha shouldBe 1f
            view.rotation shouldBe 0f
        }
    }

    @Test
    fun `pulsing the whole board takes a wave over every field and leaves each at rest`() {
        val job = scope.animateFields(fieldViews, origin, setOf(FieldRegion.BOARD))

        scope.advanceTimeBy(100)
        scope.runCurrent()
        job.isCompleted shouldBe false
        scope.advanceTimeBy(500)
        scope.runCurrent()
        job.isCompleted shouldBe false
        scope.advanceTimeBy(20)
        scope.runCurrent()
        job.isCompleted shouldBe true
        settleViewAnimations()

        pulsedIndices() shouldBe fieldViews.indices.toSet()
        assertAllFieldsAtRest()
    }

    @Test
    fun `pulsing a column and a block pulses exactly their fields and leaves each at rest`() {
        val job = scope.animateFields(fieldViews, origin, setOf(FieldRegion.COLUMN, FieldRegion.BLOCK))

        scope.advanceTimeBy(2_000)
        scope.runCurrent()
        job.isCompleted shouldBe true
        settleViewAnimations()

        pulsedIndices() shouldBe setOf(0, 1, 4, 5, 8, 12)
        assertAllFieldsAtRest()
    }

    @Test
    fun `pulsing the board from an interior field completes after the longer backward wave and leaves each field at rest`() {
        val interior = fieldViews[10].position

        val job = scope.animateFields(fieldViews, interior, setOf(FieldRegion.BOARD))

        scope.advanceTimeBy(100)
        scope.runCurrent()
        job.isCompleted shouldBe false
        scope.advanceTimeBy(400)
        scope.runCurrent()
        job.isCompleted shouldBe false
        scope.advanceTimeBy(60)
        scope.runCurrent()
        job.isCompleted shouldBe true
        settleViewAnimations()

        pulsedIndices() shouldBe fieldViews.indices.toSet()
        assertAllFieldsAtRest()
    }

    @Test
    fun `pulsing no region finishes at once and pulses no field`() {
        val job = scope.animateFields(fieldViews, origin, emptySet())

        scope.runCurrent()

        job.isCompleted shouldBe true
        pulsedIndices() shouldBe emptySet()
        assertAllFieldsAtRest()
    }

    private companion object {
        const val FRAME_MILLIS = 20L
        const val SETTLE_FRAMES = 25
    }
}
