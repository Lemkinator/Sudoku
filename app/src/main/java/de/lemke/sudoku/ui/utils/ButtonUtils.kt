package de.lemke.sudoku.ui.utils

import android.content.Context
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import de.lemke.sudoku.R

class ButtonUtils {
    companion object {
        fun setButtonStyle(context: Context, button: AppCompatButton, style: Int) {
            when (style) {
                R.style.ButtonStyle_Colored -> {
                    button.backgroundTintList = ContextCompat.getColorStateList(context, R.color.primary_color_themed)
                    TextViewCompat.setCompoundDrawableTintList(
                        button,
                        ContextCompat.getColorStateList(context, R.color.primary_text_icon_color)
                    )
                    button.setTextColor(ContextCompat.getColor(context, R.color.primary_text_icon_color))
                }
                R.style.ButtonStyle_Transparent -> {
                    button.backgroundTintList = ContextCompat.getColorStateList(context, android.R.color.transparent)
                    TextViewCompat.setCompoundDrawableTintList(
                        button,
                        ContextCompat.getColorStateList(context, R.color.primary_text_icon_color)
                    )
                    button.setTextColor(ContextCompat.getColorStateList(context, R.color.primary_text_icon_color))
                }
                R.style.ButtonStyle_Transparent_Colored -> {
                    button.backgroundTintList = ContextCompat.getColorStateList(context, android.R.color.transparent)
                    TextViewCompat.setCompoundDrawableTintList(
                        button,
                        ContextCompat.getColorStateList(context, R.color.primary_color_themed)
                    )
                    button.setTextColor(ContextCompat.getColorStateList(context, R.color.primary_color_themed))
                }
                R.style.ButtonStyle_Filled -> {
                    button.backgroundTintList = ContextCompat.getColorStateList(context, R.color.filled_background_color)
                    TextViewCompat.setCompoundDrawableTintList(
                        button,
                        ContextCompat.getColorStateList(context, R.color.primary_text_icon_color)
                    )
                    button.setTextColor(ContextCompat.getColor(context, R.color.primary_text_icon_color))
                }
                R.style.ButtonStyle_Filled_Themed -> {
                    button.backgroundTintList = ContextCompat.getColorStateList(context, R.color.filled_background_color_themed)
                    TextViewCompat.setCompoundDrawableTintList(
                        button,
                        ContextCompat.getColorStateList(context, R.color.primary_text_icon_color_themed)
                    )
                    button.setTextColor(ContextCompat.getColor(context, R.color.primary_text_icon_color_themed))
                }
                else -> setButtonStyle(context, button, R.style.ButtonStyle_Colored)
            }
        }
    }
}
