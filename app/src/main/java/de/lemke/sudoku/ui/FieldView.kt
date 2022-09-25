package de.lemke.sudoku.ui

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.*
import java.util.stream.Collectors


class FieldView(context: Context) : LinearLayout(context) {
    private var fieldViewValue: TextView? = null
    private var fieldViewNotes: TextView? = null
    private var fieldViewContainer: View? = null
    private var sudoku: Sudoku? = null
    private lateinit var adapter: SudokuViewAdapter
    private lateinit var field: Field
    private lateinit var position: Position
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
        position = Position.create(sudoku.size, index)
        field = sudoku[position.index]
        if (fieldViewContainer == null) return

        //init appearance
        val typedValue = TypedValue()
        if (field.given) {
            context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
            fieldViewValue?.setTextColor(typedValue.data)
        } else {
            fieldViewValue?.setTextColor(context.getColor(dev.oneuiproject.oneui.R.color.oui_primary_text_color))
        }

        val rm = position.row % (sudoku.blockSize * 2)
        val cm = position.column % (sudoku.blockSize * 2)
        isColored = (rm >= sudoku.blockSize && rm < sudoku.blockSize * 2) != (cm >= sudoku.blockSize && cm < sudoku.blockSize * 2)
        update()
    }

    fun update() {
        fieldViewValue?.text = if (field.value == null) null else field.value.toString()
        fieldViewValue?.visibility = if (field.value == null) GONE else VISIBLE
        if (field.hint) {
            isEnabled = false
            fieldViewValue?.setTextColor(context.getColor(R.color.primary_dark_color))
        }
        updateNotes()
        setBackground()
        if (!field.given && !field.hint && sudoku?.completed != true) {
            setOnClickListener {
                sudoku?.gameListener?.onFieldClicked(position)
            }
            setOnLongClickListener {
                if (field.value != null) {
                    sudoku?.move(position, null)
                    return@setOnLongClickListener true
                }
                false
            }
        }
    }

    private fun updateNotes() {
        fieldViewNotes?.visibility = if (field.notes.size == 0) View.GONE else View.VISIBLE
        fieldViewNotes?.text = field.notes.stream().map { it.toString() }.collect(Collectors.joining())
        if (position.row == (sudoku?.size ?: return) - 1 && (position.column == 0 || position.column == sudoku!!.size - 1))
            fieldViewNotes?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    }

    fun setBackground() {
        if (field.error) setBackgroundColor(context.getColor(androidx.appcompat.R.color.sesl_error_color))
        else if (isColored) setBackgroundColor(resources.getColor(R.color.control_color_normal, context.theme))
        else if (isHighlighted) setBackgroundColor(resources.getColor(R.color.control_color_highlighted, context.theme))
        else if (isHighlightedNumber) setBackgroundColor(resources.getColor(R.color.control_color_highlighted_number, context.theme))
        else setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}