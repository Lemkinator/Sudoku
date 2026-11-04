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

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import de.lemke.commonutils.awaitMainLooperUntil
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.NoteMask
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val SIZE = SudokuSize.FOUR
private const val GIVEN_INDEX = 0
private const val PLAIN_INDEX = 1
private const val HINT_INDEX = 4
private const val ERROR_INDEX = 5
private const val COLORED_INDEX = 8
private const val CORNER_NOTES_INDEX = 12
private const val COLORED_BY_COLUMN_INDEX = 2
private const val UNCOLORED_BOTH_HALVES_INDEX = 10

private fun solutionFor(index: Int): Int {
    val blockSize = SIZE.blockSize
    val row = index / SIZE.value
    val col = index % SIZE.value
    return (blockSize * (row % blockSize) + row / blockSize + col) % SIZE.value + 1
}

private fun fourByFourBoard(): Board {
    val overrides =
        mapOf(
            GIVEN_INDEX to Cell(solutionFor(GIVEN_INDEX), value = 1, origin = CellOrigin.GIVEN),
            PLAIN_INDEX to Cell(solutionFor(PLAIN_INDEX), value = 2),
            HINT_INDEX to Cell(solutionFor(HINT_INDEX), value = 3, origin = CellOrigin.HINT),
            ERROR_INDEX to Cell(solutionFor(ERROR_INDEX), value = 1),
        )
    return Board(SIZE, List(SIZE.cellCount) { index -> overrides[index] ?: Cell(solutionFor(index)) })
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FieldViewTest {
    private lateinit var context: AppCompatActivity
    private lateinit var board: Board
    private lateinit var adapter: SudokuViewAdapter
    private val clicked = mutableListOf<Int>()
    private val longClicked = mutableListOf<Int>()
    private var longClickConsumed = true

    @Before
    fun setUp() {
        context = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        board = fourByFourBoard()
        adapter =
            SudokuViewAdapter(
                context,
                board,
                onFieldClicked = { clicked += it },
                onFieldLongClicked = {
                    longClicked += it
                    longClickConsumed
                },
                onHighlightingUsed = {},
            )
    }

    private fun inflatedFieldView(index: Int): FieldView {
        val fieldView = FieldView(context)
        // performLongClick() falls through to View.showContextMenu(), which NPEs without a parent.
        FrameLayout(context).addView(fieldView)
        awaitMainLooperUntil { fieldView.fieldViewValue != null }
        fieldView.init(board, index, adapter)
        return fieldView
    }

    @Test
    fun `no border is drawn for a cell away from any block boundary`() {
        val fieldView = FieldView(context)
        FrameLayout(context).addView(fieldView)
        awaitMainLooperUntil { fieldView.fieldViewValue != null }
        val sentinel = ColorDrawable(Color.MAGENTA)
        fieldView.foreground = sentinel

        fieldView.init(board, GIVEN_INDEX, adapter)

        fieldView.foreground shouldBe sentinel
    }

    @Test
    fun `a bottom border is drawn for a row block boundary`() {
        val fieldView = inflatedFieldView(HINT_INDEX)

        fieldView.foreground?.constantState shouldBe
            AppCompatResources.getDrawable(context, R.drawable.sudoku_view_item_fg_border_bottom)?.constantState
    }

    @Test
    fun `a right border is drawn for a column block boundary`() {
        val fieldView = inflatedFieldView(PLAIN_INDEX)

        fieldView.foreground?.constantState shouldBe
            AppCompatResources.getDrawable(context, R.drawable.sudoku_view_item_fg_border_right)?.constantState
    }

    @Test
    fun `a bottom-right corner border is drawn where a row and column boundary meet`() {
        val fieldView = inflatedFieldView(ERROR_INDEX)

        fieldView.foreground?.constantState shouldBe
            AppCompatResources.getDrawable(context, R.drawable.sudoku_view_item_fg_border_bottom_right)?.constantState
    }

    @Test
    fun `background is transparent for an uncolored cell with no highlighting`() {
        val fieldView = inflatedFieldView(GIVEN_INDEX)

        (fieldView.background as ColorDrawable).color shouldBe Color.TRANSPARENT
    }

    @Test
    fun `background uses the normal control color for a colored block cell`() {
        val fieldView = inflatedFieldView(COLORED_INDEX)

        (fieldView.background as ColorDrawable).color shouldBe context.getColor(R.color.control_color_normal)
    }

    @Test
    fun `a cell colored only via its column half also gets the normal control color`() {
        val fieldView = inflatedFieldView(COLORED_BY_COLUMN_INDEX)

        (fieldView.background as ColorDrawable).color shouldBe context.getColor(R.color.control_color_normal)
    }

    @Test
    fun `a cell where both the row and column halves match is not colored`() {
        val fieldView = inflatedFieldView(UNCOLORED_BOTH_HALVES_INDEX)

        (fieldView.background as ColorDrawable).color shouldBe Color.TRANSPARENT
    }

    @Test
    fun `isSelected overrides the colored background`() {
        val fieldView = inflatedFieldView(COLORED_INDEX)

        fieldView.isSelected = true
        fieldView.setBackground()

        (fieldView.background as ColorDrawable).color shouldBe context.getColor(R.color.control_color_selected)
    }

    @Test
    fun `isSelected takes priority over isHighlightedNumber`() {
        val fieldView = inflatedFieldView(COLORED_INDEX)

        fieldView.isSelected = true
        fieldView.isHighlightedNumber = true
        fieldView.setBackground()

        (fieldView.background as ColorDrawable).color shouldBe context.getColor(R.color.control_color_selected)
    }

    @Test
    fun `isHighlightedNumber overrides the colored background`() {
        val fieldView = inflatedFieldView(COLORED_INDEX)

        fieldView.isHighlightedNumber = true
        fieldView.setBackground()

        (fieldView.background as ColorDrawable).color shouldBe context.getColor(R.color.control_color_highlighted_number)
    }

    @Test
    fun `isHighlighted overrides the colored background`() {
        val fieldView = inflatedFieldView(COLORED_INDEX)

        fieldView.isHighlighted = true
        fieldView.setBackground()

        (fieldView.background as ColorDrawable).color shouldBe context.getColor(R.color.control_color_highlighted)
    }

    @SuppressLint("PrivateResource")
    @Test
    fun `a field error takes priority over every other background state`() {
        val fieldView = inflatedFieldView(ERROR_INDEX)

        fieldView.isSelected = true
        fieldView.isHighlighted = true
        fieldView.isHighlightedNumber = true
        fieldView.setBackground()

        (fieldView.background as ColorDrawable).color shouldBe context.getColor(androidx.appcompat.R.color.sesl_error_color)
    }

    @Test
    fun `a field bound before its layout inflates shows the value once the layout is ready`() {
        val fieldView = FieldView(context)
        FrameLayout(context).addView(fieldView)
        fieldView.init(board, PLAIN_INDEX, adapter)

        awaitMainLooperUntil { fieldView.fieldViewValue != null }

        fieldView.fieldViewValue?.text.toString() shouldBe "2"
    }

    @Test
    fun `update shows the value and text for a filled field`() {
        val fieldView = inflatedFieldView(PLAIN_INDEX)

        fieldView.fieldViewValue?.text.toString() shouldBe "2"
        fieldView.fieldViewValue?.isVisible shouldBe true
    }

    @Test
    fun `update hides the value text for an empty field`() {
        val fieldView = inflatedFieldView(COLORED_INDEX)

        fieldView.fieldViewValue?.text.toString() shouldBe ""
        fieldView.fieldViewValue?.isVisible shouldBe false
    }

    @Test
    fun `update uses the hint text color for a hint field`() {
        val fieldView = inflatedFieldView(HINT_INDEX)

        fieldView.fieldViewValue?.currentTextColor shouldBe context.getColor(R.color.field_hint_text_color)
    }

    @Test
    fun `update uses the given text color for a given field`() {
        val fieldView = inflatedFieldView(GIVEN_INDEX)

        fieldView.fieldViewValue?.currentTextColor shouldBe context.getColor(R.color.field_given_text_color)
    }

    @Test
    fun `update uses the user input text color for a plain field`() {
        val fieldView = inflatedFieldView(PLAIN_INDEX)

        fieldView.fieldViewValue?.currentTextColor shouldBe context.getColor(R.color.field_userinput_text_color)
    }

    @Test
    fun `updateNotes shows the joined notes and hides the view when notes are cleared`() {
        val fieldView = inflatedFieldView(COLORED_INDEX)
        val notesView = fieldView.findViewById<TextView>(R.id.itemNotes)
        notesView.isVisible shouldBe false

        fieldView.update(board.with(COLORED_INDEX, Cell(solutionFor(COLORED_INDEX), notes = NoteMask.of(1, 2))))

        notesView.isVisible shouldBe true
        notesView.text.toString() shouldBe "12"

        fieldView.update(board)

        notesView.isVisible shouldBe false
    }

    @Test
    fun `updateNotes sets top-center gravity for a last-row, first-column cell`() {
        val fieldView = inflatedFieldView(CORNER_NOTES_INDEX)
        val notesView = fieldView.findViewById<TextView>(R.id.itemNotes)

        fieldView.update(board)

        notesView.gravity shouldBe (Gravity.TOP or Gravity.CENTER_HORIZONTAL)
    }

    @Test
    fun `updateNotes keeps the default gravity for a non-corner cell`() {
        val fieldView = inflatedFieldView(PLAIN_INDEX)
        val notesView = fieldView.findViewById<TextView>(R.id.itemNotes)

        fieldView.update(board)

        notesView.gravity shouldNotBe (Gravity.TOP or Gravity.CENTER_HORIZONTAL)
    }

    @Test
    fun `a click reports its index`() {
        val fieldView = inflatedFieldView(PLAIN_INDEX)

        fieldView.performClick()

        clicked shouldBe listOf(PLAIN_INDEX)
    }

    @Test
    fun `a long click reports its index and clears the selection state when it is consumed`() {
        val fieldView = inflatedFieldView(PLAIN_INDEX)
        fieldView.isSelected = true
        fieldView.isHighlightedNumber = true

        val consumed = fieldView.performLongClick()

        consumed shouldBe true
        longClicked shouldBe listOf(PLAIN_INDEX)
        fieldView.isSelected shouldBe false
        fieldView.isHighlightedNumber shouldBe false
    }

    @Test
    fun `a long click that is not consumed leaves the selection state untouched`() {
        val fieldView = inflatedFieldView(GIVEN_INDEX)
        longClickConsumed = false
        fieldView.isSelected = true
        fieldView.isHighlightedNumber = true

        val consumed = fieldView.performLongClick()

        consumed shouldBe false
        longClicked shouldBe listOf(GIVEN_INDEX)
        fieldView.isSelected shouldBe true
        fieldView.isHighlightedNumber shouldBe true
    }

    @Test
    fun `a completed board sets no click listeners`() {
        val completed = Board(SIZE, board.cells.map { it.copy(value = it.solution) })
        val fieldView = FieldView(context)
        FrameLayout(context).addView(fieldView)
        awaitMainLooperUntil { fieldView.fieldViewValue != null }
        fieldView.init(completed, PLAIN_INDEX, adapter)

        fieldView.performClick()

        clicked shouldBe emptyList()
    }

    @Test
    fun `init before the async inflate completes returns early without touching the view`() {
        val fieldView = FieldView(context)
        FrameLayout(context).addView(fieldView)

        fieldView.init(board, PLAIN_INDEX, adapter)

        fieldView.foreground.shouldBeNull()
        fieldView.cell.value shouldBe 2

        awaitMainLooperUntil { fieldView.fieldViewValue != null }
        fieldView.fieldViewValue?.text.toString() shouldBe "2"
    }

    @Test
    fun `update called before the async inflate completes is a safe no-op on the text views`() {
        val fieldView = FieldView(context)
        FrameLayout(context).addView(fieldView)
        fieldView.init(board, PLAIN_INDEX, adapter)
        fieldView.fieldViewValue.shouldBeNull()

        fieldView.update(board)

        fieldView.fieldViewValue.shouldBeNull()
    }
}
