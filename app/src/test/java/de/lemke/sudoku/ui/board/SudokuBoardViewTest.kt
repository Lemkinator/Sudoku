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

package de.lemke.sudoku.ui.board

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.NoteMask
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.time.Duration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.oneuiproject.oneui.design.R as designR

private const val SIDE = 900
private const val COLOR_TOLERANCE = 2
private const val FRAMES_INTO_ANIMATION = 6

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SudokuBoardViewTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var view: SudokuBoardView
    private var clicked: Int? = null
    private var longClicked: Int? = null

    @Before
    fun setup() {
        view = SudokuBoardView(context)
        view.onCellClick = { clicked = it }
        view.onCellLongClick = { longClicked = it }
        view.render = BoardRender(testSudoku(SudokuSize.NINE).board)
        layout()
    }

    private fun layout(target: SudokuBoardView = view) {
        target.measure(MeasureSpec.makeMeasureSpec(SIDE, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(SIDE, MeasureSpec.EXACTLY))
        target.layout(0, 0, SIDE, SIDE)
    }

    private fun touch(
        action: Int,
        x: Float,
        y: Float,
    ) {
        val now = SystemClock.uptimeMillis()
        MotionEvent.obtain(now, now, action, x, y, 0).also {
            view.dispatchTouchEvent(it)
            it.recycle()
        }
    }

    private fun tap(
        x: Float,
        y: Float,
    ) {
        touch(MotionEvent.ACTION_DOWN, x, y)
        touch(MotionEvent.ACTION_UP, x, y)
    }

    private fun longPress(
        x: Float,
        y: Float,
    ) {
        touch(MotionEvent.ACTION_DOWN, x, y)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        touch(MotionEvent.ACTION_UP, x, y)
    }

    private fun draw(target: SudokuBoardView = view): Bitmap =
        Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888).also { target.draw(Canvas(it)) }

    private fun drawFresh(render: BoardRender): Bitmap {
        val fresh = SudokuBoardView(context)
        fresh.render = render
        layout(fresh)
        return draw(fresh)
    }

    private val boardColor: Int
        get() {
            val value = TypedValue()
            context.theme.resolveAttribute(android.R.attr.colorBackground, value, true)
            return value.data
        }

    @Test
    fun `a tap at the center reports cell 40`() {
        tap(450f, 450f)

        clicked shouldBe 40
        longClicked.shouldBeNull()
    }

    @Test
    fun `a tap in the cut corner reports nothing`() {
        tap(5f, 5f)

        clicked.shouldBeNull()
    }

    @Test
    fun `a long press reports the cell and no tap`() {
        longPress(150f, 50f)

        longClicked shouldBe 1
        clicked.shouldBeNull()
    }

    @Test
    fun `a long press in the cut corner reports nothing`() {
        longPress(5f, 5f)

        longClicked.shouldBeNull()
    }

    @Test
    fun `a tap before a board is rendered reports nothing`() {
        val bare = SudokuBoardView(context)
        bare.onCellClick = { clicked = it }
        bare.measure(MeasureSpec.makeMeasureSpec(SIDE, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(SIDE, MeasureSpec.EXACTLY))
        bare.layout(0, 0, SIDE, SIDE)
        val now = SystemClock.uptimeMillis()
        bare.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 450f, 450f, 0))
        bare.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 450f, 450f, 0))

        clicked.shouldBeNull()
    }

    @Test
    fun `a view without a click callback survives a tap and a long press`() {
        view.onCellClick = null
        view.onCellLongClick = null

        longPress(450f, 450f)
        tap(450f, 450f)

        clicked.shouldBeNull()
        longClicked.shouldBeNull()
    }

    @Test
    fun `the view measures the largest square`() {
        view.measure(MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(1500, MeasureSpec.AT_MOST))

        view.measuredWidth to view.measuredHeight shouldBe (1080 to 1080)
    }

    @Test
    fun `the view measures the shorter limit when the height is smaller`() {
        view.measure(MeasureSpec.makeMeasureSpec(1080, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(700, MeasureSpec.AT_MOST))

        view.measuredWidth to view.measuredHeight shouldBe (700 to 700)
    }

    @Test
    fun `an unconstrained view measures zero`() {
        view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))

        view.measuredWidth to view.measuredHeight shouldBe (0 to 0)
    }

    @Test
    fun `a scaled transform maps taps through its inverse`() {
        view.transform.setScale(2f, 2f)

        tap(900f, 900f)

        clicked shouldBe 40
    }

    @Test
    fun `the view reports its board geometry after layout`() {
        view.geometry?.size shouldBe SudokuSize.NINE
        view.geometry?.metrics?.side shouldBe SIDE.toFloat()
    }

    @Test
    fun `a view without a size has no geometry`() {
        val bare = SudokuBoardView(context)
        bare.render = BoardRender(testSudoku(SudokuSize.NINE).board)

        bare.geometry.shouldBeNull()
    }

    @Test
    fun `changing the board size rebuilds the geometry`() {
        view.render = BoardRender(testSudoku(SudokuSize.FOUR).board)

        view.geometry?.size shouldBe SudokuSize.FOUR
    }

    @Test
    fun `an equal render keeps the geometry`() {
        val geometry = view.geometry

        view.render = BoardRender(testSudoku(SudokuSize.NINE).board)

        view.geometry shouldBeSameInstanceAs geometry
    }

    @Test
    fun `a new selection or board of the same size keeps the geometry`() {
        val board = testSudoku(SudokuSize.NINE).board
        val geometry = view.geometry

        view.render = BoardRender(board, selected = 3)
        view.render = BoardRender(board.with(0, Cell(board[0].solution, value = board[0].solution)), selected = 3)

        view.geometry shouldBeSameInstanceAs geometry
    }

    @Test
    fun `a board change draws like a fresh view with the same board`() {
        val board = testSudoku(SudokuSize.FOUR).board
        view.render = BoardRender(board)
        val before = draw()
        val changed = BoardRender(board.with(1, Cell(board[1].solution, value = board[1].solution)))

        view.render = changed

        val after = draw()
        after.sameAs(before).shouldBeFalse()
        after.sameAs(drawFresh(changed)).shouldBeTrue()
    }

    @Test
    fun `a notes-only change draws like a fresh view with the same notes`() {
        val board = testSudoku(SudokuSize.FOUR).board
        view.render = BoardRender(board)
        val before = draw()
        val noted = BoardRender(board.with(1, Cell(board[1].solution, notes = NoteMask.of(1, 2))))

        view.render = noted

        val after = draw()
        after.sameAs(before).shouldBeFalse()
        after.sameAs(drawFresh(noted)).shouldBeTrue()
    }

    @Test
    fun `removing the render clears the geometry and draws nothing`() {
        view.render = null

        view.geometry.shouldBeNull()
        draw().getPixel(450, 450) shouldBe Color.TRANSPARENT
    }

    @Test
    fun `the cut corner stays transparent and the board is filled`() {
        val bitmap = draw()

        bitmap.getPixel(2, 2) shouldBe Color.TRANSPARENT
        bitmap.getPixel(130, 130) shouldBe boardColor
    }

    @Test
    fun `a selected cell takes the selected tone`() {
        view.render = BoardRender(testSudoku(SudokuSize.NINE).board, selected = 40)

        val selected = ColorUtils.compositeColors(context.getColor(R.color.control_color_selected), boardColor)
        draw().getPixel(404, 404).shouldBeCloseToColor(selected)
    }

    @Test
    fun `a wrong value takes the error tone`() {
        val board = testSudoku(SudokuSize.FOUR).board
        view.render = BoardRender(board.with(2, Cell(board[2].solution, value = 1)))

        val error = context.getColor(androidx.appcompat.R.color.sesl_error_color)
        draw().getPixel(470, 20) shouldBe ColorUtils.compositeColors(error, boardColor)
    }

    @Test
    fun `thin lines and box lines use the divider color`() {
        view.render = BoardRender(testSudoku(SudokuSize.FOUR).board)
        val divider = ColorUtils.compositeColors(context.getColor(designR.color.oui_des_list_divider_color), boardColor)

        val bitmap = draw()

        bitmap.getPixel(225, 100) shouldBe divider
        bitmap.getPixel(450, 100) shouldBe divider
        bitmap.getPixel(449, 100) shouldBe divider
        bitmap.getPixel(340, 100) shouldNotBe divider
    }

    @Test
    fun `an emphasized note draws in the accent color and a plain note does not`() {
        val board = testSudoku(SudokuSize.FOUR).board
        val withNote = board.with(1, Cell(board[1].solution, notes = NoteMask.of(3)))
        val accent = context.getColor(R.color.primary_color_themed)

        view.render = BoardRender(withNote)
        val plain = draw().countPixels(accent)
        view.render = BoardRender(withNote, highlightedDigit = 3)
        val emphasized = draw().countPixels(accent)

        (emphasized > plain).shouldBeTrue()
    }

    @Test
    fun `a value draws in the color of its origin`() {
        val given = context.getColor(R.color.field_given_text_color)
        val hint = context.getColor(R.color.field_hint_text_color)
        val board = testSudoku(SudokuSize.FOUR).board

        val bitmap = draw(board)

        (bitmap.countPixels(given) > 0).shouldBeTrue()
        view.render = BoardRender(board.with(1, Cell(board[1].solution, value = board[1].solution, origin = CellOrigin.HINT)))
        (draw().countPixels(hint) > 0).shouldBeTrue()
    }

    private fun draw(board: Board): Bitmap {
        view.render = BoardRender(board)
        return draw()
    }

    private fun Int.shouldBeCloseToColor(expected: Int) {
        listOf(Color.red(this), Color.green(this), Color.blue(this), Color.alpha(this))
            .zip(
                listOf(Color.red(expected), Color.green(expected), Color.blue(expected), Color.alpha(expected)),
            ).forEach { (actual, wanted) -> actual.shouldBeInRange(wanted - COLOR_TOLERANCE..wanted + COLOR_TOLERANCE) }
    }

    private fun Bitmap.countPixels(color: Int): Int {
        var count = 0
        for (x in 0 until width) for (y in 0 until height) if (getPixel(x, y) == color) count++
        return count
    }

    @Test
    fun `an animation moves the values and ends at rest`() {
        val rest = draw()
        var ended = 0

        view.animateCells(listOf(listOf(0, 3)), CellAnimation.WAVE) { ended++ }
        repeat(FRAMES_INTO_ANIMATION) { shadowOf(Looper.getMainLooper()).runOneTask() }
        val moving = draw()
        ended shouldBe 0
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        moving.sameAs(rest).shouldBeFalse()
        draw().sameAs(rest).shouldBeTrue()
        ended shouldBe 1
    }

    @Test
    fun `a pulse grows the whole cell and ends at rest`() {
        val rest = draw()
        var ended = 0

        view.animateCells(listOf(listOf(40)), CellAnimation.PULSE) { ended++ }
        repeat(FRAMES_INTO_ANIMATION) { shadowOf(Looper.getMainLooper()).runOneTask() }
        val moving = draw()
        ended shouldBe 0
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))

        moving.sameAs(rest).shouldBeFalse()
        draw().sameAs(rest).shouldBeTrue()
        ended shouldBe 1
    }

    @Test
    fun `an animation draws nothing different before its first frame`() {
        val rest = draw()

        view.animateCells(listOf(listOf(0, 3)), CellAnimation.WAVE)
        val started = draw()
        view.animateCells(listOf(listOf(40)), CellAnimation.PULSE)
        val pulseStarted = draw()

        started.sameAs(rest).shouldBeTrue()
        pulseStarted.sameAs(rest).shouldBeTrue()
    }

    @Test
    fun `removing the render during an animation lets it end quietly`() {
        var ended = 0
        view.animateCells(listOf(listOf(0)), CellAnimation.PULSE) { ended++ }

        view.render = null
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))

        ended shouldBe 1
    }

    @Test
    fun `a view without a size draws nothing`() {
        val bare = SudokuBoardView(context)
        bare.render = BoardRender(testSudoku(SudokuSize.NINE).board)
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        bare.draw(Canvas(bitmap))

        bitmap.getPixel(5, 5) shouldBe Color.TRANSPARENT
    }

    @Test
    fun `notes on a cell that holds a value stay hidden`() {
        val board = testSudoku(SudokuSize.FOUR).board
        val plain = board.with(1, Cell(board[1].solution, value = board[1].solution))
        val noted = board.with(1, Cell(board[1].solution, value = board[1].solution, notes = NoteMask.of(1, 2)))

        view.render = BoardRender(plain)
        val without = draw()
        view.render = BoardRender(noted)

        draw().sameAs(without).shouldBeTrue()
    }

    @Test
    fun `a focused view leaves the arrow keys to the focus search`() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val focusable = SudokuBoardView(activity)
        val board = testSudoku(SudokuSize.NINE).board
        focusable.render = BoardRender(board, selected = 4)
        focusable.onCellClick = { clicked = it }
        activity.setContentView(focusable, ViewGroup.LayoutParams(SIDE, SIDE))

        focusable.requestFocusFromTouch().shouldBeTrue()

        listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
        ).forEach { keyCode ->
            focusable.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode)).shouldBeFalse()
        }
        clicked.shouldBeNull()
        focusable.render?.selected shouldBe 4
    }

    @Test
    fun `an animation without steps ends at once`() {
        var ended = 0

        view.animateCells(emptyList(), CellAnimation.PULSE) { ended++ }

        ended shouldBe 1
    }

    @Test
    fun `animating without a board does nothing`() {
        var ended = 0
        view.render = null

        view.animateCells(listOf(listOf(0)), CellAnimation.PULSE) { ended++ }

        ended shouldBe 0
    }

    @Test
    fun `animating a view that was never measured ends without drawing a frame`() {
        var ended = 0
        val unmeasured = SudokuBoardView(context)
        unmeasured.render = BoardRender(testSudoku(SudokuSize.NINE).board)

        unmeasured.animateCells(listOf(listOf(0)), CellAnimation.PULSE) { ended++ }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))

        unmeasured.geometry.shouldBeNull()
        unmeasured.isAnimating.shouldBeFalse()
        ended shouldBe 1
    }

    @Test
    fun `detaching cancels a running animation without ending it`() {
        var ended = 0
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val parent = FrameLayout(activity)
        activity.setContentView(parent)
        val view = SudokuBoardView(activity)
        view.render = BoardRender(testSudoku(SudokuSize.NINE).board)
        parent.addView(view, SIDE, SIDE)

        view.animateCells(listOf(listOf(0)), CellAnimation.PULSE) { ended++ }
        parent.removeView(view)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        ended shouldBe 0
    }

    @Test
    fun `two animations on the same cells both finish`() {
        var ended = 0

        view.animateCells(listOf(listOf(40)), CellAnimation.WAVE) { ended++ }
        view.animateCells(listOf(listOf(40, 41)), CellAnimation.BOARD_WAVE) { ended++ }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        ended shouldBe 2
    }
}
