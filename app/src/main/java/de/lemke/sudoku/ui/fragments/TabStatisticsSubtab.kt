package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.GetAllSudokusWithDifficultyUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Sudoku
import dev.oneuiproject.oneui.widget.MarginsTabLayout
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class TabStatisticsSubtab : Fragment() {
    private lateinit var rootView: View
    private lateinit var subTabs: MarginsTabLayout
    private lateinit var mainTabs: MarginsTabLayout
    private lateinit var viewPager2List: ViewPager2
    private lateinit var sudokus: List<Sudoku>
    private lateinit var textViewGamesStarted: TextView
    private lateinit var textViewGamesCompleted: TextView
    private lateinit var textViewWinRate: TextView
    private lateinit var textViewGamesWithoutErrors: TextView
    private lateinit var textViewMostErrors: TextView
    private lateinit var textViewAverageErrors: TextView
    private lateinit var textViewGamesWithoutAutoHints: TextView
    private lateinit var textViewGamesWithoutHints: TextView
    private lateinit var textViewMostHints: TextView
    private lateinit var textViewAverageHints: TextView
    private lateinit var textViewGamesWithoutNotes: TextView
    private lateinit var textViewMostNotes: TextView
    private lateinit var textViewAverageNotes: TextView
    private lateinit var textViewBestTime: TextView
    private lateinit var textViewAverageTime: TextView
    private lateinit var textViewCurrentStreak: TextView
    private lateinit var textViewBestStreak: TextView
    private lateinit var generalStatisticsLayout: LinearLayout
    private lateinit var textViewMostGamesStarted: TextView
    private lateinit var textViewMostGamesWon: TextView
    private var difficulty: Difficulty? = null

    @Inject
    lateinit var getAllSudokusWithDifficulty: GetAllSudokusWithDifficultyUseCase

    @Inject
    lateinit var getAllSudokus: GetAllSudokusUseCase

    companion object {
        fun newInstance(position: Int): TabStatisticsSubtab {
            val f = TabStatisticsSubtab()
            val args = Bundle()
            args.putInt("position", position)
            f.arguments = args
            return f
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        rootView = inflater.inflate(R.layout.fragment_tab_statistics_subtab, container, false)
        val intDifficulty = arguments?.getInt("position") ?: -1
        if (intDifficulty in 0..Difficulty.max) difficulty = Difficulty.fromInt(intDifficulty)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity()
        subTabs = activity.findViewById(R.id.fragment_statistics_sub_tabs)
        mainTabs = activity.findViewById(R.id.main_tabs)
        viewPager2List = activity.findViewById(R.id.statistics_viewpager)
        textViewGamesStarted = rootView.findViewById(R.id.games_statistic_total_started_value)
        textViewGamesCompleted = rootView.findViewById(R.id.games_statistic_total_completed_value)
        textViewWinRate = rootView.findViewById(R.id.games_statistic_win_rate_value)
        textViewGamesWithoutErrors = rootView.findViewById(R.id.games_statistic_wins_without_error_value)
        textViewMostErrors = rootView.findViewById(R.id.games_statistic_most_errors_value)
        textViewAverageErrors = rootView.findViewById(R.id.games_statistic_average_errors_value)
        textViewGamesWithoutAutoHints = rootView.findViewById(R.id.games_statistic_wins_without_auto_hint_value)
        textViewGamesWithoutHints = rootView.findViewById(R.id.games_statistic_wins_without_hint_value)
        textViewMostHints = rootView.findViewById(R.id.games_statistic_most_hints_value)
        textViewAverageHints = rootView.findViewById(R.id.games_statistic_average_hints_value)
        textViewGamesWithoutNotes = rootView.findViewById(R.id.games_statistic_wins_without_notes_value)
        textViewMostNotes = rootView.findViewById(R.id.games_statistic_most_notes_value)
        textViewAverageNotes = rootView.findViewById(R.id.games_statistic_average_notes_value)
        textViewBestTime = rootView.findViewById(R.id.time_statistic_best_time_value)
        textViewAverageTime = rootView.findViewById(R.id.time_statistic_average_time_value)
        textViewCurrentStreak = rootView.findViewById(R.id.streak_statistic_current_streak_value)
        textViewBestStreak = rootView.findViewById(R.id.streak_statistic_best_streak_value)
        generalStatisticsLayout = rootView.findViewById(R.id.general_statistics_layout)
        generalStatisticsLayout.visibility = if (difficulty != null) View.GONE else View.VISIBLE
        textViewMostGamesStarted = rootView.findViewById(R.id.difficulty_statistic_most_games_started_value)
        textViewMostGamesWon = rootView.findViewById(R.id.difficulty_statistic_most_games_won_value)
        lifecycleScope.launch {
            sudokus = if (difficulty != null) getAllSudokusWithDifficulty(difficulty!!) else getAllSudokus()
            updateStatistics()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateStatistics() {
        val gamesStarted = sudokus.size
        val gamesCompleted = sudokus.filter { it.completed }.size
        val winRate = if (gamesStarted == 0) 0 else (gamesCompleted.toFloat() / gamesStarted.toFloat() * 100).toInt()
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
        val bestTimeSudoku = sudokus.filter { it.completed }.minByOrNull { it.seconds }
        val bestTime = bestTimeSudoku?.seconds ?: 0
        val averageTime = if (gamesCompleted == 0) 0 else sudokus.filter { it.completed }.sumOf { it.seconds } / gamesCompleted
        val currentStreak = sudokus.takeWhile { it.completed }.size
        val bestStreak = if (gamesCompleted == 0) 0 else sudokus.windowed(2, 1).count { it[0].completed && it[1].completed } + 1
        val mostGamesStartedDifficultyInt = sudokus.groupingBy { it.difficulty.value }.eachCount().maxByOrNull { it.value }?.key
        val mostGamesStartedDifficulty =
            if (mostGamesStartedDifficultyInt == null) "-" else Difficulty.getLocalString(mostGamesStartedDifficultyInt, resources)
        val mostGamesWonDifficultyInt = sudokus.filter { it.completed }.groupingBy { it.difficulty.value }.eachCount().maxByOrNull { it.value }?.key
        val mostGamesWonDifficulty =
            if (mostGamesWonDifficultyInt == null) "-" else Difficulty.getLocalString(mostGamesWonDifficultyInt, resources)
        textViewGamesStarted.text = gamesStarted.toString()
        textViewGamesCompleted.text = gamesCompleted.toString()
        textViewWinRate.text = "$winRate%"
        textViewGamesWithoutErrors.text = gamesWithoutErrors.toString()
        textViewMostErrors.text = mostErrors.toString()
        textViewAverageErrors.text = averageErrors.toString()
        textViewGamesWithoutAutoHints.text = gamesWithoutAutoHints.toString()
        textViewGamesWithoutHints.text = gamesWithoutHints.toString()
        textViewMostHints.text = mostHints.toString()
        textViewAverageHints.text = averageHints.toString()
        textViewGamesWithoutNotes.text = gamesWithoutNotes.toString()
        textViewMostNotes.text = mostNotes.toString()
        textViewAverageNotes.text = averageNotes.toString()
        textViewBestTime.text = secondsToTimeString(bestTime) + if (bestTimeSudoku != null && difficulty == null) " (${bestTimeSudoku.difficulty.getLocalString(resources)})" else ""
        textViewAverageTime.text = secondsToTimeString(averageTime)
        textViewCurrentStreak.text = currentStreak.toString()
        textViewBestStreak.text = bestStreak.toString()
        textViewMostGamesStarted.text = mostGamesStartedDifficulty
        textViewMostGamesWon.text = mostGamesWonDifficulty
    }

    private fun secondsToTimeString(seconds: Int): String =
        if (seconds == 0) "--:--"
        else if (seconds >= 3600) String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
}