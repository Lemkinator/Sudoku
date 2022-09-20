package de.lemke.sudoku.domain.model

enum class Difficulty(val value: Int) {
    VERY_EASY(0),
    EASY(1),
    MEDIUM(2),
    HARD(3),
    EXPERT(4);

    companion object {
        fun fromInt(value: Int?): Difficulty = when (value) {
            0 -> VERY_EASY
            1 -> EASY
            2 -> MEDIUM
            3 -> HARD
            4 -> EXPERT
            else -> VERY_EASY
        }
        val max: Int
            get() = values().size - 1
    }
}