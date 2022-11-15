package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabStatisticsBinding
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.ui.dialog.StatisticsFilterDialog
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivityTabStatistics : Fragment() {
    private lateinit var binding: FragmentTabStatisticsBinding
    private lateinit var sudokus: List<Sudoku>

    @Inject
    lateinit var getAllSudokus: GetAllSudokusUseCase

    @Inject
    lateinit var getAllDailySudokus: GetAllSudokusUseCase

    @Inject
    lateinit var getAllSudokuLevels: GetAllSudokusUseCase

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentTabStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { updateStatistics() }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.main_menu_statistics, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.main_menu_statistics_filter -> {
                StatisticsFilterDialog { lifecycleScope.launch { onResume() } }.show(childFragmentManager, "StatisticsFilterDialog")
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n")
    private suspend fun updateStatistics() {
        sudokus = getAllSudokus(getUserSettings().statisticsFilterFlags)

        val gamesStarted = sudokus.size
        val gamesCompleted = sudokus.filter { it.completed }.size
        val winRate = if (gamesStarted == 0) 0 else (gamesCompleted.toFloat() / gamesStarted.toFloat() * 100).toInt()
        val bestTimeSudoku = sudokus.filter { it.completed }.minByOrNull { it.seconds }
        val bestTime = bestTimeSudoku?.seconds ?: 0
        val averageTime = if (gamesCompleted == 0) 0 else sudokus.filter { it.completed }.sumOf { it.seconds } / gamesCompleted
        val gamesWithoutErrors = sudokus.filter { it.completed && it.errorsMade == 0 }.size
        val mostErrors = sudokus.maxByOrNull { it.errorsMade }?.errorsMade ?: 0
        val averageErrors = if (gamesCompleted == 0) 0 else sudokus.filter { it.completed }.sumOf { it.errorsMade } / gamesCompleted
        val gamesWithoutAutoHints = sudokus.filter { it.completed && !it.autoNotesUsed }.size
        val gamesWithoutHints = sudokus.filter { it.completed && it.hintsUsed == 0 }.size
        val mostHints = sudokus.maxByOrNull { it.hintsUsed }?.hintsUsed ?: 0
        val averageHints = if (gamesCompleted == 0) 0 else sudokus.filter { it.completed }.sumOf { it.hintsUsed } / gamesCompleted
        val gamesWithoutNotes = sudokus.filter { it.completed && it.notesMade == 0 }.size
        val mostNotes = sudokus.maxByOrNull { it.notesMade }?.notesMade ?: 0
        val averageNotes = if (gamesCompleted == 0) 0 else sudokus.filter { it.completed }.sumOf { it.notesMade } / gamesCompleted
        val mostGamesStartedDifficultyInt = sudokus.groupingBy { it.difficulty.value }.eachCount().maxByOrNull { it.value }?.key
        val mostGamesStartedDifficulty =
            if (mostGamesStartedDifficultyInt == null) "-" else Difficulty.getLocalString(mostGamesStartedDifficultyInt, resources)
        val mostGamesWonDifficultyInt =
            sudokus.filter { it.completed }.groupingBy { it.difficulty.value }.eachCount().maxByOrNull { it.value }?.key
        val mostGamesWonDifficulty =
            if (mostGamesWonDifficultyInt == null) "-" else Difficulty.getLocalString(mostGamesWonDifficultyInt, resources)
        val mostGamesStartedSizeInt = sudokus.groupingBy { it.size }.eachCount().maxByOrNull { it.value }?.key
        val mostGamesStartedSize = if (mostGamesStartedSizeInt == null) "-" else "$mostGamesStartedSizeInt×$mostGamesStartedSizeInt"
        val mostGamesWonSizeInt = sudokus.filter { it.completed }.groupingBy { it.size }.eachCount().maxByOrNull { it.value }?.key
        val mostGamesWonSize = if (mostGamesWonSizeInt == null) "-" else "$mostGamesWonSizeInt×$mostGamesWonSizeInt"
        binding.gamesStatisticTotalStartedValue.text = gamesStarted.toString()
        binding.gamesStatisticTotalCompletedValue.text = gamesCompleted.toString()
        binding.gamesStatisticWinRateValue.text = "$winRate%"
        binding.timeStatisticBestTimeValue.text =
            secondsToTimeString(bestTime) + if (bestTimeSudoku != null) " (${
                bestTimeSudoku.difficulty.getLocalString(resources)
            })" else ""
        binding.timeStatisticAverageTimeValue.text = secondsToTimeString(averageTime)
        binding.gamesStatisticWinsWithoutErrorValue.text = gamesWithoutErrors.toString()
        binding.gamesStatisticMostErrorsValue.text = mostErrors.toString()
        binding.gamesStatisticAverageErrorsValue.text = averageErrors.toString()
        binding.gamesStatisticWinsWithoutAutoHintValue.text = gamesWithoutAutoHints.toString()
        binding.gamesStatisticWinsWithoutHintValue.text = gamesWithoutHints.toString()
        binding.gamesStatisticMostHintsValue.text = mostHints.toString()
        binding.gamesStatisticAverageHintsValue.text = averageHints.toString()
        binding.gamesStatisticWinsWithoutNotesValue.text = gamesWithoutNotes.toString()
        binding.gamesStatisticMostNotesValue.text = mostNotes.toString()
        binding.gamesStatisticAverageNotesValue.text = averageNotes.toString()
        binding.difficultyStatisticMostGamesStartedValue.text = mostGamesStartedDifficulty
        binding.difficultyStatisticMostGamesWonValue.text = mostGamesWonDifficulty
        binding.sizeStatisticMostGamesStartedValue.text = mostGamesStartedSize
        binding.sizeStatisticMostGamesWonValue.text = mostGamesWonSize
    }

    private fun secondsToTimeString(seconds: Int): String =
        if (seconds == 0) "--:--"
        else if (seconds >= 3600) String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)

}