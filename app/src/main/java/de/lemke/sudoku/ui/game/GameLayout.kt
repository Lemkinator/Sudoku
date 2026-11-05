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

package de.lemke.sudoku.ui.game

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec.AT_MOST
import android.view.View.MeasureSpec.EXACTLY
import android.view.View.MeasureSpec.makeMeasureSpec
import android.view.ViewGroup
import androidx.core.content.res.use
import de.lemke.sudoku.R
import de.lemke.sudoku.ui.board.Box
import kotlin.math.min
import kotlin.math.roundToInt

class GameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {
    private val controlsAtEnd =
        context.obtainStyledAttributes(attrs, R.styleable.GameLayout).use {
            it.getInt(R.styleable.GameLayout_controlsPosition, POSITION_BOTTOM) == POSITION_END
        }
    private val controlsScroll =
        context.obtainStyledAttributes(attrs, R.styleable.GameLayout).use {
            it.getBoolean(R.styleable.GameLayout_controlsScroll, false)
        }

    private val board: View get() = getChildAt(0)
    private val controls: View get() = getChildAt(1)

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val params = controls.layoutParams
        val boardReserve = if (controlsScroll) min(width, height) / 2 else 0
        if (controlsAtEnd) {
            val limit = makeMeasureSpec(width - boardReserve, AT_MOST)
            controls.measure(getChildMeasureSpec(limit, 0, params.width), makeMeasureSpec(height, EXACTLY))
        } else {
            val limit = makeMeasureSpec(height - boardReserve, AT_MOST)
            controls.measure(makeMeasureSpec(width, EXACTLY), getChildMeasureSpec(limit, 0, params.height))
        }
        val arrangement = arrangement(width, height)
        val side = arrangement.board.width.roundToInt()
        board.measure(makeMeasureSpec(side, EXACTLY), makeMeasureSpec(side, EXACTLY))
        setMeasuredDimension(width, height)
    }

    override fun onLayout(
        changed: Boolean,
        l: Int,
        t: Int,
        r: Int,
        b: Int,
    ) {
        val width = r - l
        val arrangement = arrangement(width, b - t)
        val mirrored = controlsAtEnd && layoutDirection == LAYOUT_DIRECTION_RTL
        place(board, arrangement.board, width, mirrored)
        place(controls, arrangement.controls, width, mirrored)
    }

    private fun arrangement(
        width: Int,
        height: Int,
    ): GameArrangement {
        val extent = if (controlsAtEnd) controls.measuredWidth else controls.measuredHeight
        return arrangeGame(width, height, extent, controlsAtEnd)
    }

    private fun place(
        child: View,
        box: Box,
        width: Int,
        mirrored: Boolean,
    ) {
        val left = if (mirrored) width - box.right else box.left
        child.layout(left.roundToInt(), box.top.roundToInt(), (left + box.width).roundToInt(), box.bottom.roundToInt())
    }

    private companion object {
        const val POSITION_BOTTOM = 0
        const val POSITION_END = 1
    }
}
