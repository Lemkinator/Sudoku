package de.lemke.sudoku.ui.utils

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import java.util.stream.Collectors


class FieldView(context: Context) : LinearLayout(context) {
    private var fieldViewValue: TextView? = null
    private var fieldViewNotes: TextView? = null
    private var fieldViewContainer: View? = null
    private var sudoku: Sudoku? = null
    private lateinit var adapter: SudokuViewAdapter
    lateinit var field: Field
    lateinit var position: Position
    private var isColored = false
    var isHighlighted = false
    var isHighlightedNumber = false

    init {
        val asyncLayoutInflater = AsyncLayoutInflater(context)
        asyncLayoutInflater.inflate(R.layout.field_view, this) { view, _, parent ->
            parent?.layoutTransition = LayoutTransition()
            parent?.addView(view)
            fieldViewValue = findViewById(R.id.itemNumber)
            fieldViewNotes = findViewById(R.id.itemNotes)
            fieldViewContainer = findViewById(R.id.itemContainer)

            sudoku?.let { init(it, position.index, adapter) }
        }
    }

    fun init(sudoku: Sudoku, index: Int, adapter: SudokuViewAdapter) {
        //init vars
        this.adapter = adapter
        this.sudoku = sudoku
        position = Position.create(index, sudoku.size)
        field = sudoku[position.index]
        if (fieldViewContainer == null) return

        when {
            position.row % sudoku.blockSize == sudoku.blockSize -1 && position.row != sudoku.size -1 -> foreground = context.getDrawable(
                if (position.column % sudoku.blockSize == sudoku.blockSize -1 && position.column != sudoku.size - 1)
                    R.drawable.sudoku_view_item_fg_border_bottom_right
                else R.drawable.sudoku_view_item_fg_border_bottom
            )
            position.column % sudoku.blockSize == sudoku.blockSize -1 && position.column != sudoku.size -1 ->
                foreground = context.getDrawable(R.drawable.sudoku_view_item_fg_border_right)
        }
        val rm = position.row % (sudoku.blockSize * 2)
        val cm = position.column % (sudoku.blockSize * 2)
        isColored = (rm >= sudoku.blockSize && rm < sudoku.blockSize * 2) != (cm >= sudoku.blockSize && cm < sudoku.blockSize * 2)
        update()
    }

    fun update() {
        fieldViewValue?.text = field.value.toSudokuString()
        fieldViewValue?.visibility = if (field.value == null) GONE else VISIBLE
        fieldViewValue?.setTextColor(
            context.getColor(
                if (field.hint) R.color.field_hint_text_color
                else if (field.given) R.color.field_given_text_color
                else R.color.field_userinput_text_color
            )
        )
        updateNotes()
        setBackground()
        if (sudoku?.completed != true) {
            setOnClickListener { sudoku?.gameListener?.onFieldClicked(position) }
            setOnLongClickListener {
                if (field.given || field.hint || field.value == null) {
                    return@setOnLongClickListener false
                }
                sudoku?.move(position, null)
                isSelected = false
                isHighlightedNumber = false
                setBackground()
                true
            }
        }
    }

    private fun updateNotes() {
        fieldViewNotes?.visibility = if (field.notes.size == 0) View.GONE else View.VISIBLE
        fieldViewNotes?.text = field.notes.stream().map { it.toSudokuString() }.collect(Collectors.joining())
        if (position.row == (sudoku?.size ?: return) - 1 && (position.column == 0 || position.column == sudoku!!.size - 1))
            fieldViewNotes?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    }

    fun setBackground() = setBackgroundColor(getCurrentBackgroundColor())

    private fun getCurrentBackgroundColor(): Int = if (field.error) context.getColor(androidx.appcompat.R.color.sesl_error_color)
    else if (isSelected) context.getColor(R.color.control_color_selected)
    else if (isHighlightedNumber) context.getColor(R.color.control_color_highlighted_number)
    else if (isHighlighted) context.getColor(R.color.control_color_highlighted)
    else if (isColored) context.getColor(R.color.control_color_normal)
    else Color.TRANSPARENT

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}

private fun Int?.toSudokuString(): CharSequence? = when (this) {
    null -> null
    10 -> "A"
    11 -> "B"
    12 -> "C"
    13 -> "D"
    14 -> "E"
    15 -> "F"
    16 -> "G"
    else -> this.toString()
}
