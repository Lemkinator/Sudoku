package de.lemke.sudoku.domain

import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetAllSudokusUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
) {
    companion object {
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

    suspend operator fun invoke(flags: Int): List<Sudoku> = withContext(Dispatchers.Default) {
        val typeAll = flags and TYPE_ALL != 0
        val typeNormal = flags and TYPE_NORMAL != 0
        val typeDaily = flags and TYPE_DAILY != 0
        val typeLevel = flags and TYPE_LEVEL != 0
        val difficultyAll = flags and DIFFICULTY_ALL != 0
        val difficultyVeryEasy = flags and DIFFICULTY_VERY_EASY != 0
        val difficultyEasy = flags and DIFFICULTY_EASY != 0
        val difficultyMedium = flags and DIFFICULTY_MEDIUM != 0
        val difficultyHard = flags and DIFFICULTY_HARD != 0
        val difficultyExpert = flags and DIFFICULTY_EXPERT != 0
        val sizeAll = flags and SIZE_ALL != 0
        val size4x4 = flags and SIZE_4X4 != 0
        val size9x9 = flags and SIZE_9X9 != 0
        val size16x16 = flags and SIZE_16X16 != 0

        sudokusRepository.getAllSudokus().filter {
            (typeAll || (typeNormal && it.isNormalSudoku) || (typeDaily && it.isDailySudoku) || (typeLevel && it.isSudokuLevel)) &&
                    (sizeAll || (size4x4 && it.size == 4) || (size9x9 && it.size == 9) || (size16x16 && it.size == 16)) &&
                    (difficultyAll ||
                            (difficultyVeryEasy && it.difficulty == Difficulty.VERY_EASY) ||
                            (difficultyEasy && it.difficulty == Difficulty.EASY) ||
                            (difficultyMedium && it.difficulty == Difficulty.MEDIUM) ||
                            (difficultyHard && it.difficulty == Difficulty.HARD) ||
                            (difficultyExpert && it.difficulty == Difficulty.EXPERT))
        }
    }
}
