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

enum class SudokuSize(
    val value: Int,
    val hintLimit: Int,
) {
    FOUR(value = 4, hintLimit = 1),
    NINE(value = 9, hintLimit = 3),
    SIXTEEN(value = 16, hintLimit = 8),
    ;

    companion object {
        fun fromValueOrNull(value: Int): SudokuSize? = entries.firstOrNull { it.value == value }

        fun fromValue(value: Int): SudokuSize = requireNotNull(fromValueOrNull(value)) { "Unsupported sudoku size: $value" }
    }
}
