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
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.Highlighting

class SudokuViewAdapter(
    private val context: Context,
    board: Board,
    internal val onFieldClicked: (index: Int) -> Unit,
    internal val onFieldLongClicked: (index: Int) -> Boolean,
    private val onHighlightingUsed: (Highlighting) -> Unit,
) : RecyclerView.Adapter<SudokuViewAdapter.ViewHolder>() {
    var board: Board = board
        private set
    val fieldViews: MutableList<FieldView> = MutableList(itemCount) { FieldView(context) }
    private var selectedField: Int? = null

    override fun getItemCount(): Int = board.cells.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(FieldView(context))

    override fun onBindViewHolder(
        holder: ViewHolder,
        index: Int,
    ) {
        fieldViews[index] = holder.itemView as FieldView
        (holder.itemView as FieldView).init(board, index, this)
    }

    fun render(
        board: Board,
        changed: Set<Int>,
    ) {
        this.board = board
        changed.forEach { fieldViews[it].update(board) }
    }

    fun selectFieldView(
        position: Int?,
        highlightNeighbors: Boolean,
        highlightNumber: Boolean,
    ) {
        if (position != selectedField && highlightNeighbors) {
            onHighlightingUsed(Highlighting.REGIONAL)
            selectedField = position
            fieldViews.forEach {
                it.isHighlighted = false
                it.isHighlightedNumber = false
            }
            if (position != null && board[position].origin == CellOrigin.PLAYER) {
                (board.peers(position) + position).forEach { fieldViews[it].isHighlighted = true }
            }
        }

        fieldViews.forEach { it.isSelected = false }
        if (position != null) {
            fieldViews[position].isSelected = true
            if (highlightNumber) highlightNumber(board[position].value)
        } else if (highlightNumber) {
            highlightNumber(null)
        }
        fieldViews.forEach { it.setBackground() }
    }

    fun highlightNumber(number: Int?) {
        if (number == null) {
            fieldViews.forEach {
                it.isHighlightedNumber = false
                it.setBackground()
            }
        } else {
            onHighlightingUsed(Highlighting.NUMBER)
            fieldViews.forEachIndexed { index, fieldView ->
                fieldView.isHighlightedNumber = board[index].value == number
                fieldView.setBackground()
            }
        }
    }

    class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView)
}
