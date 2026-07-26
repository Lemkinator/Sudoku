/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.transformToActivity
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabSudokuBinding
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

@AndroidEntryPoint
class TabSudoku : Fragment(), ViewYTranslator by AppBarAwareYTranslator() {
    private lateinit var binding: FragmentTabSudokuBinding
    private val viewModel: TabSudokuViewModel by viewModels()

    private val SeslSeekBar.sudokuSize: Int
        get() =
            when (this.progress) {
                0 -> 4
                1 -> 9
                2 -> 16
                else -> 9
            }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FragmentTabSudokuBinding.inflate(inflater, container, false).also { binding = it }.root

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.newSudokuLayout.translateYWithAppBar(requireActivity().findViewById<DrawerLayout>(R.id.drawerLayout).appBarLayout, this)
        binding.sizeSeekbar.setSeamless(true)
        binding.difficultySeekbar.setSeamless(true)
        binding.difficultySeekbar.max = Difficulty.max
        binding.newGameButton.onSingleClick {
            binding.newSudokuProgressBar.visibility = VISIBLE
            lifecycleScope.launch {
                val sudoku =
                    viewModel.createNewSudoku(binding.sizeSeekbar.sudokuSize, Difficulty.fromInt(binding.difficultySeekbar.progress))
                binding.newGameButton.transformToActivity(
                    Intent(requireActivity(), SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value),
                )
                binding.newSudokuProgressBar.visibility = INVISIBLE
            }
        }
        binding.dailyButton.onSingleClick {
            binding.dailyButton.transformToActivity(
                Intent(requireActivity(), DailySudokuActivity::class.java),
                "DailySudokuActivityTransition", // transitionNames should be unique within the view hierarchy
            )
        }
        binding.dailyAvailableButton.onSingleClick {
            binding.dailyAvailableButton.transformToActivity(
                Intent(requireActivity(), DailySudokuActivity::class.java),
                "DailySudokuActivityTransition", // transitionNames should be unique within the view hierarchy
            )
        }
        binding.levelsButton.onSingleClick {
            binding.levelsButton.transformToActivity(
                Intent(requireActivity(), SudokuLevelActivity::class.java),
                "SudokuLevelActivityTransition", // transitionNames should be unique within the view hierarchy
            )
        }
        binding.difficultySeekbar.progress = viewModel.difficultySliderValue
        binding.difficultySeekbar.setOnSeekBarChangeListener(
            object : SeslSeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeslSeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeslSeekBar?) {}

                override fun onProgressChanged(
                    seekBar: SeslSeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    viewModel.difficultySliderValue = progress
                }
            },
        )
        binding.sizeSeekbar.progress = viewModel.sizeSliderValue
        binding.sizeSeekbar.setOnSeekBarChangeListener(
            object : SeslSeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeslSeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeslSeekBar?) {}

                override fun onProgressChanged(
                    seekBar: SeslSeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    viewModel.sizeSliderValue = progress
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val sudoku = viewModel.getContinuableSudoku()
            if (sudoku != null) {
                binding.continueGameButton.isVisible = true
                binding.continueGameButton.text =
                    getString(
                        R.string.continue_game,
                        sudoku.sizeString,
                        sudoku.difficulty.getLocalString(resources),
                    )
                binding.continueGameButton.onSingleClick {
                    binding.continueGameButton.transformToActivity(
                        Intent(requireActivity(), SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value),
                    )
                }
            } else {
                binding.continueGameButton.isVisible = false
            }
            viewModel.checkDailySudokuCompleted().let {
                binding.dailyAvailableButton.isVisible = !it
                binding.dailyButton.isVisible = it
            }
        }
    }
}
