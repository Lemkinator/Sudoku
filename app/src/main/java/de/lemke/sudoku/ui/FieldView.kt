package de.lemke.sudoku.ui

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.*
import kotlinx.coroutines.delay
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
        position = Position.create(sudoku.size, index)
        field = sudoku[position.index]
        if (fieldViewContainer == null) return

        if (position.row == 2 || position.row == 5) foreground = context.getDrawable(
            if (position.column == 2 || position.column == 5) R.drawable.sudoku_view_item_fg_border_bottom_right
            else R.drawable.sudoku_view_item_fg_border_bottom
        )
        else if (position.column == 2 || position.column == 5)
            foreground = context.getDrawable(R.drawable.sudoku_view_item_fg_border_right)

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
        if (field.hint) fieldViewValue?.setTextColor(Color.GRAY)
        updateNotes()
        setBackground()
        if (sudoku?.completed != true) {
            setOnClickListener { sudoku?.gameListener?.onFieldClicked(position) }
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
        else if (isSelected) setBackgroundColor(resources.getColor(R.color.control_color_selected, context.theme))
        else if (isHighlightedNumber) setBackgroundColor(resources.getColor(R.color.control_color_highlighted_number, context.theme))
        else if (isHighlighted) setBackgroundColor(resources.getColor(R.color.control_color_highlighted, context.theme))
        else if (isColored) setBackgroundColor(resources.getColor(R.color.control_color_normal, context.theme))
        else setBackgroundColor(Color.TRANSPARENT)
    }

    suspend fun flash(milliseconds: Long) {
        setBackgroundColor(resources.getColor(dev.oneuiproject.oneui.R.color.sesl_btn_background_color_dark, context.theme))
        delay(milliseconds)
        setBackground()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}