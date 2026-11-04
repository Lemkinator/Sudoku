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

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import androidx.core.view.isVisible
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.toSudokuDigitChar
import de.lemke.sudoku.domain.model.toSudokuString

class FieldView(context: Context) : LinearLayout(context) {
    var fieldViewValue: TextView? = null
    private var fieldViewNotes: TextView? = null
    private var fieldViewContainer: View? = null
    private lateinit var board: Board
    private lateinit var adapter: SudokuViewAdapter
    lateinit var cell: Cell
    lateinit var position: Position
    private var isColored = false
    var isHighlighted = false
    var isHighlightedNumber = false

    init {
        val asyncLayoutInflater = AsyncLayoutInflater(context)
        asyncLayoutInflater.inflate(R.layout.field_view, this) { view, _, _ ->
            layoutTransition = LayoutTransition()
            addView(view)
            fieldViewValue = findViewById(R.id.itemNumber)
            fieldViewNotes = findViewById(R.id.itemNotes)
            fieldViewContainer = findViewById(R.id.itemContainer)

            if (::board.isInitialized) init(board, position.index, adapter)
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }

    fun init(
        board: Board,
        index: Int,
        adapter: SudokuViewAdapter,
    ) {
        this.adapter = adapter
        this.board = board
        position = board.position(index)
        cell = board[index]
        if (fieldViewContainer == null) return
        val blockSize = board.size.blockSize
        val lastLine = board.size.value - 1

        when {
            position.row % blockSize == blockSize - 1 && position.row != lastLine -> {
                foreground =
                    AppCompatResources.getDrawable(
                        context,
                        if (position.column % blockSize == blockSize - 1 && position.column != lastLine) {
                            R.drawable.sudoku_view_item_fg_border_bottom_right
                        } else {
                            R.drawable.sudoku_view_item_fg_border_bottom
                        },
                    )
            }

            position.column % blockSize == blockSize - 1 && position.column != lastLine -> {
                foreground = AppCompatResources.getDrawable(context, R.drawable.sudoku_view_item_fg_border_right)
            }
        }
        val rm = position.row % (blockSize * 2)
        val cm = position.column % (blockSize * 2)
        isColored = rm >= blockSize != cm >= blockSize
        update(board)
    }

    fun update(board: Board) {
        this.board = board
        cell = board[position.index]
        fieldViewValue?.text = cell.value.toSudokuString()
        fieldViewValue?.isVisible = cell.value != null
        fieldViewValue?.setTextColor(
            context.getColor(
                when (cell.origin) {
                    CellOrigin.HINT -> R.color.field_hint_text_color
                    CellOrigin.GIVEN -> R.color.field_given_text_color
                    CellOrigin.PLAYER -> R.color.field_userinput_text_color
                },
            ),
        )
        updateNotes()
        setBackground()
        if (!board.completed) {
            setOnClickListener { adapter.onFieldClicked(position.index) }
            setOnLongClickListener {
                if (adapter.onFieldLongClicked(position.index)) {
                    isSelected = false
                    isHighlightedNumber = false
                    setBackground()
                    return@setOnLongClickListener true
                } else {
                    return@setOnLongClickListener false
                }
            }
        }
    }

    private fun updateNotes() {
        fieldViewNotes?.isVisible = !cell.notes.isEmpty()
        fieldViewNotes?.text =
            cell.notes.digits
                .map { it.toSudokuDigitChar() }
                .joinToString("")
        if (position.row == board.size.value - 1 && (position.column == 0 || position.column == board.size.value - 1)) {
            fieldViewNotes?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
    }

    fun setBackground() = setBackgroundColor(getCurrentBackgroundColor())

    @SuppressLint("PrivateResource")
    private fun getCurrentBackgroundColor(): Int =
        if (cell.error) {
            context.getColor(androidx.appcompat.R.color.sesl_error_color)
        } else if (isSelected) {
            context.getColor(R.color.control_color_selected)
        } else if (isHighlightedNumber) {
            context.getColor(R.color.control_color_highlighted_number)
        } else if (isHighlighted) {
            context.getColor(R.color.control_color_highlighted)
        } else if (isColored) {
            context.getColor(R.color.control_color_normal)
        } else {
            Color.TRANSPARENT
        }
}
