package de.lemke.sudoku.domain

import de.lemke.sudoku.data.UserSettingsRepository
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.DIFFICULTY_ALL
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.DIFFICULTY_EASY
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.DIFFICULTY_EXPERT
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.DIFFICULTY_HARD
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.DIFFICULTY_MEDIUM
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.DIFFICULTY_VERY_EASY
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.SIZE_16X16
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.SIZE_4X4
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.SIZE_9X9
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.SIZE_ALL
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.TYPE_ALL
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.TYPE_DAILY
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.TYPE_LEVEL
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.TYPE_NORMAL
import de.lemke.sudoku.domain.model.Difficulty.EASY
import de.lemke.sudoku.domain.model.Difficulty.EXPERT
import de.lemke.sudoku.domain.model.Difficulty.HARD
import de.lemke.sudoku.domain.model.Difficulty.MEDIUM
import de.lemke.sudoku.domain.model.Difficulty.VERY_EASY
import de.lemke.sudoku.domain.model.Sudoku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ObserveSudokusAndStatisticsFilterFlagsUseCase @Inject constructor(
    private val sudokusRepository: SudokusRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {
    operator fun invoke(): Flow<List<Sudoku>> =
        combine(userSettingsRepository.observeStatisticsFilterFlags(), sudokusRepository.observeAllSudokus()) { flags, sudokus ->
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
            sudokus.filter {
                (typeAll || (typeNormal && it.isNormalSudoku) || (typeDaily && it.isDailySudoku) || (typeLevel && it.isSudokuLevel)) &&
                        (sizeAll || (size4x4 && it.size == 4) || (size9x9 && it.size == 9) || (size16x16 && it.size == 16)) &&
                        (difficultyAll || (difficultyVeryEasy && it.difficulty == VERY_EASY) || (difficultyEasy && it.difficulty == EASY) ||
                                (difficultyMedium && it.difficulty == MEDIUM) || (difficultyHard && it.difficulty == HARD) ||
                                (difficultyExpert && it.difficulty == EXPERT))
            }
        }.flowOn(Dispatchers.Default)
}