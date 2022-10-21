package de.lemke.sudoku.ui.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.DialogStatisticsFilterBinding
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class StatisticsFilterDialog(private val onDismissListener: DialogInterface.OnDismissListener) : DialogFragment() {

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener.onDismiss(dialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogStatisticsFilterBinding.inflate(layoutInflater)
        lifecycleScope.launch {
            val userSettings = getUserSettings()
            binding.radioGroupDifficulty.check(
                when (userSettings.statisticsFilterDifficulty) {
                    -1 -> R.id.radio_button_difficulty_all
                    0 -> R.id.radio_button_difficulty_very_easy
                    1 -> R.id.radio_button_difficulty_easy
                    2 -> R.id.radio_button_difficulty_medium
                    3 -> R.id.radio_button_difficulty_hard
                    4 -> R.id.radio_button_difficulty_expert
                    else -> R.id.radio_button_difficulty_all
                }
            )
            binding.checkboxStatisticsFilterIncludeNormal.isChecked = userSettings.statisticsFilterIncludeNormal
            binding.checkboxStatisticsFilterIncludeDaily.isChecked = userSettings.statisticsFilterIncludeDaily
            binding.checkboxStatisticsFilterIncludeLevels.isChecked = userSettings.statisticsFilterIncludeLevels
        }
        binding.radioGroupDifficulty.setOnCheckedChangeListener { _, checkedId ->
            lifecycleScope.launch {
                val newDifficulty = when (checkedId) {
                    R.id.radio_button_difficulty_all -> -1
                    R.id.radio_button_difficulty_very_easy -> 0
                    R.id.radio_button_difficulty_easy -> 1
                    R.id.radio_button_difficulty_medium -> 2
                    R.id.radio_button_difficulty_hard -> 3
                    R.id.radio_button_difficulty_expert -> 4
                    else -> -1
                }
                updateUserSettings { it.copy(statisticsFilterDifficulty = newDifficulty) }
            }
        }
        binding.checkboxStatisticsFilterIncludeNormal.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                updateUserSettings { it.copy(statisticsFilterIncludeNormal = isChecked) }
            }
        }
        binding.checkboxStatisticsFilterIncludeDaily.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                updateUserSettings { it.copy(statisticsFilterIncludeDaily = isChecked) }
            }
        }
        binding.checkboxStatisticsFilterIncludeLevels.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                updateUserSettings { it.copy(statisticsFilterIncludeLevels = isChecked) }
            }
        }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.statistics_filter)
            .setView(binding.root)
            .setNeutralButton(R.string.ok) { _, _ -> dismiss() }
            .setOnDismissListener { onDismissListener.onDismiss(it) }
        return builder.create()

    }
}