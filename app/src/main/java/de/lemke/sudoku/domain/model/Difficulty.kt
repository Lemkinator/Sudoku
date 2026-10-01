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

enum class Difficulty {
    VERY_EASY,
    EASY,
    MEDIUM,
    HARD,
    EXPERT,
    ;

    // Persisted/serialized as this ordinal (see fromInt/getLocalString(ordinal, ...)); entry order is the contract.
    val value: Int get() = ordinal

    fun getLocalString(resources: Resources): String = resources.getStringArray(R.array.difficulty)[this.ordinal]

    // total number of valid 9-by-9 Sudoku grids is 6,670,903,752,021,072,936,960
    // minimal amount of givens in an initial Sudoku puzzle that can yield a unique solution is 17
    // more than 50, 36-49, 32-35, 28-31, 22-27
    fun numbersToRemove(size: SudokuSize): Int = size.value * size.value - givenNumbersTable.getValue(size to this)

    companion object {
        fun fromInt(value: Int?): Difficulty = entries.getOrNull(value ?: -1) ?: MEDIUM

        fun getLocalString(
            ordinal: Int,
            resources: Resources,
        ): String = fromInt(ordinal).getLocalString(resources)

        val max: Int
            get() = Difficulty.entries.size - 1

        private val givenNumbersTable: Map<Pair<SudokuSize, Difficulty>, Int> =
            mapOf(
                (SudokuSize.FOUR to VERY_EASY) to 10,
                (SudokuSize.FOUR to EASY) to 9,
                (SudokuSize.FOUR to MEDIUM) to 7,
                (SudokuSize.FOUR to HARD) to 6,
                (SudokuSize.FOUR to EXPERT) to 4,
                (SudokuSize.NINE to VERY_EASY) to 50,
                (SudokuSize.NINE to EASY) to 40,
                (SudokuSize.NINE to MEDIUM) to 35,
                (SudokuSize.NINE to HARD) to 30,
                (SudokuSize.NINE to EXPERT) to 23,
                (SudokuSize.SIXTEEN to VERY_EASY) to 196,
                (SudokuSize.SIXTEEN to EASY) to 176,
                (SudokuSize.SIXTEEN to MEDIUM) to 156,
                (SudokuSize.SIXTEEN to HARD) to 136,
                (SudokuSize.SIXTEEN to EXPERT) to 116,
            )
    }
}
