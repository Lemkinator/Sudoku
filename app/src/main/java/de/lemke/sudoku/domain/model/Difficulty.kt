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
    private val givensFour: Int,
    private val givensNine: Int,
    private val givensSixteen: Int,
) {
    VERY_EASY(givensFour = 10, givensNine = 50, givensSixteen = 196),
    EASY(givensFour = 9, givensNine = 40, givensSixteen = 176),
    MEDIUM(givensFour = 7, givensNine = 35, givensSixteen = 156),
    HARD(givensFour = 6, givensNine = 30, givensSixteen = 136),
    EXPERT(givensFour = 4, givensNine = 23, givensSixteen = 116),
    ;

    // Persisted/serialized as this ordinal (see fromInt/getLocalString(ordinal, ...)); entry order is the contract.
    val value: Int get() = ordinal

    fun getLocalString(resources: Resources): String = resources.getStringArray(R.array.difficulty)[this.ordinal]

    // total number of valid 9-by-9 Sudoku grids is 6,670,903,752,021,072,936,960
    // minimal amount of givens in an initial Sudoku puzzle that can yield a unique solution is 17
    // more than 50, 36-49, 32-35, 28-31, 22-27
    fun numbersToRemove(size: SudokuSize): Int = size.cellCount - givenNumbers(size)

    private fun givenNumbers(size: SudokuSize): Int =
        when (size) {
            SudokuSize.FOUR -> givensFour
            SudokuSize.NINE -> givensNine
            SudokuSize.SIXTEEN -> givensSixteen
        }

    companion object {
        fun fromInt(value: Int?): Difficulty = entries.getOrNull(value ?: -1) ?: MEDIUM

        fun getLocalString(
            ordinal: Int,
            resources: Resources,
        ): String = fromInt(ordinal).getLocalString(resources)

        val max: Int
            get() = Difficulty.entries.size - 1
    }
}
