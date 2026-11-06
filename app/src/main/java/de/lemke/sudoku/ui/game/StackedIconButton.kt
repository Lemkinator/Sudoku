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
import androidx.appcompat.R
import androidx.appcompat.widget.AppCompatButton

/**
 * A button whose top icon sits directly above its label, the pair centered vertically when the button is taller than its content.
 */
class StackedIconButton
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = R.attr.buttonStyle,
    ) : AppCompatButton(context, attrs, defStyleAttr) {
        // With a fixed size, TextView.checkForRelayout rebuilds the layout in place and only invalidates, so onLayout needs this request.
        override fun onTextChanged(
            text: CharSequence?,
            start: Int,
            lengthBefore: Int,
            lengthAfter: Int,
        ) {
            super.onTextChanged(text, start, lengthBefore, lengthAfter)
            requestLayout()
        }

        // TextView draws the top icon at the top padding edge, so the icon bounds carry the offset.
        override fun onLayout(
            changed: Boolean,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ) {
            super.onLayout(changed, left, top, right, bottom)
            val textLayout = layout
            val icon = compoundDrawables[1]
            if (textLayout == null || icon == null) return
            val slack = measuredHeight - paddingTop - paddingBottom - icon.bounds.height() - compoundDrawablePadding - textLayout.height
            val iconTop = maxOf(slack, 0) / 2
            icon.setBounds(icon.bounds.left, iconTop, icon.bounds.right, iconTop + icon.bounds.height())
        }
    }
