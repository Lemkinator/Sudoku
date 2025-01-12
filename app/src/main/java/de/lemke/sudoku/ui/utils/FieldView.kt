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
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.toSudokuString


class FieldView(context: Context) : LinearLayout(context) {
    var fieldViewValue: TextView? = null
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
            position.row % sudoku.blockSize == sudoku.blockSize - 1 && position.row != sudoku.size - 1 -> foreground =
                AppCompatResources.getDrawable(
                    context,
                    if (position.column % sudoku.blockSize == sudoku.blockSize - 1 && position.column != sudoku.size - 1) {
                        R.drawable.sudoku_view_item_fg_border_bottom_right
                    } else {
                        R.drawable.sudoku_view_item_fg_border_bottom
                    }
                )

            position.column % sudoku.blockSize == sudoku.blockSize - 1 && position.column != sudoku.size - 1 ->
                foreground = AppCompatResources.getDrawable(context, R.drawable.sudoku_view_item_fg_border_right)
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
                if (sudoku?.move(position, null) == true) {
                    isSelected = false
                    isHighlightedNumber = false
                    setBackground()
                    return@setOnLongClickListener true
                } else return@setOnLongClickListener false
            }
        }
    }

    private fun updateNotes() {
        fieldViewNotes?.visibility = if (field.notes.isEmpty()) GONE else VISIBLE
        fieldViewNotes?.text = field.notes.joinToString("")
        if (position.row == (sudoku?.size ?: return) - 1 && (position.column == 0 || position.column == sudoku!!.size - 1))
            fieldViewNotes?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    }

    fun setBackground() = setBackgroundColor(getCurrentBackgroundColor())

    @SuppressLint("PrivateResource")
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
