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

import android.util.TypedValue
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.github.takahirom.roborazzi.captureRoboImage
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.NoteMask
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.domain.model.tutorialSudoku
import de.lemke.sudoku.testSudoku
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val BOARD_DP = 360f

@RunWith(RobolectricTestRunner::class)
// sdk = [36]: Robolectric's SDK 37 shadows lack InputManager.getInstance().
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SudokuBoardViewScreenshotTest {
    private fun capture(
        render: BoardRender,
        fileName: String,
    ) {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val side = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BOARD_DP, activity.resources.displayMetrics).toInt()
        val view = SudokuBoardView(activity)
        view.render = render
        val frame = FrameLayout(activity)
        frame.addView(view, FrameLayout.LayoutParams(side, side))
        activity.setContentView(frame, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        view.captureRoboImage("src/test/screenshots/$fileName")
    }

    private fun fresh(size: SudokuSize): Board = if (size == SudokuSize.NINE) tutorialSudoku().board else testSudoku(size).board

    private fun Board.withNotes(vararg notes: Pair<Int, NoteMask>): Board =
        notes.fold(this) { board, (index, mask) -> board.with(index, Cell(board[index].solution, notes = mask)) }

    private fun noteCounts(
        size: SudokuSize,
        cells: List<Int>,
        counts: List<Int>,
    ): Board =
        fresh(size).withNotes(*cells.zip(counts) { index, count -> index to NoteMask.of(*(1..count).toList().toIntArray()) }.toTypedArray())

    private fun Board.firstEmpty(count: Int) = cells.indices.filter { this[it].value == null }.take(count)

    @Test
    fun board_4x4() = capture(BoardRender(fresh(SudokuSize.FOUR)), "board_4x4.png")

    @Test
    fun board_9x9() = capture(BoardRender(fresh(SudokuSize.NINE)), "board_9x9.png")

    @Test
    fun board_16x16() = capture(BoardRender(fresh(SudokuSize.SIXTEEN)), "board_16x16.png")

    @Test
    fun board_4x4_note() {
        val board = fresh(SudokuSize.FOUR)
        capture(BoardRender(board.withNotes(board.firstEmpty(1).single() to NoteMask.of(2))), "board_4x4_note.png")
    }

    @Test
    fun board_9x9_notes() = capture(BoardRender(notes9()), "board_9x9_notes.png")

    @Test
    fun board_16x16_notes() {
        val board = fresh(SudokuSize.SIXTEEN)
        val cells = listOf(0) + board.firstEmpty(4).filter { it != 0 }.take(3)
        capture(BoardRender(noteCounts(SudokuSize.SIXTEEN, cells, listOf(1, 4, 9, 16))), "board_16x16_notes.png")
    }

    @Test
    fun board_9x9_selected_digit_notes() = capture(BoardRender(notes9(), highlightedDigit = FIVE), "board_9x9_selected_digit_notes.png")

    @Test
    fun board_4x4_selected_digit_notes() {
        val board = fresh(SudokuSize.FOUR)
        val cells = board.firstEmpty(3)
        val noted = board.withNotes(cells[0] to NoteMask.of(2), cells[1] to NoteMask.of(1, 2), cells[2] to NoteMask.of(1, 2, 3, 4))
        capture(BoardRender(noted, highlightedDigit = 2), "board_4x4_selected_digit_notes.png")
    }

    @Test
    fun board_9x9_errors() {
        val board = fresh(SudokuSize.NINE)
        val cells = board.firstEmpty(2)
        val wrong = cells.fold(board) { b, index -> b.with(index, Cell(b[index].solution, value = b[index].solution % 9 + 1)) }
        capture(BoardRender(wrong), "board_9x9_errors.png")
    }

    @Test
    fun board_16x16_corners() {
        val board = fresh(SudokuSize.SIXTEEN)
        val corners = listOf(0, 1, 16, 14, 15, 31, 224, 240, 241, 239, 254, 255)
        val noted = corners.fold(board) { b, index -> b.with(index, Cell(b[index].solution, notes = NoteMask.of(1, 2, 3, 4, 5))) }
        capture(BoardRender(noted, selected = 0), "board_16x16_corners.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun board_9x9_dark() = capture(BoardRender(notes9()), "board_9x9_dark.png")

    private fun notes9(): Board {
        val board = fresh(SudokuSize.NINE)
        return noteCounts(SudokuSize.NINE, board.firstEmpty(3), listOf(1, 4, 9))
    }

    private companion object {
        const val FIVE = 5
    }
}
