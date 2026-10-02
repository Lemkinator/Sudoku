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
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.collectEvents
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.toast
import de.lemke.commonutils.ui.utils.transformToActivity
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabLevelBinding
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_LEVEL_ERROR_LIMIT
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.utils.SudokuListAdapter
import de.lemke.sudoku.ui.utils.SudokuListAdapter.Mode.LEVEL
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.recyclerview.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule.ALL
import dev.oneuiproject.oneui.utils.ItemDecorRule.NONE
import dev.oneuiproject.oneui.utils.SemItemDecoration
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SudokuLevelTab : Fragment() {
    internal lateinit var binding: FragmentTabLevelBinding
    internal val viewModel: SudokuLevelTabViewModel by viewModels()
    internal val sudokuListAdapter: SudokuListAdapter by lazy { SudokuListAdapter(requireContext(), MODE_LEVEL_ERROR_LIMIT, LEVEL) }
    private var pendingReveal: SudokuId? = null

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
        collectState(viewModel.state) { state ->
            sudokuListAdapter.submitList(state.sudokuLevel) { revealPending() }
            binding.sudokuLevelsRecycler.isVisible = !state.isLoading
            binding.tabLevelProgressBar.isVisible = state.isLoading || state.isGeneratingNextLevel
        }
        collectEvents(viewModel.events, minActiveState = RESUMED) { event ->
            when (event) {
                is SudokuLevelTabEvent.RevealSudoku -> {
                    pendingReveal = event.sudokuId
                    revealPending()
                }

                SudokuLevelTabEvent.ShowLoadError -> {
                    toast(R.string.error_loading_sudoku_level_failed)
                }
            }
        }
    }

    private fun revealPending() {
        val sudokuId = pendingReveal ?: return
        if (sudokuListAdapter.currentList != viewModel.state.value.sudokuLevel) return
        pendingReveal = null
        val position = sudokuListAdapter.revealPositionOf(sudokuId)
        if (position != NO_POSITION) binding.sudokuLevelsRecycler.smoothScrollToPosition(position)
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
                        viewModel.onNextLevelSudokuConfirmed(sudokuListItem.sudoku)
                    }
                    viewHolder.itemView.transformToActivity(
                        Intent(requireActivity(), SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuListItem.sudoku.id.value),
                    )
                }
            }
        }
    }

    companion object {
        const val KEY_SIZE = "size"

        fun newInstance(size: SudokuSize): SudokuLevelTab = SudokuLevelTab().apply { arguments = bundleOf(KEY_SIZE to size.value) }
    }
}
