package de.lemke.sudoku.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity.START
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.prepareActivityTransformationBetween
import de.lemke.commonutils.setCustomBackAnimation
import de.lemke.commonutils.transformToActivity
import de.lemke.commonutils.ui.widget.InfoBottomSheet.Companion.showInfoBottomSheet
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityDailySudokuBinding
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.InitDailySudokusUseCase
import de.lemke.sudoku.domain.ObserveDailySudokusUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY_ERROR_LIMIT
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.utils.SudokuListAdapter
import de.lemke.sudoku.ui.utils.SudokuListAdapter.Mode.DAILY
import de.lemke.sudoku.ui.utils.SudokuListItem
import de.lemke.sudoku.ui.utils.SudokuListItem.SeparatorItem
import de.lemke.sudoku.ui.utils.SudokuListItem.SudokuItem
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule.SELECTED
import dev.oneuiproject.oneui.utils.SemItemDecoration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class DailySudokuActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDailySudokuBinding
    private lateinit var sudokuListAdapter: SudokuListAdapter
    private var dailySudokus: List<SudokuListItem> = emptyList()

    @Inject
    lateinit var initDailySudokus: InitDailySudokusUseCase

    @Inject
    lateinit var observeDailySudokus: ObserveDailySudokusUseCase

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationBetween()
        super.onCreate(savedInstanceState)
        binding = ActivityDailySudokuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        initRecycler()
        lifecycleScope.launch {
            initDailySudokus()
            invalidateOptionsMenu()
            observeDailySudokus().flowWithLifecycle(lifecycle, RESUMED).collectLatest {
                dailySudokus = it
                sudokuListAdapter.submitList(it)
                binding.dailySudokuRecycler.isVisible = true
            }
        }
    }

    private fun initRecycler() {
        binding.dailySudokuRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = SudokuListAdapter(context, errorLimit = MODE_DAILY_ERROR_LIMIT, mode = DAILY).also {
                it.setupOnClickListeners()
                sudokuListAdapter = it
            }
            itemAnimator = null
            addItemDecoration(
                SemItemDecoration(
                    context,
                    dividerRule = SELECTED { it.itemViewType == SudokuItem.VIEW_TYPE },
                    subHeaderRule = SELECTED { it.itemViewType == SeparatorItem.VIEW_TYPE }
                ).apply { setDividerInsetStart(64.dpToPx(resources)) }
            )
            enableCoreSeslFeatures()
        }
    }

    private fun SudokuListAdapter.setupOnClickListeners() {
        onClickItem = { position, sudokuListItem, viewHolder ->
            if (sudokuListItem is SudokuItem) {
                viewHolder.itemView.transformToActivity(
                    Intent(this@DailySudokuActivity, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuListItem.sudoku.id.value)
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.daily_sudoku_menu, menu)
        MenuCompat.setGroupDividerEnabled(menu, true)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        lifecycleScope.launch {
            getUserSettings().dailyShowUncompleted.let {
                menu?.findItem(R.id.menuitem_show_all_sudokus)?.isVisible = !it
                menu?.findItem(R.id.menuitem_show_only_completed_sudokus)?.isVisible = it
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menuitem_daily_sudoku_info -> showInfoBottomSheet(
            titleResId = R.string.daily_sudoku,
            messageResId = R.string.daily_sudoku_info_message,
            textGravity = START
        ).let { true }

        R.id.menuitem_show_all_sudokus -> lifecycleScope.launch {
            updateUserSettings { it.copy(dailyShowUncompleted = true) }
            invalidateOptionsMenu()
        }.let { true }

        R.id.menuitem_show_only_completed_sudokus -> lifecycleScope.launch {
            updateUserSettings { it.copy(dailyShowUncompleted = false) }
            invalidateOptionsMenu()
        }.let { true }

        else -> super.onOptionsItemSelected(item)
    }
}
