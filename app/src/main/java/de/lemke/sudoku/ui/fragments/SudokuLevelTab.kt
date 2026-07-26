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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.collectEvents
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.toast
import de.lemke.commonutils.ui.utils.transformToActivity
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabLevelBinding
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_LEVEL_ERROR_LIMIT
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.utils.SudokuListAdapter
import de.lemke.sudoku.ui.utils.SudokuListAdapter.Mode.LEVEL
import de.lemke.sudoku.ui.utils.SudokuListItem.SudokuItem
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.recyclerview.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule.ALL
import dev.oneuiproject.oneui.utils.ItemDecorRule.NONE
import dev.oneuiproject.oneui.utils.SemItemDecoration
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SudokuLevelTab : Fragment() {
    private lateinit var binding: FragmentTabLevelBinding
    private val viewModel: SudokuLevelTabViewModel by viewModels()
    private val sudokuListAdapter: SudokuListAdapter by lazy { SudokuListAdapter(requireContext(), MODE_LEVEL_ERROR_LIMIT, LEVEL) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FragmentTabLevelBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        initRecycler()
        collectState(viewModel.state, minActiveState = RESUMED) { state ->
            sudokuListAdapter.submitList(state.sudokuLevel)
            binding.sudokuLevelsRecycler.isVisible = !state.isLoading
            binding.tabLevelProgressBar.isVisible = state.isLoading || state.isGeneratingNextLevel
        }
        collectEvents(viewModel.events, minActiveState = RESUMED) { event ->
            when (event) {
                SudokuLevelTabEvent.ScrollToTop -> binding.sudokuLevelsRecycler.smoothScrollToPosition(0)
                SudokuLevelTabEvent.ShowLoadError -> toast(R.string.error_loading_sudoku_level_failed)
            }
        }
    }

    private fun initRecycler() {
        binding.sudokuLevelsRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = sudokuListAdapter.also { it.setupOnClickListeners() }
            itemAnimator = null
            addItemDecoration(SemItemDecoration(context, ALL, NONE).apply { setDividerInsetStart(64.dpToPx(resources)) })
            enableCoreSeslFeatures()
        }
    }

    private fun SudokuListAdapter.setupOnClickListeners() {
        onClickItem = { position, sudokuListItem, viewHolder ->
            if (sudokuListItem is SudokuItem) {
                lifecycleScope.launch {
                    if (position == 0 && viewModel.state.value.hasNextLevelToStart) {
                        binding.tabLevelProgressBar.isVisible = true
                        viewModel.onNextLevelSudokuConfirmed(sudokuListItem.sudoku)
                        binding.tabLevelProgressBar.isVisible = false
                    }
                    viewHolder.itemView.transformToActivity(
                        Intent(requireActivity(), SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuListItem.sudoku.id.value),
                    )
                }
            }
        }
    }
}
