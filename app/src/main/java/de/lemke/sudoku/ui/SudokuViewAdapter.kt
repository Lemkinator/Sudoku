package de.lemke.sudoku.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.lemke.sudoku.domain.model.Sudoku

class SudokuViewAdapter(
    private val context: Context,
    private val sudoku: Sudoku,
) : RecyclerView.Adapter<SudokuViewAdapter.ViewHolder>() {
    private val fieldViews: MutableList<FieldView?>

    init {
        fieldViews = MutableList(itemCount) {FieldView((context))}
    }

    override fun getItemCount(): Int = sudoku.itemCount

    fun getFieldView(position: Int): FieldView? = fieldViews[position]

    fun updateFieldView(position: Int) = fieldViews[position]?.init(sudoku, position, this)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(FieldView(context))

    override fun onBindViewHolder(holder: ViewHolder, index: Int) {
        fieldViews[index] = holder.itemView as FieldView
        (holder.itemView as FieldView).init(sudoku, index, this)
    }

    inner class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView)
}