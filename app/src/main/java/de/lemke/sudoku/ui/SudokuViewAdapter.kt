package de.lemke.sudoku.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku

class SudokuViewAdapter(
    private val context: Context,
    private val sudoku: Sudoku,
) : RecyclerView.Adapter<SudokuViewAdapter.ViewHolder>() {
    private val fieldViews: MutableList<FieldView?>
    private var selectedfield: Int? = null

    init {
        fieldViews = MutableList(itemCount) { FieldView((context)) }
    }

    override fun getItemCount(): Int = sudoku.itemCount

    fun getFieldView(position: Int): FieldView? = fieldViews[position]

    fun updateFieldView(position: Int) = fieldViews[position]?.update()//init(sudoku, position, this)

    fun selectFieldView(position: Int?, highlightNeighbors: Boolean = true) {
        if (highlightNeighbors && position != selectedfield) {
            fieldViews.forEach { it?.isHighlighted = false }
            if (position != null) {
                sudoku.getNeighbors(Position.create(sudoku.size, position)).map { it.position.index }
                    .forEach { fieldViews[it]?.isHighlighted = true }
            }

        }
        fieldViews.forEach { it?.isSelected = false }
        if (position != null) {
            fieldViews[position]?.isSelected = true
            fieldViews[position]?.isHighlightedNumber = true
        }
        fieldViews.forEach { it?.setBackground() }
    }

    fun highightNumber(number: Int?) {
        TODO()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FieldView(context))

    override fun onBindViewHolder(holder: ViewHolder, index: Int) {
        fieldViews[index] = holder.itemView as FieldView
        (holder.itemView as FieldView).init(sudoku, index, this)
    }

    inner class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView)
}