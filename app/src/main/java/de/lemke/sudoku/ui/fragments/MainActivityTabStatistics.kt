package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.app.ActionBar.LayoutParams
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.MarginLayoutParams
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.util.SeslRoundedCorner
import androidx.appcompat.util.SeslSubheaderRoundedCorner
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabStatisticsBinding
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.ui.fragments.MainActivityTabStatistics.StatisticsListAdapter.ViewHolder
import dev.oneuiproject.oneui.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.widget.Separator
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivityTabStatistics : Fragment() {
    private lateinit var binding: FragmentTabStatisticsBinding
    private var statisticsList: MutableList<Pair<String, String?>> = mutableListOf()

    @Inject
    lateinit var getAllSudokus: GetAllSudokusUseCase

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentTabStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { updateStatistics() }
    }

    @SuppressLint("SetTextI18n")
    private suspend fun updateStatistics() {
        val sudokus = getAllSudokus(getUserSettings().statisticsFilterFlags) //.filter { !it.autoNotesUsed }
        statisticsList = mutableListOf()
        val gamesStarted = sudokus.size
        val gamesCompleted = sudokus.filter { it.completed }.size
        val winRate = if (gamesStarted == 0) 0 else (gamesCompleted.toFloat() / gamesStarted.toFloat() * 100).toInt()
        val bestTimeSudoku = sudokus.filter { it.completed }.minByOrNull { it.seconds }
        val bestTime = bestTimeSudoku?.seconds ?: -1
        val averageTime = if (gamesCompleted == 0) 0 else sudokus.filter { it.completed }.sumOf { it.seconds } / gamesCompleted
        val winsWithoutErrors = sudokus.filter { it.completed && it.errorsMade == 0 }.size
        val mostErrors = sudokus.maxByOrNull { it.errorsMade }?.errorsMade ?: 0
        val averageErrors = if (gamesCompleted == 0) 0 else sudokus.filter { it.completed }.sumOf { it.errorsMade } / gamesCompleted
        //val winsWithoutAutoHints = sudokus.filter { it.completed && !it.autoNotesUsed }.size
        val winsWithoutHints = sudokus.filter { it.completed && it.hintsUsed == 0 }.size
        val mostHints = sudokus.maxByOrNull { it.hintsUsed }?.hintsUsed ?: 0
        val averageHints = if (gamesCompleted == 0) 0 else sudokus.filter { it.completed }.sumOf { it.hintsUsed } / gamesCompleted
        val winsWithoutNotes = sudokus.filter { it.completed && it.notesMade == 0 }.size
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
        statisticsList.add(getString(R.string.games) to null)
        statisticsList.add(getString(R.string.games_started) to gamesStarted.toString())
        statisticsList.add(getString(R.string.games_completed) to gamesCompleted.toString())
        statisticsList.add(getString(R.string.win_rate) to "$winRate%")
        statisticsList.add(getString(R.string.time) to null)
        statisticsList.add(
            getString(R.string.best_time) to
                    secondsToTimeString(bestTime) + if (bestTimeSudoku != null) " (${bestTimeSudoku.sizeString}, ${
                bestTimeSudoku.difficulty.getLocalString(resources)
            })" else ""
        )
        statisticsList.add(getString(R.string.average_time) to secondsToTimeString(averageTime))
        statisticsList.add(getString(R.string.errors) to null)
        statisticsList.add(getString(R.string.wins_without_error) to winsWithoutErrors.toString())
        statisticsList.add(getString(R.string.most_errors) to mostErrors.toString())
        statisticsList.add(getString(R.string.average_errors) to averageErrors.toString())
        statisticsList.add(getString(R.string.hints) to null)
        //statisticsList.add(getString(R.string.wins_without_auto_hint) to winsWithoutAutoHints.toString())
        statisticsList.add(getString(R.string.wins_without_hint) to winsWithoutHints.toString())
        statisticsList.add(getString(R.string.most_hints) to mostHints.toString())
        statisticsList.add(getString(R.string.average_hints) to averageHints.toString())
        statisticsList.add(getString(R.string.notes) to null)
        statisticsList.add(getString(R.string.wins_without_notes) to winsWithoutNotes.toString())
        statisticsList.add(getString(R.string.most_notes) to mostNotes.toString())
        statisticsList.add(getString(R.string.average_notes) to averageNotes.toString())
        statisticsList.add(getString(R.string.difficulty) to null)
        statisticsList.add(getString(R.string.most_games_started) to mostGamesStartedDifficulty)
        statisticsList.add(getString(R.string.most_games_won) to mostGamesWonDifficulty)
        statisticsList.add(getString(R.string.size) to null)
        statisticsList.add(getString(R.string.most_games_started) to mostGamesStartedSize)
        statisticsList.add(getString(R.string.most_games_won) to mostGamesWonSize)

        binding.statisticsListRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = StatisticsListAdapter()
            itemAnimator = null
            addItemDecoration(ItemDecoration(requireContext()))
            enableCoreSeslFeatures()
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
                itemView.layoutParams = MarginLayoutParams(MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }
        }


        @SuppressLint("SetTextI18n", "StringFormatInvalid")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (holder.isSeparator) {
                holder.textView.text = statisticsList[position].first
            } else {
                holder.textView.text = statisticsList[position].first
                holder.textViewValue.text = statisticsList[position].second
            }
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

    @SuppressLint("PrivateResource")
    inner class ItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
        private val divider: Drawable?
        private val roundedCorner: SeslSubheaderRoundedCorner
        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            super.onDraw(c, parent, state)
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val holder: ViewHolder =
                    binding.statisticsListRecycler.getChildViewHolder(child) as ViewHolder
                if (!holder.isSeparator) {
                    val top = (child.bottom + (child.layoutParams as MarginLayoutParams).bottomMargin)
                    val bottom = divider!!.intrinsicHeight + top
                    divider.setBounds(parent.left, top, parent.right, bottom)
                    divider.draw(c)
                }
            }
        }

        override fun seslOnDispatchDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val holder: ViewHolder =
                    binding.statisticsListRecycler.getChildViewHolder(child) as ViewHolder
                if (holder.isSeparator) roundedCorner.drawRoundedCorner(child, c)
            }
        }

        init {
            val outValue = TypedValue()
            context.theme.resolveAttribute(androidx.appcompat.R.attr.isLightTheme, outValue, true)
            divider = AppCompatResources.getDrawable(
                context,
                if (outValue.data == 0) androidx.appcompat.R.drawable.sesl_list_divider_dark
                else androidx.appcompat.R.drawable.sesl_list_divider_light
            )!!
            roundedCorner = SeslSubheaderRoundedCorner(context)
            roundedCorner.roundedCorners = SeslRoundedCorner.ROUNDED_CORNER_ALL
        }
    }
}