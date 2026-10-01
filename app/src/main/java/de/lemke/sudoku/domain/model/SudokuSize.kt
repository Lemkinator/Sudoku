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

package de.lemke.sudoku.domain.model

import android.content.res.Resources
import androidx.annotation.StringRes
import de.lemke.sudoku.R

enum class SudokuSize(
    val value: Int,
    val blockSize: Int,
    val hintLimit: Int,
    val filterFlag: Int,
    @param:StringRes private val labelRes: Int,
) {
    FOUR(value = 4, blockSize = 2, hintLimit = 1, filterFlag = SudokuFilterFlags.SIZE_4X4, labelRes = R.string.size4),
    NINE(value = 9, blockSize = 3, hintLimit = 3, filterFlag = SudokuFilterFlags.SIZE_9X9, labelRes = R.string.size9),
    SIXTEEN(value = 16, blockSize = 4, hintLimit = 8, filterFlag = SudokuFilterFlags.SIZE_16X16, labelRes = R.string.size16),
    ;

    val cellCount: Int get() = value * value

    fun getLocalString(resources: Resources): String = resources.getString(labelRes)

    companion object {
        fun fromValueOrNull(value: Int): SudokuSize? = entries.firstOrNull { it.value == value }

        fun fromValue(value: Int): SudokuSize = requireNotNull(fromValueOrNull(value)) { "Unsupported sudoku size: $value" }
    }
}
