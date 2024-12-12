package de.lemke.sudoku.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.databinding.BottomsheetStatisticsFilterBinding
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
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.apply

@AndroidEntryPoint
class FilterBottomSheet: BottomSheetDialogFragment() {
    private lateinit var binding: BottomsheetStatisticsFilterBinding

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = BottomsheetStatisticsFilterBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            behavior.skipCollapsed = true
            setOnShowListener { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateFlags()
        lifecycleScope.launch {
            val userSettings = getUserSettings()
            binding.checkboxStatisticsFilterNormal.isChecked = userSettings.statisticsFilterFlags and TYPE_NORMAL != 0 ||
                    userSettings.statisticsFilterFlags and TYPE_ALL != 0
            binding.checkboxStatisticsFilterDaily.isChecked = userSettings.statisticsFilterFlags and TYPE_DAILY != 0 ||
                    userSettings.statisticsFilterFlags and TYPE_ALL != 0
            binding.checkboxStatisticsFilterLevel.isChecked = userSettings.statisticsFilterFlags and TYPE_LEVEL != 0 ||
                    userSettings.statisticsFilterFlags and TYPE_ALL != 0
            binding.checkboxStatisticsFilterSize4.isChecked = userSettings.statisticsFilterFlags and SIZE_4X4 != 0 ||
                    userSettings.statisticsFilterFlags and SIZE_ALL != 0
            binding.checkboxStatisticsFilterSize9.isChecked = userSettings.statisticsFilterFlags and SIZE_9X9 != 0 ||
                    userSettings.statisticsFilterFlags and SIZE_ALL != 0
            binding.checkboxStatisticsFilterSize16.isChecked = userSettings.statisticsFilterFlags and SIZE_16X16 != 0 ||
                    userSettings.statisticsFilterFlags and SIZE_ALL != 0
            binding.checkboxStatisticsFilterDifficultyVeryEasy.isChecked =
                userSettings.statisticsFilterFlags and DIFFICULTY_VERY_EASY != 0 ||
                        userSettings.statisticsFilterFlags and DIFFICULTY_ALL != 0
            binding.checkboxStatisticsFilterDifficultyEasy.isChecked = userSettings.statisticsFilterFlags and DIFFICULTY_EASY != 0 ||
                    userSettings.statisticsFilterFlags and DIFFICULTY_ALL != 0
            binding.checkboxStatisticsFilterDifficultyMedium.isChecked = userSettings.statisticsFilterFlags and DIFFICULTY_MEDIUM != 0 ||
                    userSettings.statisticsFilterFlags and DIFFICULTY_ALL != 0
            binding.checkboxStatisticsFilterDifficultyHard.isChecked = userSettings.statisticsFilterFlags and DIFFICULTY_HARD != 0 ||
                    userSettings.statisticsFilterFlags and DIFFICULTY_ALL != 0
            binding.checkboxStatisticsFilterDifficultyExpert.isChecked = userSettings.statisticsFilterFlags and DIFFICULTY_EXPERT != 0 ||
                    userSettings.statisticsFilterFlags and DIFFICULTY_ALL != 0
        }
        binding.checkboxStatisticsFilterNormal.setOnCheckedChangeListener { _, _ -> updateFlags() }
        binding.checkboxStatisticsFilterDaily.setOnCheckedChangeListener { _, _ -> updateFlags() }
        binding.checkboxStatisticsFilterLevel.setOnCheckedChangeListener { _, _ -> updateFlags() }
        binding.checkboxStatisticsFilterSize4.setOnCheckedChangeListener { _, _ -> updateFlags() }
        binding.checkboxStatisticsFilterSize9.setOnCheckedChangeListener { _, _ -> updateFlags() }
        binding.checkboxStatisticsFilterSize16.setOnCheckedChangeListener { _, _ -> updateFlags() }
        binding.checkboxStatisticsFilterDifficultyVeryEasy.setOnCheckedChangeListener { _, _ -> updateFlags() }
        binding.checkboxStatisticsFilterDifficultyEasy.setOnCheckedChangeListener { _, _ -> updateFlags() }
        binding.checkboxStatisticsFilterDifficultyMedium.setOnCheckedChangeListener { _, _ -> updateFlags() }
        binding.checkboxStatisticsFilterDifficultyHard.setOnCheckedChangeListener { _, _ -> updateFlags() }
        binding.checkboxStatisticsFilterDifficultyExpert.setOnCheckedChangeListener { _, _ -> updateFlags() }
    }

    private fun updateFlags() {
        var flags = 0
        if (binding.checkboxStatisticsFilterNormal.isChecked) flags = flags or TYPE_NORMAL
        if (binding.checkboxStatisticsFilterDaily.isChecked) flags = flags or TYPE_DAILY
        if (binding.checkboxStatisticsFilterLevel.isChecked) flags = flags or TYPE_LEVEL
        if (flags and TYPE_NORMAL != 0 && flags and TYPE_DAILY != 0 && flags and TYPE_LEVEL != 0) {
            flags = flags or TYPE_ALL
        }
        if (binding.checkboxStatisticsFilterSize4.isChecked) flags = flags or SIZE_4X4
        if (binding.checkboxStatisticsFilterSize9.isChecked) flags = flags or SIZE_9X9
        if (binding.checkboxStatisticsFilterSize16.isChecked) flags = flags or SIZE_16X16
        if (flags and SIZE_4X4 != 0 && flags and SIZE_9X9 != 0 && flags and SIZE_16X16 != 0) {
            flags = flags or SIZE_ALL
        }
        if (binding.checkboxStatisticsFilterDifficultyVeryEasy.isChecked) flags = flags or DIFFICULTY_VERY_EASY
        if (binding.checkboxStatisticsFilterDifficultyEasy.isChecked) flags = flags or DIFFICULTY_EASY
        if (binding.checkboxStatisticsFilterDifficultyMedium.isChecked) flags = flags or DIFFICULTY_MEDIUM
        if (binding.checkboxStatisticsFilterDifficultyHard.isChecked) flags = flags or DIFFICULTY_HARD
        if (binding.checkboxStatisticsFilterDifficultyExpert.isChecked) flags = flags or DIFFICULTY_EXPERT
        if (flags and DIFFICULTY_VERY_EASY != 0 &&
            flags and DIFFICULTY_EASY != 0 &&
            flags and DIFFICULTY_MEDIUM != 0 &&
            flags and DIFFICULTY_HARD != 0 &&
            flags and DIFFICULTY_EXPERT != 0
        ) {
            flags = flags or DIFFICULTY_ALL
        }
        lifecycleScope.launch { updateUserSettings { it.copy(statisticsFilterFlags = flags) } }
    }
}