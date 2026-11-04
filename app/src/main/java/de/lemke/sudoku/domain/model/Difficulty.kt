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
import de.lemke.sudoku.R

enum class Difficulty(
    val code: Int,
    private val givensFour: Int,
    private val givensNine: Int,
    private val givensSixteen: Int,
) {
    VERY_EASY(code = 0, givensFour = 10, givensNine = 50, givensSixteen = 196),
    EASY(code = 1, givensFour = 9, givensNine = 40, givensSixteen = 176),
    MEDIUM(code = 2, givensFour = 7, givensNine = 35, givensSixteen = 156),
    HARD(code = 3, givensFour = 6, givensNine = 30, givensSixteen = 136),
    EXPERT(code = 4, givensFour = 4, givensNine = 23, givensSixteen = 116),
    ;

    // Entry order is the index into R.array.difficulty and the difficulty seekbar progress.
    val value: Int get() = ordinal

    fun getLocalString(resources: Resources): String = resources.getStringArray(R.array.difficulty)[this.ordinal]

    fun numbersToRemove(size: SudokuSize): Int = size.cellCount - givenNumbers(size)

    private fun givenNumbers(size: SudokuSize): Int =
        when (size) {
            SudokuSize.FOUR -> givensFour
            SudokuSize.NINE -> givensNine
            SudokuSize.SIXTEEN -> givensSixteen
        }

    companion object {
        fun fromInt(value: Int?): Difficulty = entries.getOrNull(value ?: -1) ?: MEDIUM

        fun fromCodeOrNull(code: Int): Difficulty? = entries.firstOrNull { it.code == code }

        fun fromNameOrNull(name: String): Difficulty? = entries.firstOrNull { it.name == name }

        fun getLocalString(
            ordinal: Int,
            resources: Resources,
        ): String = fromInt(ordinal).getLocalString(resources)

        val max: Int
            get() = Difficulty.entries.size - 1
    }
}
