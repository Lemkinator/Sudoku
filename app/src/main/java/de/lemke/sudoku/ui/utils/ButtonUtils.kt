package de.lemke.sudoku.ui.utils

import android.content.Context
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import de.lemke.sudoku.R


class ButtonUtils {
    companion object {
        fun setButtonStyle(context: Context, button: AppCompatButton, style: Int) {
            when (style) {
                R.style.ButtonStyle_Colored -> {
                    button.backgroundTintList = ContextCompat.getColorStateList(context, R.color.primary_color)
                    button.compoundDrawableTintList =
                        ContextCompat.getColorStateList(context, dev.oneuiproject.oneui.design.R.color.oui_round_and_bgcolor)
                    button.setTextColor(ContextCompat.getColor(context, dev.oneuiproject.oneui.design.R.color.oui_round_and_bgcolor))
                }
                R.style.ButtonStyle_Filled -> {
                    button.backgroundTintList = ContextCompat.getColorStateList(context, R.color.filled_background_color)
                    button.compoundDrawableTintList =
                        ContextCompat.getColorStateList(context, dev.oneuiproject.oneui.design.R.color.oui_primary_text_color)
                    button.setTextColor(ContextCompat.getColor(context, dev.oneuiproject.oneui.design.R.color.oui_primary_text_color))
                }
                R.style.ButtonStyle_Transparent -> {
                    button.backgroundTintList = ContextCompat.getColorStateList(context, android.R.color.transparent)
                    button.compoundDrawableTintList = ContextCompat.getColorStateList(context, R.color.primary_color)
                    button.setTextColor(ContextCompat.getColorStateList(context, R.color.primary_color))
                }
                else -> setButtonStyle(context, button, R.style.ButtonStyle_Colored)
            }
        }
    }
}
