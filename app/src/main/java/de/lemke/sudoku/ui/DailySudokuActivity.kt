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

package de.lemke.sudoku.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity.START
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.collectEvents
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.prepareActivityTransformationBetween
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.toast
import de.lemke.commonutils.ui.utils.transformToActivity
import de.lemke.commonutils.ui.widget.InfoBottomSheet.Companion.showInfoBottomSheet
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityDailySudokuBinding
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY_ERROR_LIMIT
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.utils.SudokuListAdapter
import de.lemke.sudoku.ui.utils.SudokuListAdapter.Mode.DAILY
import de.lemke.sudoku.ui.utils.SudokuListItem.SeparatorItem
import de.lemke.sudoku.ui.utils.SudokuListItem.SudokuItem
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.recyclerview.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule.SELECTED
import dev.oneuiproject.oneui.utils.SemItemDecoration

@AndroidEntryPoint
class DailySudokuActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDailySudokuBinding
    private val sudokuListAdapter: SudokuListAdapter by lazy { SudokuListAdapter(this, MODE_DAILY_ERROR_LIMIT, DAILY) }
    private val viewModel: DailySudokuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationBetween()
        super.onCreate(savedInstanceState)
        binding = ActivityDailySudokuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        initRecycler()
        collectState(viewModel.state, minActiveState = RESUMED) { state ->
            sudokuListAdapter.submitList(state.sudokus)
            binding.dailySudokuRecycler.isVisible = !state.isLoading
            binding.dailyProgressBar.isVisible = state.isLoading
        }
        collectEvents(viewModel.events) { event ->
            when (event) {
                DailySudokuEvent.ShowLoadError -> toast(R.string.error_loading_daily_sudokus_failed)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.daily_sudoku_menu, menu)
        MenuCompat.setGroupDividerEnabled(menu, true)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        viewModel.dailyShowUncompleted.let {
            menu?.findItem(R.id.menuitem_show_all_sudokus)?.isVisible = !it
            menu?.findItem(R.id.menuitem_show_only_completed_sudokus)?.isVisible = it
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.menuitem_daily_sudoku_info -> {
                showInfoBottomSheet(
                    titleResId = R.string.daily_sudoku,
                    messageResId = R.string.daily_sudoku_info_message,
                    textGravity = START,
                ).let { true }
            }

            R.id.menuitem_show_all_sudokus -> {
                viewModel.dailyShowUncompleted = true
                invalidateOptionsMenu()
                true
            }

            R.id.menuitem_show_only_completed_sudokus -> {
                viewModel.dailyShowUncompleted = false
                invalidateOptionsMenu()
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }

    private fun initRecycler() {
        binding.dailySudokuRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = sudokuListAdapter.also { it.setupOnClickListeners() }
            itemAnimator = null
            addItemDecoration(
                SemItemDecoration(
                    context,
                    dividerRule = SELECTED { it.itemViewType == SudokuItem.VIEW_TYPE },
                    subHeaderRule = SELECTED { it.itemViewType == SeparatorItem.VIEW_TYPE },
                ).apply { setDividerInsetStart(64.dpToPx(resources)) },
            )
            enableCoreSeslFeatures()
        }
    }

    private fun SudokuListAdapter.setupOnClickListeners() {
        onClickItem = { _, sudokuListItem, viewHolder ->
            if (sudokuListItem is SudokuItem) {
                viewHolder.itemView.transformToActivity(
                    Intent(this@DailySudokuActivity, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuListItem.sudoku.id.value),
                )
            }
        }
    }
}
