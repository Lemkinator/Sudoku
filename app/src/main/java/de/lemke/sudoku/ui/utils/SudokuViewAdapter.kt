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
import de.lemke.sudoku.domain.model.Sudoku

class SudokuViewAdapter(private val context: Context, private val sudoku: Sudoku) : RecyclerView.Adapter<SudokuViewAdapter.ViewHolder>() {
    val fieldViews: MutableList<FieldView?> = MutableList(itemCount) { FieldView(context) }
    private var selectedField: Int? = null

    override fun getItemCount(): Int = sudoku.itemCount

    fun updateFieldView(position: Int) = fieldViews[position]?.update()

    fun selectFieldView(
        position: Int?,
        highlightNeighbors: Boolean,
        highlightNumber: Boolean,
    ) {
        if (position != selectedField && highlightNeighbors) {
            sudoku.regionalHighlightingUsed = true
            selectedField = position
            fieldViews.forEach {
                it?.isHighlighted = false
                it?.isHighlightedNumber = false
            }
            if (position != null && fieldViews[position]?.field?.hint == false && fieldViews[position]?.field?.given == false) {
                sudoku.getNeighbors(position).map { it.position.index }.forEach { fieldViews[it]?.isHighlighted = true }
            }
        }

        fieldViews.forEach { it?.isSelected = false }
        if (position != null) {
            fieldViews[position]?.isSelected = true
            if (highlightNumber) highlightNumber(fieldViews[position]?.field?.value)
        } else if (highlightNumber) {
            highlightNumber(null)
        }
        fieldViews.forEach { it?.setBackground() }
    }

    fun highlightNumber(number: Int?) {
        if (number == null) {
            fieldViews.forEach {
                it?.isHighlightedNumber = false
                it?.setBackground()
            }
        } else {
            sudoku.numberHighlightingUsed = true
            fieldViews.forEach {
                it?.isHighlightedNumber = it.field.value == number
                it?.setBackground()
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(FieldView(context))

    override fun onBindViewHolder(
        holder: ViewHolder,
        index: Int,
    ) {
        fieldViews[index] = holder.itemView as FieldView
        (holder.itemView as FieldView).init(sudoku, index, this)
    }

    class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView)
}
