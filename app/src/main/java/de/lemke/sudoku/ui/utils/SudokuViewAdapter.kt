package de.lemke.sudoku.ui.utils

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.lemke.sudoku.domain.model.Sudoku

class SudokuViewAdapter(private val context: Context, private val sudoku: Sudoku) : RecyclerView.Adapter<SudokuViewAdapter.ViewHolder>() {
    val fieldViews: MutableList<FieldView?> = MutableList(itemCount) { FieldView((context)) }
    private var selectedfield: Int? = null

    override fun getItemCount(): Int = sudoku.itemCount

    fun updateFieldView(position: Int) = fieldViews[position]?.update()

    fun selectFieldView(position: Int?, highlightNeighbors: Boolean, highlightNumber: Boolean) {
        if (position != selectedfield && highlightNeighbors) {
            sudoku.regionalHighlightingUsed = true
            selectedfield = position
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
        } else if (highlightNumber) highlightNumber(null)
        fieldViews.forEach { it?.setBackground() }
    }

    fun highlightNumber(number: Int?) {
        sudoku.numberHighlightingUsed = true
        fieldViews.forEach {
            it?.isHighlightedNumber = number != null && it?.field?.value == number
            it?.setBackground()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FieldView(context))

    override fun onBindViewHolder(holder: ViewHolder, index: Int) {
        fieldViews[index] = holder.itemView as FieldView
        (holder.itemView as FieldView).init(sudoku, index, this)
    }

    inner class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView)
}