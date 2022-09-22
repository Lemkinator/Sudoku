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

        //init view
        fieldViewValue?.text = if (field.value == null) null else field.value.toString()
        fieldViewValue?.visibility = if (field.value == null) GONE else VISIBLE
        if (!field.given && !field.hint && !sudoku.completed) {
            setOnClickListener {
                //showMainPopup()
            }
            setOnLongClickListener {
                if (field.value != null) {
                    move(null)
                    return@setOnLongClickListener true
                }
                false
            }
        }
        updateNotes()

        //init appearance
        val typedValue = TypedValue()
        context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        fieldViewValue?.setTextColor(typedValue.data)
        if (field.hint) {
            fieldViewValue?.setTextColor(context.getColor(R.color.secondary_color))
        }
        val rm = position.row % (sudoku.blockSize * 2)
        val cm = position.column % (sudoku.blockSize * 2)
        isColored = (rm >= sudoku.blockSize && rm < sudoku.blockSize * 2) != (cm >= sudoku.blockSize && cm < sudoku.blockSize * 2)
        setBackground()
    }

    private fun move(value: Int?) {
        if (value == field.value) return
        sudoku?.move(position, value) ?: return
        fieldViewValue?.text = value?.toString()
        fieldViewValue?.visibility = if (value == null) View.GONE else View.VISIBLE
        setBackground()
        if (value != null) {
            if (field.error) sudoku!!.errorsMade++
            checkSudoku()
        }
    }

    private fun setHint() {
        sudoku!!.hintsUsed++
        field.hint = true
        isEnabled = false
        fieldViewValue?.setTextColor(context.getColor(R.color.secondary_color))
        move(field.solution)
    }

    private fun updateNotes() {
        fieldViewNotes?.visibility = if (field.notes.size == 0) View.GONE else View.VISIBLE
        fieldViewNotes?.text = field.notes.stream().map { it.toString() }.collect(Collectors.joining())
        if (position.row == (sudoku?.size ?: return) - 1 && (position.column == 0 || position.column == sudoku!!.size - 1))
            fieldViewNotes?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    }

    private fun checkSudoku() {
        if (sudoku?.completed == true) {
            sudoku!!.gameListener?.onCompleted()
            AlertDialog.Builder(context)
                .setTitle(R.string.completed_no_error_title)
                .setPositiveButton(R.string.dismiss, null)
                .show()
        }
        //show errors count
        //errors > max errors?

    }

    private fun setBackground() {
        if (field.error) setBackgroundColor(context.getColor(androidx.appcompat.R.color.sesl_error_color))
        else if (isColored) setBackgroundColor(resources.getColor(R.color.control_color_normal, context.theme))
        else setBackgroundColor(Color.TRANSPARENT)
    }

    /*
    private fun showMainPopup() {
        val popupView: View =
            (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.popup_number_input, null)
        val popupWindow = PopupWindow(popupView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true)
        popupWindow.animationStyle = R.style.MenuPopupAnimStyle
        isSelected = true
        popupWindow.setOnDismissListener { isSelected = false }

        //buttons
        val popupNotes: AppCompatImageButton = popupView.findViewById(R.id.popup_notes)
        popupNotes.tooltipText = context.getString(R.string.notes)
        popupNotes.visibility = if (sudoku.isEditMode()) View.GONE else View.VISIBLE
        popupNotes.setOnClickListener(View.OnClickListener { v: View? ->
            popupWindow.dismiss()
            showNotesPopup()
        })
        val popupRemove: AppCompatImageButton = popupView.findViewById(R.id.popup_remove)
        popupRemove.tooltipText = context.getString(R.string.remove)
        popupRemove.visibility = if (field.value == null) View.GONE else View.VISIBLE
        popupRemove.setOnClickListener {
            popupWindow.dismiss()
            setValue(null)
        }
        val popupHint: AppCompatImageButton = popupView.findViewById(R.id.popup_hint)
        popupHint.tooltipText = context.getString(R.string.hint)
        popupHint.visibility = if (sudoku.isEditMode()) View.GONE else View.VISIBLE
        popupHint.setOnClickListener(View.OnClickListener { v: View? ->
            popupWindow.dismiss()
            setHint()
        })

        //Number grid
        val popupGrid = popupView.findViewById<GridLayout>(R.id.popup_numbers)
        popupGrid.columnCount = sudoku.blockSize
        popupGrid.rowCount = sudoku.blockSize
        for (i in 1..size) {
            val materialTextView = MaterialTextView(context)
            materialTextView.setPadding(28, 4, 28, 4)
            val outValue = TypedValue()
            context.theme.resolveAttribute(R.attr.selectableItemBackgroundBorderless, outValue, true)
            materialTextView.setBackgroundResource(outValue.resourceId)
            materialTextView.textSize = 24f
            materialTextView.setTypeface(Typeface.DEFAULT_BOLD)
            materialTextView.text = i.toString()
            materialTextView.setOnClickListener { v: View ->
                popupWindow.dismiss()
                setValue(Integer.valueOf((v as MaterialTextView).text.toString()))
            }
            popupGrid.addView(materialTextView)
        }

        //show at position
        val rect = Rect()
        fieldViewContainer!!.getGlobalVisibleRect(rect)
        popupView.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        popupWindow.showAtLocation(
            fieldViewContainer,
            Gravity.TOP or Gravity.START,
            rect.left - popupView.measuredWidth / 2 + rect.width() / 2,
            rect.bottom - popupView.measuredHeight / 2 - rect.height() / 2
        )

        //dim screen
        val container = popupWindow.contentView.parent as View
        val wmlp: WindowManager.LayoutParams = container.layoutParams as WindowManager.LayoutParams
        wmlp.flags = wmlp.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
        wmlp.dimAmount = 0.3f
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(container, wmlp)
    }

    private fun showNotesPopup() {
        val popupView: View =
            (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.popup_notes_input, null)
        val popupWindow = PopupWindow(popupView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true)
        popupWindow.animationStyle = R.style.MenuPopupAnimStyle
        isSelected = true
        popupWindow.setOnDismissListener { isSelected = false }

        //content
        val popupGrid = popupView.findViewById<GridLayout>(R.id.popup_notes_grid)
        popupGrid.columnCount = sudoku.blockSize
        popupGrid.rowCount = sudoku.blockSize
        for (i in 1..size) {
            val materialTextView = MaterialTextView(context)
            materialTextView.setPadding(28, 4, 28, 4)
            materialTextView.textSize = 24f
            materialTextView.setTextColor(resources.getColorStateList(R.color.dialog_notes_selector, context.theme))
            materialTextView.setTypeface(Typeface.DEFAULT_BOLD)
            materialTextView.text = i.toString()
            materialTextView.isSelected = field.notes.contains(i)
            val outValue = TypedValue()
            context.theme.resolveAttribute(R.attr.selectableItemBackgroundBorderless, outValue, true)
            materialTextView.setBackgroundResource(outValue.resourceId)
            materialTextView.setOnClickListener {
                sudoku.addFieldToHistory(field, position)
                materialTextView.isSelected = !materialTextView.isSelected
                if (materialTextView.isSelected) field.addNote(i) else field.removeNote(i)
                updateNotes()
            }
            popupGrid.addView(materialTextView)
        }

        //calculate position
        val rect = Rect()
        fieldViewContainer!!.getGlobalVisibleRect(rect)
        popupView.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val mPosX = intArrayOf(rect.left - popupView.measuredWidth / 2 + rect.width() / 2)
        val mPosY = intArrayOf(rect.bottom - popupView.measuredHeight / 2 - rect.height() / 2)

        //dragging
        popupView.findViewById<View>(R.id.drag_icon).setOnTouchListener(object : OnTouchListener {
            private var dx = 0
            private var dy = 0
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dx = (mPosX[0] - motionEvent.rawX).toInt()
                        dy = (mPosY[0] - motionEvent.rawY).toInt()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        mPosX[0] = (motionEvent.rawX + dx).toInt()
                        mPosY[0] = (motionEvent.rawY + dy).toInt()
                        popupWindow.update(mPosX[0], mPosY[0], -1, -1)
                    }
                }
                return true
            }
        })

        //show at position
        popupWindow.showAtLocation(fieldViewContainer, Gravity.TOP or Gravity.START, mPosX[0], mPosY[0])
    }
    */

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}