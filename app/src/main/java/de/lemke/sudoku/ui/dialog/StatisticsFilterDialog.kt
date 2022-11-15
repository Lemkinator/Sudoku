package de.lemke.sudoku.ui.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.DialogStatisticsFilterBinding
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class StatisticsFilterDialog(private val onDismissListener: DialogInterface.OnDismissListener) : DialogFragment(),
    CompoundButton.OnCheckedChangeListener {
    lateinit var binding: DialogStatisticsFilterBinding

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener.onDismiss(dialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogStatisticsFilterBinding.inflate(layoutInflater)
        updateFlags()
        lifecycleScope.launch {
            val userSettings = getUserSettings()
            binding.checkboxStatisticsFilterNormal.isChecked =
                userSettings.statisticsFilterFlags and GetAllSudokusUseCase.TYPE_NORMAL != 0 ||
                        userSettings.statisticsFilterFlags and GetAllSudokusUseCase.TYPE_ALL != 0
            binding.checkboxStatisticsFilterDaily.isChecked = userSettings.statisticsFilterFlags and GetAllSudokusUseCase.TYPE_DAILY != 0 ||
                    userSettings.statisticsFilterFlags and GetAllSudokusUseCase.TYPE_ALL != 0
            binding.checkboxStatisticsFilterLevel.isChecked = userSettings.statisticsFilterFlags and GetAllSudokusUseCase.TYPE_LEVEL != 0 ||
                    userSettings.statisticsFilterFlags and GetAllSudokusUseCase.TYPE_ALL != 0
            binding.checkboxStatisticsFilterSize4.isChecked = userSettings.statisticsFilterFlags and GetAllSudokusUseCase.SIZE_4X4 != 0 ||
                    userSettings.statisticsFilterFlags and GetAllSudokusUseCase.SIZE_ALL != 0
            binding.checkboxStatisticsFilterSize9.isChecked = userSettings.statisticsFilterFlags and GetAllSudokusUseCase.SIZE_9X9 != 0 ||
                    userSettings.statisticsFilterFlags and GetAllSudokusUseCase.SIZE_ALL != 0
            binding.checkboxStatisticsFilterSize16.isChecked =
                userSettings.statisticsFilterFlags and GetAllSudokusUseCase.SIZE_16X16 != 0 ||
                        userSettings.statisticsFilterFlags and GetAllSudokusUseCase.SIZE_ALL != 0
            binding.checkboxStatisticsFilterDifficultyVeryEasy.isChecked =
                userSettings.statisticsFilterFlags and GetAllSudokusUseCase.DIFFICULTY_VERY_EASY != 0 ||
                        userSettings.statisticsFilterFlags and GetAllSudokusUseCase.DIFFICULTY_ALL != 0
            binding.checkboxStatisticsFilterDifficultyEasy.isChecked =
                userSettings.statisticsFilterFlags and GetAllSudokusUseCase.DIFFICULTY_EASY != 0 ||
                        userSettings.statisticsFilterFlags and GetAllSudokusUseCase.DIFFICULTY_ALL != 0
            binding.checkboxStatisticsFilterDifficultyMedium.isChecked =
                userSettings.statisticsFilterFlags and GetAllSudokusUseCase.DIFFICULTY_MEDIUM != 0 ||
                        userSettings.statisticsFilterFlags and GetAllSudokusUseCase.DIFFICULTY_ALL != 0
            binding.checkboxStatisticsFilterDifficultyHard.isChecked =
                userSettings.statisticsFilterFlags and GetAllSudokusUseCase.DIFFICULTY_HARD != 0 ||
                        userSettings.statisticsFilterFlags and GetAllSudokusUseCase.DIFFICULTY_ALL != 0
            binding.checkboxStatisticsFilterDifficultyExpert.isChecked =
                userSettings.statisticsFilterFlags and GetAllSudokusUseCase.DIFFICULTY_EXPERT != 0 ||
                        userSettings.statisticsFilterFlags and GetAllSudokusUseCase.DIFFICULTY_ALL != 0
        }
        binding.checkboxStatisticsFilterNormal.setOnCheckedChangeListener(this)
        binding.checkboxStatisticsFilterDaily.setOnCheckedChangeListener(this)
        binding.checkboxStatisticsFilterLevel.setOnCheckedChangeListener(this)
        binding.checkboxStatisticsFilterSize4.setOnCheckedChangeListener(this)
        binding.checkboxStatisticsFilterSize9.setOnCheckedChangeListener(this)
        binding.checkboxStatisticsFilterSize16.setOnCheckedChangeListener(this)
        binding.checkboxStatisticsFilterDifficultyVeryEasy.setOnCheckedChangeListener(this)
        binding.checkboxStatisticsFilterDifficultyEasy.setOnCheckedChangeListener(this)
        binding.checkboxStatisticsFilterDifficultyMedium.setOnCheckedChangeListener(this)
        binding.checkboxStatisticsFilterDifficultyHard.setOnCheckedChangeListener(this)
        binding.checkboxStatisticsFilterDifficultyExpert.setOnCheckedChangeListener(this)

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.statistics_filter)
            .setView(binding.root)
            .setNeutralButton(R.string.ok) { _, _ -> dismiss() }
            .setOnDismissListener { onDismissListener.onDismiss(it) }
        return builder.create()

    }

    private fun updateFlags() {
        var flags = 0
        if (binding.checkboxStatisticsFilterNormal.isChecked) flags = flags or GetAllSudokusUseCase.TYPE_NORMAL
        if (binding.checkboxStatisticsFilterDaily.isChecked) flags = flags or GetAllSudokusUseCase.TYPE_DAILY
        if (binding.checkboxStatisticsFilterLevel.isChecked) flags = flags or GetAllSudokusUseCase.TYPE_LEVEL
        if (flags and GetAllSudokusUseCase.TYPE_NORMAL != 0 &&
            flags and GetAllSudokusUseCase.TYPE_DAILY != 0 &&
            flags and GetAllSudokusUseCase.TYPE_LEVEL != 0
        ) {
            flags = flags or GetAllSudokusUseCase.TYPE_ALL
        }
        if (binding.checkboxStatisticsFilterSize4.isChecked) flags = flags or GetAllSudokusUseCase.SIZE_4X4
        if (binding.checkboxStatisticsFilterSize9.isChecked) flags = flags or GetAllSudokusUseCase.SIZE_9X9
        if (binding.checkboxStatisticsFilterSize16.isChecked) flags = flags or GetAllSudokusUseCase.SIZE_16X16
        if (flags and GetAllSudokusUseCase.SIZE_4X4 != 0 &&
            flags and GetAllSudokusUseCase.SIZE_9X9 != 0 &&
            flags and GetAllSudokusUseCase.SIZE_16X16 != 0
        ) {
            flags = flags or GetAllSudokusUseCase.SIZE_ALL
        }
        if (binding.checkboxStatisticsFilterDifficultyVeryEasy.isChecked) flags = flags or GetAllSudokusUseCase.DIFFICULTY_VERY_EASY
        if (binding.checkboxStatisticsFilterDifficultyEasy.isChecked) flags = flags or GetAllSudokusUseCase.DIFFICULTY_EASY
        if (binding.checkboxStatisticsFilterDifficultyMedium.isChecked) flags = flags or GetAllSudokusUseCase.DIFFICULTY_MEDIUM
        if (binding.checkboxStatisticsFilterDifficultyHard.isChecked) flags = flags or GetAllSudokusUseCase.DIFFICULTY_HARD
        if (binding.checkboxStatisticsFilterDifficultyExpert.isChecked) flags = flags or GetAllSudokusUseCase.DIFFICULTY_EXPERT
        if (flags and GetAllSudokusUseCase.DIFFICULTY_VERY_EASY != 0 &&
            flags and GetAllSudokusUseCase.DIFFICULTY_EASY != 0 &&
            flags and GetAllSudokusUseCase.DIFFICULTY_MEDIUM != 0 &&
            flags and GetAllSudokusUseCase.DIFFICULTY_HARD != 0 &&
            flags and GetAllSudokusUseCase.DIFFICULTY_EXPERT != 0
        ) {
            flags = flags or GetAllSudokusUseCase.DIFFICULTY_ALL
        }
        lifecycleScope.launch { updateUserSettings { it.copy(statisticsFilterFlags = flags) } }
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        updateFlags()
    }
}