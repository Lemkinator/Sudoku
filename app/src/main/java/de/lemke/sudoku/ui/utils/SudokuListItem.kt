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