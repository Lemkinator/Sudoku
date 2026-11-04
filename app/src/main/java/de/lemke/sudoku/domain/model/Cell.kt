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

enum class CellOrigin(val code: Int) {
    PLAYER(code = 0),
    GIVEN(code = 1),
    HINT(code = 2),
    ;

    companion object {
        fun fromCodeOrNull(code: Int): CellOrigin? = entries.firstOrNull { it.code == code }
    }
}

data class Cell(
    val solution: Int,
    val value: Int? = null,
    val origin: CellOrigin = CellOrigin.PLAYER,
    val notes: NoteMask = NoteMask.EMPTY,
) {
    val error: Boolean get() = value != null && value != solution
    val correct: Boolean get() = value != null && value == solution
    val locked: Boolean get() = origin != CellOrigin.PLAYER
}
