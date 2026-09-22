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

import android.os.Looper
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.testLevelSudoku
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Every 3rd field (index % 3 == 0) is given, per testLevelSudoku; index 1 is neither given nor a hint.
private const val GIVEN_INDEX = 0
private const val PLAIN_INDEX = 1

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SudokuViewAdapterTest {
    private lateinit var context: AppCompatActivity
    private lateinit var sudoku: Sudoku
    private lateinit var adapter: SudokuViewAdapter

    @Before
    fun setUp() {
        context = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        sudoku = testLevelSudoku(size = 4)
        adapter = SudokuViewAdapter(context, sudoku)
        for (index in 0 until sudoku.itemCount) {
            val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
            adapter.onBindViewHolder(holder, index)
        }
    }

    private fun awaitInflated(fieldView: FieldView) {
        val deadline = System.currentTimeMillis() + 5_000
        while (fieldView.fieldViewValue == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        check(fieldView.fieldViewValue != null) { "FieldView's async inflate did not complete within 5000ms" }
    }

    @Test
    fun `getItemCount matches the sudoku's item count`() {
        adapter.itemCount shouldBe sudoku.itemCount
    }

    @Test
    fun `onCreateViewHolder and onBindViewHolder populate the field view at the bound index`() {
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)

        adapter.onBindViewHolder(holder, 7)

        adapter.fieldViews[7] shouldBe holder.itemView
        adapter.fieldViews[7].position.index shouldBe 7
    }

    @Test
    fun `updateFieldView refreshes the bound field view's display`() {
        val fieldView = adapter.fieldViews[PLAIN_INDEX]
        awaitInflated(fieldView)
        fieldView.field.value = 3

        adapter.updateFieldView(PLAIN_INDEX)

        fieldView.fieldViewValue?.text.toString() shouldBe "3"
    }

    @Test
    fun `selecting a non-given, non-hint field with highlightNeighbors true highlights its row, column and block`() {
        val expectedNeighbors = sudoku.getNeighbors(PLAIN_INDEX).map { it.position.index }.toSet()

        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)

        sudoku.regionalHighlightingUsed shouldBe true
        for (i in 0 until sudoku.itemCount) {
            adapter.fieldViews[i].isHighlighted shouldBe (i in expectedNeighbors)
        }
        adapter.fieldViews[PLAIN_INDEX].isSelected shouldBe true
    }

    @Test
    fun `selecting a given field does not highlight neighbors even when highlightNeighbors is true`() {
        adapter.selectFieldView(GIVEN_INDEX, highlightNeighbors = true, highlightNumber = false)

        sudoku.regionalHighlightingUsed shouldBe true
        for (i in 0 until sudoku.itemCount) {
            adapter.fieldViews[i].isHighlighted shouldBe false
        }
        adapter.fieldViews[GIVEN_INDEX].isSelected shouldBe true
    }

    @Test
    fun `selecting a hint field does not highlight neighbors even when highlightNeighbors is true`() {
        sudoku[PLAIN_INDEX].hint = true

        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)

        sudoku.regionalHighlightingUsed shouldBe true
        for (i in 0 until sudoku.itemCount) {
            adapter.fieldViews[i].isHighlighted shouldBe false
        }
        adapter.fieldViews[PLAIN_INDEX].isSelected shouldBe true
    }

    @Test
    fun `selecting a field with highlightNeighbors false does not touch highlighting state`() {
        val expectedNeighbors = sudoku.getNeighbors(PLAIN_INDEX).map { it.position.index }.toSet()
        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)
        sudoku.regionalHighlightingUsed shouldBe true

        adapter.selectFieldView(GIVEN_INDEX, highlightNeighbors = false, highlightNumber = false)

        // The highlight state from the prior highlightNeighbors=true call survives untouched.
        for (i in 0 until sudoku.itemCount) {
            adapter.fieldViews[i].isHighlighted shouldBe (i in expectedNeighbors)
        }
        adapter.fieldViews[GIVEN_INDEX].isSelected shouldBe true
    }

    @Test
    fun `selecting null position with highlightNumber true clears region and number highlighting`() {
        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)
        adapter.fieldViews[2].isHighlightedNumber = true

        adapter.selectFieldView(null, highlightNeighbors = true, highlightNumber = true)

        for (i in 0 until sudoku.itemCount) {
            adapter.fieldViews[i].isHighlighted shouldBe false
            adapter.fieldViews[i].isSelected shouldBe false
            adapter.fieldViews[i].isHighlightedNumber shouldBe false
        }
    }

    @Test
    fun `selecting null position with highlightNumber false does not touch number highlighting`() {
        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)
        adapter.fieldViews[2].isHighlightedNumber = true

        // highlightNeighbors false this time skips the block that would otherwise reset isHighlightedNumber
        // for every field, isolating the else-if (highlightNumber)'s own false arm.
        adapter.selectFieldView(null, highlightNeighbors = false, highlightNumber = false)

        adapter.fieldViews[2].isHighlightedNumber shouldBe true
    }

    @Test
    fun `re-selecting the same position does not recompute neighbor highlighting`() {
        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)
        val neighborIndex = sudoku.getNeighbors(PLAIN_INDEX).map { it.position.index }.first { it != PLAIN_INDEX }
        adapter.fieldViews[neighborIndex].isHighlighted = false

        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)

        adapter.fieldViews[neighborIndex].isHighlighted shouldBe false
    }

    @Test
    fun `highlightNumber null clears number highlighting for every field`() {
        adapter.highlightNumber(3)

        adapter.highlightNumber(null)

        sudoku.numberHighlightingUsed shouldBe true
        for (i in 0 until sudoku.itemCount) {
            adapter.fieldViews[i].isHighlightedNumber shouldBe false
        }
    }

    @Test
    fun `highlightNumber marks only fields whose value matches`() {
        val matching = (0 until sudoku.itemCount).filter { sudoku[it].value == 3 }.toSet()
        matching.shouldNotBeEmpty()

        adapter.highlightNumber(3)

        sudoku.numberHighlightingUsed shouldBe true
        for (i in 0 until sudoku.itemCount) {
            adapter.fieldViews[i].isHighlightedNumber shouldBe (i in matching)
        }
    }
}
