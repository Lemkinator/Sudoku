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

package de.lemke.sudoku.ui.utils

import de.lemke.sudoku.domain.model.Sudoku

sealed class SudokuListItem {
    abstract val label: String
    abstract val stableId: Long

    data class SudokuItem(val sudoku: Sudoku, override val label: String) : SudokuListItem() {
        override val stableId: Long get() = sudoku.hashCode().toLong()

        companion object {
            const val VIEW_TYPE = 0
        }
    }

    data class SeparatorItem(val indexText: String) : SudokuListItem() {
        override val label = indexText
        override val stableId: Long get() = indexText.hashCode().toLong()

        companion object {
            const val VIEW_TYPE = 1
        }
    }
}
