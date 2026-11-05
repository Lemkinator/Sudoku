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

package de.lemke.sudoku.ui.board

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.view.ViewCompat
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.toSudokuDigitChar
import kotlin.math.min
import dev.oneuiproject.oneui.design.R as designR

private const val HALF = 0.5f
private const val NO_CELL = -1
private const val MAX_ALPHA = 255

/** Draws a whole sudoku board on one canvas: tones, lines, values and notes inside one rounded board path. */
class SudokuBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private class RunningAnimation(
        val style: CellAnimation,
        val stepOfCell: IntArray,
        val stepCount: Int,
        val animator: ValueAnimator,
    )

    private class NoteLayout(
        val chars: CharArray,
        val digits: IntArray,
        val slots: List<Box>,
        val textSize: Float,
    )

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint().apply { style = Paint.Style.STROKE }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val valuePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fontMetrics = Paint.FontMetrics()
    private val boardPath = Path()
    private val borderRect = RectF()
    private val cellRect = RectF()
    private val inverse = Matrix()
    private val touchPoint = FloatArray(2)
    private val valueChar = CharArray(1)
    private val animations = mutableListOf<RunningAnimation>()
    private val animationInterpolator = AccelerateDecelerateInterpolator()
    private var cellPeaks = FloatArray(0)
    private var valueSizes = FloatArray(0)
    private var noteLayouts: Array<NoteLayout?> = emptyArray()

    @ColorInt private val boardColor = resolveBackgroundColor()

    @ColorInt private val dividerColor = context.getColor(designR.color.oui_des_list_divider_color)

    @ColorInt private val givenColor = context.getColor(R.color.field_given_text_color)

    @ColorInt private val hintColor = context.getColor(R.color.field_hint_text_color)

    @ColorInt private val playerColor = context.getColor(R.color.field_userinput_text_color)

    @ColorInt private val accentColor = context.getColor(R.color.primary_color_themed)

    @ColorInt private val errorColor = loadErrorColor()

    private val toneColors: Map<CellTone, Int> =
        mapOf(
            CellTone.BOX_TINT to colorOf(R.color.control_color_normal),
            CellTone.PEER to colorOf(R.color.control_color_highlighted),
            CellTone.SAME_DIGIT to colorOf(R.color.control_color_highlighted_number),
            CellTone.SELECTED to colorOf(R.color.control_color_selected),
            CellTone.ERROR to errorColor,
        )

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val index = cellAt(e.x, e.y)
                    if (index != NO_CELL) {
                        performClick()
                        onCellClick?.invoke(index)
                    }
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    val index = cellAt(e.x, e.y)
                    if (index != NO_CELL) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onCellLongClick?.invoke(index)
                    }
                }
            },
        )

    var onCellClick: ((Int) -> Unit)? = null

    var onCellLongClick: ((Int) -> Unit)? = null

    var render: BoardRender? = null
        set(value) {
            val sizeChanged = field?.board?.size != value?.board?.size
            field = value
            if (sizeChanged) rebuildGeometry()
            rebuildCellLayouts()
            invalidate()
            accessibilityHelper.invalidateRoot()
        }

    internal val transform = Matrix()

    internal var geometry: BoardGeometry? = null
        private set

    private val accessibilityHelper = BoardAccessibilityHelper(this)

    init {
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        clipToOutline = true
        outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(
                    view: View,
                    outline: Outline,
                ) {
                    outline.setRoundRect(0, 0, view.width, view.height, geometry?.cornerRadius ?: 0f)
                }
            }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val limits =
            listOf(widthMeasureSpec, heightMeasureSpec)
                .filter { MeasureSpec.getMode(it) != MeasureSpec.UNSPECIFIED }
                .map(MeasureSpec::getSize)
        val side = limits.minOrNull() ?: 0
        setMeasuredDimension(side, side)
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGeometry()
        rebuildCellLayouts()
        invalidateOutline()
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = accessibilityHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        accessibilityHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun onDetachedFromWindow() {
        animations.toList().forEach { it.animator.cancel() }
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        val render = render ?: return
        val geometry = geometry ?: return
        val metrics = geometry.metrics
        canvas.concat(transform)
        canvas.save()
        canvas.clipPath(boardPath)
        fillPaint.color = boardColor
        canvas.drawPath(boardPath, fillPaint)
        for (index in 0 until render.board.size.cellCount) drawTone(canvas, geometry, render.toneOf(index), index)
        drawLines(canvas, geometry, render.board.size.blockSize)
        for (index in 0 until render.board.size.cellCount) {
            drawValue(canvas, geometry, render, index)
            drawNotes(canvas, render, index)
        }
        canvas.restore()
        borderPaint.color = dividerColor
        borderPaint.strokeWidth = metrics.border
        val borderRadius = geometry.cornerRadius - metrics.border * HALF
        canvas.drawRoundRect(borderRect, borderRadius, borderRadius, borderPaint)
    }

    /** Pulses the values of the cells in each step of [steps] in turn, then calls [onEnd] once the last value is at rest. */
    fun animateCells(
        steps: List<List<Int>>,
        style: CellAnimation,
        onEnd: () -> Unit = {},
    ) {
        val board = render?.board ?: return
        if (steps.isEmpty()) {
            onEnd()
            return
        }
        val stepOfCell = IntArray(board.size.cellCount) { NO_CELL }
        steps.forEachIndexed { step, cells -> cells.forEach { stepOfCell[it] = step } }
        val blockSize = board.size.blockSize
        val animator = ValueAnimator.ofFloat(0f, style.totalMillis(steps.size, blockSize).toFloat())
        animator.duration = style.totalMillis(steps.size, blockSize)
        val running = RunningAnimation(style, stepOfCell, steps.size, animator)
        animations += running
        animator.addUpdateListener { updatePeaks() }
        animator.addListener(
            object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    animations -= running
                    updatePeaks()
                    if (!cancelled) onEnd()
                }
            },
        )
        animator.start()
    }

    private fun updatePeaks() {
        val blockSize = geometry?.size?.blockSize ?: return
        cellPeaks.fill(0f)
        for (running in animations) {
            val elapsed = running.animator.animatedValue as Float
            val stepMillis = running.style.stepMillis(blockSize)
            for (index in cellPeaks.indices) {
                val step = running.stepOfCell[index]
                if (step == NO_CELL) continue
                val leg = running.style.peakAt(elapsed - step * stepMillis)
                cellPeaks[index] = maxOf(cellPeaks[index], animationInterpolator.getInterpolation(leg))
            }
        }
        invalidate()
    }

    private fun rebuildGeometry() {
        val board = render?.board
        val side = min(width, height).toFloat()
        if (board == null || side <= 0f) {
            geometry = null
            return
        }
        val metrics = BoardMetrics.of(side, cornerRadiusPx(), resources.displayMetrics.density)
        val rebuilt = BoardGeometry(board.size, metrics)
        geometry = rebuilt
        boardPath.rewind()
        boardPath.addRoundRect(0f, 0f, side, side, rebuilt.cornerRadius, rebuilt.cornerRadius, Path.Direction.CW)
        val half = metrics.border * HALF
        borderRect.set(half, half, side - half, side - half)
        cellPeaks = FloatArray(board.size.cellCount)
    }

    private fun rebuildCellLayouts() {
        val render = render
        val geometry = geometry
        if (render == null || geometry == null) {
            noteLayouts = emptyArray()
            valueSizes = FloatArray(0)
            return
        }
        val board = render.board
        valueSizes = FloatArray(board.size.cellCount) { valueTextSize(geometry.cellBox(it).width) }
        noteLayouts =
            Array(board.size.cellCount) { index ->
                val digits = board[index].notes.digits
                if (digits.isEmpty() || board[index].value != null) {
                    null
                } else {
                    val content = geometry.contentBox(index)
                    NoteLayout(
                        chars = CharArray(digits.size) { digits[it].toSudokuDigitChar() },
                        digits = digits.toIntArray(),
                        slots = noteSlots(content, digits.size),
                        textSize = noteTextSize(content, digits.size, valueSizes[index]),
                    )
                }
            }
    }

    private fun drawTone(
        canvas: Canvas,
        geometry: BoardGeometry,
        tone: CellTone,
        index: Int,
    ) {
        val color = toneColors[tone] ?: return
        val box = geometry.cellBox(index)
        cellRect.set(box.left, box.top, box.right, box.bottom)
        fillPaint.color = color
        canvas.drawRect(cellRect, fillPaint)
    }

    private fun drawLines(
        canvas: Canvas,
        geometry: BoardGeometry,
        blockSize: Int,
    ) {
        val metrics = geometry.metrics
        linePaint.color = dividerColor
        for (i in 1 until geometry.size.value) {
            val isBoxLine = i % blockSize == 0
            val width = if (isBoxLine) metrics.boxLine else metrics.thinLine
            linePaint.strokeWidth = width
            val position = geometry.edge(i) + if (width.toInt() % 2 == 1) HALF else 0f
            canvas.drawLine(position, 0f, position, metrics.side, linePaint)
            canvas.drawLine(0f, position, metrics.side, position, linePaint)
        }
    }

    private fun drawValue(
        canvas: Canvas,
        geometry: BoardGeometry,
        render: BoardRender,
        index: Int,
    ) {
        val cell = render.board[index]
        val value = cell.value ?: return
        val content = geometry.contentBox(index)
        val peak = cellPeaks[index]
        val style = animationStyleOf(index)
        valuePaint.textSize = valueSizes[index]
        valuePaint.color =
            when (cell.origin) {
                CellOrigin.GIVEN -> givenColor
                CellOrigin.HINT -> hintColor
                CellOrigin.PLAYER -> playerColor
            }
        canvas.save()
        if (style != null && peak > 0f) {
            val scale = 1f + (style.scale - 1f) * peak
            canvas.rotate(style.rotationDegrees * peak, content.centerX, content.centerY)
            canvas.scale(scale, scale, content.centerX, content.centerY)
            valuePaint.alpha = (MAX_ALPHA * (1f - (1f - style.alpha) * peak)).toInt()
        }
        valueChar[0] = value.toSudokuDigitChar()
        drawCentered(canvas, valueChar, 0, valuePaint, content.centerX, content.centerY)
        canvas.restore()
    }

    private fun drawNotes(
        canvas: Canvas,
        render: BoardRender,
        index: Int,
    ) {
        val layout = noteLayouts[index] ?: return
        notePaint.textSize = layout.textSize
        layout.slots.forEachIndexed { slot, box ->
            val emphasized = render.isEmphasizedNote(layout.digits[slot])
            notePaint.typeface = if (emphasized) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            notePaint.color = if (emphasized) accentColor else playerColor
            drawCentered(canvas, layout.chars, slot, notePaint, box.centerX, box.centerY)
        }
    }

    private fun drawCentered(
        canvas: Canvas,
        chars: CharArray,
        at: Int,
        paint: Paint,
        centerX: Float,
        centerY: Float,
    ) {
        paint.getFontMetrics(fontMetrics)
        canvas.drawText(chars, at, 1, centerX, centerY - (fontMetrics.ascent + fontMetrics.descent) * HALF, paint)
    }

    private fun animationStyleOf(index: Int): CellAnimation? {
        for (running in animations) if (running.stepOfCell[index] != NO_CELL) return running.style
        return null
    }

    internal fun cellAt(
        x: Float,
        y: Float,
    ): Int {
        val geometry = geometry ?: return NO_CELL
        transform.invert(inverse)
        touchPoint[0] = x
        touchPoint[1] = y
        inverse.mapPoints(touchPoint)
        return geometry.indexAt(touchPoint[0], touchPoint[1]) ?: NO_CELL
    }

    private fun colorOf(
        @ColorRes res: Int,
    ): Int = context.getColor(res)

    private fun resolveBackgroundColor(): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorBackground, value, true)
        return value.data
    }

    @SuppressLint("PrivateResource")
    private fun loadErrorColor(): Int = context.getColor(androidx.appcompat.R.color.sesl_error_color)

    @SuppressLint("PrivateResource")
    private fun cornerRadiusPx(): Float = resources.getDimension(androidx.appcompat.R.dimen.sesl_rounded_corner_radius)
}
