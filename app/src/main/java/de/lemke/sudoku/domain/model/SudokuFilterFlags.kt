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

object SudokuFilterFlags {
    const val TYPE_ALL = 1 shl 1
    const val TYPE_NORMAL = 1 shl 2
    const val TYPE_DAILY = 1 shl 3
    const val TYPE_LEVEL = 1 shl 4
    const val DIFFICULTY_ALL = 1 shl 10
    const val DIFFICULTY_VERY_EASY = 1 shl 11
    const val DIFFICULTY_EASY = 1 shl 12
    const val DIFFICULTY_MEDIUM = 1 shl 13
    const val DIFFICULTY_HARD = 1 shl 14
    const val DIFFICULTY_EXPERT = 1 shl 15
    const val SIZE_ALL = 1 shl 20
    const val SIZE_4X4 = 1 shl 21
    const val SIZE_9X9 = 1 shl 22
    const val SIZE_16X16 = 1 shl 23
}
