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

import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.Highlighting
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testLevelSudoku
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val GIVEN_INDEX = 0
private const val PLAIN_INDEX = 1

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SudokuViewAdapterTest {
    private lateinit var context: AppCompatActivity
    private lateinit var board: Board
    private lateinit var adapter: SudokuViewAdapter
    private val highlightingsUsed = mutableListOf<Highlighting>()

    @Before
    fun setUp() {
        context = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        board = testLevelSudoku(size = SudokuSize.FOUR).board
        adapter =
            SudokuViewAdapter(
                context,
                board,
                onFieldClicked = {},
                onFieldLongClicked = { false },
                onHighlightingUsed = { highlightingsUsed += it },
            )
        for (index in board.cells.indices) {
            val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
            adapter.onBindViewHolder(holder, index)
        }
    }

    private fun awaitInflated(fieldView: FieldView) {
        val deadline = System.currentTimeMillis() + 5_000
        while (fieldView.fieldViewValue == null && System.currentTimeMillis() < deadline) {
            idleMainLooper()
            Thread.sleep(5)
        }
        check(fieldView.fieldViewValue != null) { "FieldView's async inflate did not complete within 5000ms" }
    }

    @Test
    fun `getItemCount matches the board's cell count`() {
        adapter.itemCount shouldBe 16
    }

    @Test
    fun `onCreateViewHolder and onBindViewHolder populate the field view at the bound index`() {
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)

        adapter.onBindViewHolder(holder, 7)

        adapter.fieldViews[7] shouldBe holder.itemView
        adapter.fieldViews[7].position.index shouldBe 7
    }

    @Test
    fun `render refreshes the changed field view's display`() {
        val fieldView = adapter.fieldViews[PLAIN_INDEX]
        awaitInflated(fieldView)

        adapter.render(board.with(PLAIN_INDEX, board[PLAIN_INDEX].copy(value = 3)))

        fieldView.fieldViewValue?.text.toString() shouldBe "3"
    }

    @Test
    fun `render refreshes every field view whose cell differs from the board it replaces`() {
        val first = adapter.fieldViews[PLAIN_INDEX]
        val second = adapter.fieldViews[PLAIN_INDEX + 1]
        awaitInflated(first)
        awaitInflated(second)

        adapter.render(board.with(PLAIN_INDEX, board[PLAIN_INDEX].copy(value = 3)))
        adapter.render(
            adapter.board
                .with(PLAIN_INDEX + 1, adapter.board[PLAIN_INDEX + 1].copy(value = 2))
                .with(PLAIN_INDEX, adapter.board[PLAIN_INDEX].copy(value = 4)),
        )

        first.fieldViewValue?.text.toString() shouldBe "4"
        second.fieldViewValue?.text.toString() shouldBe "2"
    }

    @Test
    fun `render leaves a field view that is not changed on its previous cell`() {
        val fieldView = adapter.fieldViews[GIVEN_INDEX]
        val previous = fieldView.cell

        adapter.render(board.with(PLAIN_INDEX, board[PLAIN_INDEX].copy(value = 3)))

        fieldView.cell shouldBe previous
    }

    @Test
    fun `selecting a non-given, non-hint field with highlightNeighbors true highlights its row, column and block`() {
        val expectedNeighbors = board.peers(PLAIN_INDEX) + PLAIN_INDEX

        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)

        highlightingsUsed shouldContain Highlighting.REGIONAL
        for (i in board.cells.indices) {
            adapter.fieldViews[i].isHighlighted shouldBe (i in expectedNeighbors)
        }
        adapter.fieldViews[PLAIN_INDEX].isSelected shouldBe true
    }

    @Test
    fun `selecting a given field does not highlight neighbors even when highlightNeighbors is true`() {
        adapter.selectFieldView(GIVEN_INDEX, highlightNeighbors = true, highlightNumber = false)

        highlightingsUsed shouldContain Highlighting.REGIONAL
        for (i in board.cells.indices) {
            adapter.fieldViews[i].isHighlighted shouldBe false
        }
        adapter.fieldViews[GIVEN_INDEX].isSelected shouldBe true
    }

    @Test
    fun `selecting a hint field does not highlight neighbors even when highlightNeighbors is true`() {
        val solution = board[PLAIN_INDEX].solution
        adapter.render(board.with(PLAIN_INDEX, Cell(solution, solution, CellOrigin.HINT)))

        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)

        highlightingsUsed shouldContain Highlighting.REGIONAL
        for (i in board.cells.indices) {
            adapter.fieldViews[i].isHighlighted shouldBe false
        }
        adapter.fieldViews[PLAIN_INDEX].isSelected shouldBe true
    }

    @Test
    fun `selecting a field with highlightNeighbors false does not touch highlighting state`() {
        val expectedNeighbors = board.peers(PLAIN_INDEX) + PLAIN_INDEX
        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)
        highlightingsUsed shouldContain Highlighting.REGIONAL

        adapter.selectFieldView(GIVEN_INDEX, highlightNeighbors = false, highlightNumber = false)

        for (i in board.cells.indices) {
            adapter.fieldViews[i].isHighlighted shouldBe (i in expectedNeighbors)
        }
        adapter.fieldViews[GIVEN_INDEX].isSelected shouldBe true
    }

    @Test
    fun `selecting null position with highlightNumber true clears region and number highlighting`() {
        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)
        adapter.fieldViews[2].isHighlightedNumber = true

        adapter.selectFieldView(null, highlightNeighbors = true, highlightNumber = true)

        for (i in board.cells.indices) {
            adapter.fieldViews[i].isHighlighted shouldBe false
            adapter.fieldViews[i].isSelected shouldBe false
            adapter.fieldViews[i].isHighlightedNumber shouldBe false
        }
    }

    @Test
    fun `selecting null position with highlightNumber false does not touch number highlighting`() {
        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)
        adapter.fieldViews[2].isHighlightedNumber = true

        adapter.selectFieldView(null, highlightNeighbors = false, highlightNumber = false)

        adapter.fieldViews[2].isHighlightedNumber shouldBe true
    }

    @Test
    fun `re-selecting the same position does not recompute neighbor highlighting`() {
        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)
        val neighborIndex = board.peers(PLAIN_INDEX).first()
        adapter.fieldViews[neighborIndex].isHighlighted = false

        adapter.selectFieldView(PLAIN_INDEX, highlightNeighbors = true, highlightNumber = false)

        adapter.fieldViews[neighborIndex].isHighlighted shouldBe false
    }

    @Test
    fun `highlightNumber null clears number highlighting for every field`() {
        adapter.highlightNumber(3)

        adapter.highlightNumber(null)

        highlightingsUsed shouldContain Highlighting.NUMBER
        for (i in board.cells.indices) {
            adapter.fieldViews[i].isHighlightedNumber shouldBe false
        }
    }

    @Test
    fun `highlightNumber marks only fields whose value matches`() {
        val matching =
            board.cells.indices
                .filter { board[it].value == 3 }
                .toSet()
        matching.shouldNotBeEmpty()

        adapter.highlightNumber(3)

        highlightingsUsed shouldContain Highlighting.NUMBER
        for (i in board.cells.indices) {
            adapter.fieldViews[i].isHighlightedNumber shouldBe (i in matching)
        }
    }
}
