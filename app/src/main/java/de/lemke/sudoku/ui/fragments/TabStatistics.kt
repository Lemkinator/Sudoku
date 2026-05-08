package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewGroup.MarginLayoutParams
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabStatisticsBinding
import de.lemke.sudoku.domain.CalculateStatisticsUseCase
import de.lemke.sudoku.domain.ObserveSudokusAndStatisticsFilterFlagsUseCase
import de.lemke.sudoku.domain.model.SudokuStatistics
import kotlin.math.roundToInt
import de.lemke.sudoku.ui.fragments.TabStatistics.StatisticsListAdapter.ViewHolder
import dev.oneuiproject.oneui.recyclerview.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule.SELECTED
import dev.oneuiproject.oneui.utils.SemItemDecoration
import dev.oneuiproject.oneui.widget.Separator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class TabStatistics : Fragment() {
    private lateinit var binding: FragmentTabStatisticsBinding
    private var statisticsList: MutableList<Pair<String, String?>> = mutableListOf()

    @Inject
    lateinit var observeSudokusAndStatisticsFilterFlags: ObserveSudokusAndStatisticsFilterFlagsUseCase

    @Inject
    lateinit var calculateStatistics: CalculateStatisticsUseCase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentTabStatisticsBinding.inflate(inflater, container, false).also { binding = it }.root

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.statisticsListRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = StatisticsListAdapter()
            itemAnimator = null
            addItemDecoration(
                SemItemDecoration(
                    context,
                    dividerRule = SELECTED { it.itemViewType == 0 },
                    subHeaderRule = SELECTED { it.itemViewType == 1 })
            )
            enableCoreSeslFeatures()
        }
        //setupMenuProvider()
        lifecycleScope.launch {
            observeSudokusAndStatisticsFilterFlags().flowWithLifecycle(lifecycle, RESUMED).collectLatest {
                binding.statisticsProgressBar.isVisible = true
                updateStatistics(calculateStatistics(it))
                binding.statisticsListRecycler.adapter?.notifyDataSetChanged()
                binding.statisticsProgressBar.isVisible = false
            }
        }
    }

    /*
    private fun setupMenuProvider() = requireActivity().addMenuProvider(object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) = menuInflater.inflate(R.menu.menu_filter, menu)
        override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
            R.id.menu_item_filter -> showStatisticsFilterDialog().let { true }
            else -> false
        }
    }, viewLifecycleOwner, RESUMED)
     */

    @SuppressLint("SetTextI18n")
    private fun updateStatistics(stats: SudokuStatistics) {
        statisticsList = mutableListOf()
        statisticsList.add(getString(R.string.games) to null)
        statisticsList.add(getString(R.string.games_started) to stats.gamesStarted.toString())
        statisticsList.add(getString(R.string.games_completed) to stats.gamesCompleted.toString())
        statisticsList.add(getString(R.string.win_rate) to "${stats.winRate}%")
        statisticsList.add(getString(R.string.time) to null)
        statisticsList.add(
            getString(R.string.best_time) to secondsToTimeString(stats.bestTimeSudoku?.seconds ?: -1) +
                    if (stats.bestTimeSudoku != null) " (${stats.bestTimeSudoku.sizeString}, ${stats.bestTimeSudoku.difficulty.getLocalString(resources)})" else ""
        )
        statisticsList.add(getString(R.string.average_time) to secondsToTimeString(stats.averageTime))
        statisticsList.add(getString(R.string.total_time_played) to totalSecondsToString(stats.totalSecondsPlayed))
        statisticsList.add(getString(R.string.streaks) to null)
        statisticsList.add(getString(R.string.current_win_streak) to stats.currentGameStreak.toString())
        statisticsList.add(getString(R.string.best_win_streak) to stats.bestGameStreak.toString())
        statisticsList.add(getString(R.string.errors) to null)
        statisticsList.add(getString(R.string.wins_without_error) to stats.winsWithoutErrors.toString())
        statisticsList.add(getString(R.string.most_errors) to stats.mostErrors.toString())
        statisticsList.add(getString(R.string.average_errors) to stats.averageErrors.toString())
        statisticsList.add(getString(R.string.hints) to null)
        statisticsList.add(getString(R.string.wins_without_hint) to stats.winsWithoutHints.toString())
        statisticsList.add(getString(R.string.most_hints) to stats.mostHints.toString())
        statisticsList.add(getString(R.string.average_hints) to stats.averageHints.toString())
        statisticsList.add(getString(R.string.notes) to null)
        statisticsList.add(getString(R.string.wins_without_notes) to stats.winsWithoutNotes.toString())
        statisticsList.add(getString(R.string.most_notes) to stats.mostNotes.toString())
        statisticsList.add(getString(R.string.average_notes) to stats.averageNotes.toString())
        statisticsList.add(getString(R.string.difficulty) to null)
        statisticsList.add(getString(R.string.most_games_started) to (stats.mostGamesStartedDifficulty?.getLocalString(resources) ?: "-"))
        statisticsList.add(getString(R.string.most_games_won) to (stats.mostGamesWonDifficulty?.getLocalString(resources) ?: "-"))
        statisticsList.add(getString(R.string.size) to null)
        statisticsList.add(getString(R.string.most_games_started) to (stats.mostGamesStartedSize?.let { "$it×$it" } ?: "-"))
        statisticsList.add(getString(R.string.most_games_won) to (stats.mostGamesWonSize?.let { "$it×$it" } ?: "-"))
        statisticsList.add(getString(R.string.feature_usage) to null)
        statisticsList.add(getString(R.string.hint_usage_rate) to "${(stats.hintUsageRate * 100).roundToInt()}%")
        statisticsList.add(getString(R.string.notes_usage_rate) to "${(stats.notesUsageRate * 100).roundToInt()}%")
        statisticsList.add(getString(R.string.eraser_usage_rate) to "${(stats.eraserUsageRate * 100).roundToInt()}%")
        statisticsList.add(getString(R.string.perfect_games) to stats.perfectGames.toString())
    }

    private fun totalSecondsToString(seconds: Long): String {
        if (seconds < 60L) return "-"
        val days = seconds / 86400
        val hours = seconds % 86400 / 3600
        val minutes = seconds % 3600 / 60
        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    private fun secondsToTimeString(seconds: Int): String =
        when {
            seconds < 0 -> "--:--"
            seconds >= 3600 -> String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            else -> String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
        }

    inner class StatisticsListAdapter : RecyclerView.Adapter<ViewHolder>() {
        override fun getItemCount(): Int = statisticsList.size
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getItemViewType(position: Int): Int = if (statisticsList[position].second == null) 1 else 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
            0 -> ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.statistics_list_item, parent, false), false)
            else -> ViewHolder(Separator(requireContext()), true).apply {
                itemView.layoutParams = MarginLayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
        }


        @SuppressLint("SetTextI18n", "StringFormatInvalid")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.text = statisticsList[position].first
            if (!holder.isSeparator) holder.textViewValue.text = statisticsList[position].second
        }

        inner class ViewHolder internal constructor(itemView: View, var isSeparator: Boolean) : RecyclerView.ViewHolder(itemView) {
            var textView: TextView
            lateinit var textViewValue: TextView

            init {
                if (isSeparator) textView = itemView as TextView
                else {
                    textView = itemView.findViewById(R.id.item_text)
                    textViewValue = itemView.findViewById(R.id.item_text_value)
                }
            }
        }
    }
}