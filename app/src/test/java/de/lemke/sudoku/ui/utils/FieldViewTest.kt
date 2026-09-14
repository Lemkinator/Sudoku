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
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import java.util.Timer
import kotlin.math.sqrt
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val SIZE = 4
private const val GIVEN_INDEX = 0 // row0,col0: no block-boundary border, not colored
private const val PLAIN_INDEX = 1 // row0,col1: right border, not colored, filled with the correct value
private const val HINT_INDEX = 4 // row1,col0: bottom border, not colored
private const val ERROR_INDEX = 5 // row1,col1: bottom-right corner border, not colored, wrong value
private const val COLORED_INDEX = 8 // row2,col0: no border, colored, empty
private const val CORNER_NOTES_INDEX = 12 // row3,col0: last-row/first-column notes gravity corner
private const val COLORED_BY_COLUMN_INDEX = 2 // row0,col2: colored via the column half only
private const val UNCOLORED_BOTH_HALVES_INDEX = 10 // row2,col2: row and column halves both true, XOR cancels out

private fun solutionFor(index: Int): Int {
    val blockSize = sqrt(SIZE.toDouble()).toInt()
    val row = index / SIZE
    val col = index % SIZE
    return (blockSize * (row % blockSize) + row / blockSize + col) % SIZE + 1
}

private fun fourByFourSudoku(): Sudoku {
    val overrides =
        mapOf(
            GIVEN_INDEX to Field(Position.create(GIVEN_INDEX, SIZE), solution = solutionFor(GIVEN_INDEX), value = 1, given = true),
            PLAIN_INDEX to Field(Position.create(PLAIN_INDEX, SIZE), solution = solutionFor(PLAIN_INDEX), value = 2),
            HINT_INDEX to Field(Position.create(HINT_INDEX, SIZE), solution = solutionFor(HINT_INDEX), value = 3, hint = true),
            ERROR_INDEX to Field(Position.create(ERROR_INDEX, SIZE), solution = solutionFor(ERROR_INDEX), value = 1),
        )
    return Sudoku.create(
        size = SIZE,
        difficulty = Difficulty.VERY_EASY,
        modeLevel = Sudoku.MODE_NORMAL,
        fields =
            MutableList(SIZE * SIZE) { index ->
                overrides[index] ?: Field(Position.create(index, SIZE), solution = solutionFor(index))
            },
    )
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FieldViewTest {
    private lateinit var context: AppCompatActivity
    private lateinit var sudoku: Sudoku

    @Before
    fun setUp() {
        context = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        sudoku = fourByFourSudoku()
    }

    private fun inflatedFieldView(index: Int): FieldView {
        val fieldView = FieldView(context)
        // A real FieldView is always a RecyclerView child; giving it a parent here (as opposed to leaving it
        // detached) matters for performLongClick() — an unconsumed long click falls through to
        // View.showContextMenu(), which NPEs against a null getParent().
        FrameLayout(context).addView(fieldView)
        val deadline = System.currentTimeMillis() + 5_000
        while (fieldView.fieldViewValue == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        fieldView.init(sudoku, index, mockk(relaxed = true))
        return fieldView
    }

    @Test
    fun `no border is drawn for a cell away from any block boundary`() {
        val fieldView = inflatedFieldView(GIVEN_INDEX)

        fieldView.foreground.shouldBeNull()
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

        fieldView.field.notes.add('A')
        fieldView.field.notes.add('B')
        fieldView.update()

        notesView.isVisible shouldBe true
        notesView.text.toString() shouldBe "AB"

        fieldView.field.notes.clear()
        fieldView.update()

        notesView.isVisible shouldBe false
    }

    @Test
    fun `updateNotes sets top-center gravity for a last-row, first-column cell`() {
        val fieldView = inflatedFieldView(CORNER_NOTES_INDEX)
        val notesView = fieldView.findViewById<TextView>(R.id.itemNotes)

        fieldView.update()

        notesView.gravity shouldBe (Gravity.TOP or Gravity.CENTER_HORIZONTAL)
    }

    @Test
    fun `updateNotes keeps the default gravity for a non-corner cell`() {
        val fieldView = inflatedFieldView(PLAIN_INDEX)
        val notesView = fieldView.findViewById<TextView>(R.id.itemNotes)

        fieldView.update()

        notesView.gravity shouldNotBe (Gravity.TOP or Gravity.CENTER_HORIZONTAL)
    }

    @Test
    fun `a long click erases a field's value and clears its selection state`() {
        val fieldView = inflatedFieldView(PLAIN_INDEX)
        sudoku.timer = Timer()
        fieldView.isSelected = true
        fieldView.isHighlightedNumber = true

        val consumed = fieldView.performLongClick()

        consumed shouldBe true
        fieldView.field.value.shouldBeNull()
        fieldView.isSelected shouldBe false
        fieldView.isHighlightedNumber shouldBe false
        sudoku.eraserUsed shouldBe true
        sudoku.timer?.cancel()
    }

    @Test
    fun `a long click on a given field cannot erase and leaves its state untouched`() {
        val fieldView = inflatedFieldView(GIVEN_INDEX)
        sudoku.timer = Timer()
        fieldView.isSelected = true
        fieldView.isHighlightedNumber = true

        val consumed = fieldView.performLongClick()

        consumed shouldBe false
        fieldView.field.value shouldBe 1
        fieldView.isSelected shouldBe true
        fieldView.isHighlightedNumber shouldBe true
        sudoku.timer?.cancel()
    }

    @Test
    fun `init before the async inflate completes returns early without touching the view`() {
        val fieldView = FieldView(context)
        FrameLayout(context).addView(fieldView)

        fieldView.init(sudoku, PLAIN_INDEX, mockk(relaxed = true))

        // fieldViewContainer is still null, so init() returned before configuring the foreground border.
        fieldView.foreground.shouldBeNull()
        fieldView.field.value shouldBe 2

        // The async inflate callback now finds sudoku already initialized and re-invokes init() itself.
        val deadline = System.currentTimeMillis() + 5_000
        while (fieldView.fieldViewValue == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        fieldView.fieldViewValue?.text.toString() shouldBe "2"
    }

    @Test
    fun `update called before the async inflate completes is a safe no-op on the text views`() {
        val fieldView = FieldView(context)
        FrameLayout(context).addView(fieldView)
        fieldView.init(sudoku, PLAIN_INDEX, mockk(relaxed = true))
        fieldView.fieldViewValue.shouldBeNull()

        fieldView.update()

        fieldView.fieldViewValue.shouldBeNull()
    }
}
