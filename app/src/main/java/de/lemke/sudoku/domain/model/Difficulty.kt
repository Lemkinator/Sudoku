package de.lemke.sudoku.domain.model

import android.content.res.Resources
import de.lemke.sudoku.R

enum class Difficulty(val value: Int) {
    VERY_EASY(0),
    EASY(1),
    MEDIUM(2),
    HARD(3),
    EXPERT(4);

    fun getLocalString(resources: Resources): String = resources.getStringArray(R.array.difficuilty)[this.ordinal]

    private fun givenNumbers(size: Int): Int = when (size) {
        4 -> when (this) {
            VERY_EASY -> 10
            EASY -> 9
            MEDIUM -> 7
            HARD -> 6
            EXPERT -> 4
        }
        9 -> when (this) {
            /*
            total number of valid 9-by-9 Sudoku grids is 6,670,903,752,021,072,936,960
            minimal amount of givens in an initial Sudoku puzzle that can yield a unique solution is 17
             */
            VERY_EASY -> 50//more than 50
            EASY -> 36 //36-49
            MEDIUM -> 32 //32-35
            HARD -> 28 //28-31
            EXPERT -> 23 //22-27
        }
        else -> givenNumbers(9)
    }

    fun numbersToRemove(size: Int): Int = size * size - givenNumbers(size)

    companion object {
        fun fromInt(value: Int?): Difficulty = when (value) {
            0 -> VERY_EASY
            1 -> EASY
            2 -> MEDIUM
            3 -> HARD
            4 -> EXPERT
            else -> VERY_EASY
        }

        fun getLocalString(ordinal: Int, resources: Resources): String = fromInt(ordinal).getLocalString(resources)

        val max: Int
            get() = values().size - 1
    }
}