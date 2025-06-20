package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.transformToActivity
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabSudokuBinding
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetRecentlyUpdatedNormalSudokuUseCase
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.IsDailySudokuCompletedUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.ui.DailySudokuActivity
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.SudokuLevelActivity
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.ktx.onSingleClick
import dev.oneuiproject.oneui.layout.DrawerLayout
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivityTabSudoku : Fragment(), ViewYTranslator by AppBarAwareYTranslator() {
    private lateinit var binding: FragmentTabSudokuBinding

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var generateSudoku: GenerateSudokuUseCase

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Inject
    lateinit var getRecentSudoku: GetRecentlyUpdatedNormalSudokuUseCase

    @Inject
    lateinit var isDailySudokuCompleted: IsDailySudokuCompletedUseCase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentTabSudokuBinding.inflate(inflater, container, false).also { binding = it }.root

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.newSudokuLayout.translateYWithAppBar(requireActivity().findViewById<DrawerLayout>(R.id.drawerLayout).appBarLayout, this)
        binding.sizeSeekbar.setSeamless(true)
        binding.difficultySeekbar.setSeamless(true)
        binding.difficultySeekbar.max = Difficulty.max
        binding.newGameButton.onSingleClick {
            binding.newSudokuProgressBar.visibility = VISIBLE
            lifecycleScope.launch {
                val sudoku = generateSudoku(binding.sizeSeekbar.sudokuSize, Difficulty.fromInt(binding.difficultySeekbar.progress))
                saveSudoku(sudoku)
                binding.newGameButton.transformToActivity(
                    Intent(requireActivity(), SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
                )
                binding.newSudokuProgressBar.visibility = INVISIBLE
            }
        }
        binding.dailyButton.onSingleClick {
            binding.dailyButton.transformToActivity(
                Intent(requireActivity(), DailySudokuActivity::class.java),
                "DailySudokuActivityTransition" // transitionNames should be unique within the view hierarchy
            )
        }
        binding.dailyAvailableButton.onSingleClick {
            binding.dailyAvailableButton.transformToActivity(
                Intent(requireActivity(), DailySudokuActivity::class.java),
                "DailySudokuActivityTransition" // transitionNames should be unique within the view hierarchy
            )
        }
        binding.levelsButton.onSingleClick {
            binding.levelsButton.transformToActivity(
                Intent(requireActivity(), SudokuLevelActivity::class.java),
                "SudokuLevelActivityTransition" // transitionNames should be unique within the view hierarchy
            )
        }
        lifecycleScope.launch {
            val userSettings = getUserSettings()
            binding.difficultySeekbar.progress = userSettings.difficultySliderValue
            binding.difficultySeekbar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeslSeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeslSeekBar?) {}
                override fun onProgressChanged(seekBar: SeslSeekBar?, progress: Int, fromUser: Boolean) {
                    lifecycleScope.launch { updateUserSettings { it.copy(difficultySliderValue = progress) } }
                }
            })
            binding.sizeSeekbar.progress = userSettings.sizeSliderValue
            binding.sizeSeekbar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeslSeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeslSeekBar?) {}
                override fun onProgressChanged(seekBar: SeslSeekBar?, progress: Int, fromUser: Boolean) {
                    lifecycleScope.launch { updateUserSettings { it.copy(sizeSliderValue = progress) } }
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val sudoku = getRecentSudoku()
            if (sudoku != null && !sudoku.completed && !sudoku.errorLimitReached(getUserSettings().errorLimit)) {
                binding.continueGameButton.isVisible = true
                binding.continueGameButton.text = getString(
                    R.string.continue_game,
                    sudoku.sizeString,
                    sudoku.difficulty.getLocalString(resources)
                )
                binding.continueGameButton.onSingleClick {
                    binding.continueGameButton.transformToActivity(
                        Intent(requireActivity(), SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
                    )
                }
            } else binding.continueGameButton.isVisible = false
            isDailySudokuCompleted().let {
                binding.dailyAvailableButton.isVisible = !it
                binding.dailyButton.isVisible = it
            }
        }
    }

    private val SeslSeekBar.sudokuSize: Int
        get() = when (this.progress) {
            0 -> 4
            1 -> 9
            2 -> 16
            else -> 9
        }
}
