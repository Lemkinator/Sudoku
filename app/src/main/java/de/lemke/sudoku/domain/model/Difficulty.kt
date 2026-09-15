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
    private fun givenNumbers(size: Int): Int = givenNumbersTable[size to this] ?: givenNumbersTable.getValue(DEFAULT_SIZE to this)

    fun numbersToRemove(size: Int): Int = size * size - givenNumbers(size)

    companion object {
        private const val DEFAULT_SIZE = 9

        fun fromInt(value: Int?): Difficulty = entries.getOrNull(value ?: -1) ?: MEDIUM

        fun getLocalString(
            ordinal: Int,
            resources: Resources,
        ): String = fromInt(ordinal).getLocalString(resources)

        val max: Int
            get() = Difficulty.entries.size - 1

        private val givenNumbersTable: Map<Pair<Int, Difficulty>, Int> =
            mapOf(
                (4 to VERY_EASY) to 10,
                (4 to EASY) to 9,
                (4 to MEDIUM) to 7,
                (4 to HARD) to 6,
                (4 to EXPERT) to 4,
                (9 to VERY_EASY) to 50,
                (9 to EASY) to 40,
                (9 to MEDIUM) to 35,
                (9 to HARD) to 30,
                (9 to EXPERT) to 23,
                (16 to VERY_EASY) to 196,
                (16 to EASY) to 176,
                (16 to MEDIUM) to 156,
                (16 to HARD) to 136,
                (16 to EXPERT) to 116,
            )
    }
}
