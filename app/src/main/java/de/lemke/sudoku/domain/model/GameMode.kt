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

import java.time.LocalDate

sealed interface GameMode {
    val code: Int

    companion object {
        const val CODE_NORMAL = 0
        const val CODE_DAILY = 1
        const val CODE_LEVEL = 2

        /** The mode of [code]; a level needs its [level] number and a daily one the [dailyDate] it was made for. */
        fun fromCode(
            code: Int,
            level: Int?,
            dailyDate: LocalDate?,
        ): GameMode? =
            when (code) {
                CODE_NORMAL -> Normal
                CODE_DAILY -> dailyDate?.let(::Daily)
                CODE_LEVEL -> level?.takeIf { it >= 1 }?.let(::Level)
                else -> null
            }
    }

    data object Normal : GameMode {
        override val code = CODE_NORMAL
    }

    data class Daily(val date: LocalDate) : GameMode {
        override val code = CODE_DAILY
    }

    data class Level(val number: Int) : GameMode {
        init {
            require(number >= 1) { "Level number must be positive: $number" }
        }

        override val code = CODE_LEVEL
    }
}
