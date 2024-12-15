package de.lemke.sudoku.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.core.view.MenuCompat
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.skydoves.transformationlayout.TransformationAppCompatActivity
import com.skydoves.transformationlayout.TransformationCompat
import com.skydoves.transformationlayout.TransformationLayout
import com.skydoves.transformationlayout.onTransformationStartContainer
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.setCustomBackPressAnimation
import de.lemke.commonutils.widget.InfoBottomSheet
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityDailySudokuBinding
import de.lemke.sudoku.domain.*
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.utils.SudokuListAdapter
import de.lemke.sudoku.ui.utils.SudokuListItem
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule
import dev.oneuiproject.oneui.utils.SemItemDecoration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class DailySudokuActivity : TransformationAppCompatActivity() {
    private lateinit var binding: ActivityDailySudokuBinding
    private lateinit var sudokuListAdapter: SudokuListAdapter
    private var dailySudokus: List<SudokuListItem> = emptyList()
    private var dailyShowUncompleted = true

    @Inject
    lateinit var initDailySudokus: InitDailySudokusUseCase

    @Inject
    lateinit var observeDailySudokus: ObserveDailySudokusUseCase

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        onTransformationStartContainer()
        super.onCreate(savedInstanceState)
        binding = ActivityDailySudokuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackPressAnimation(binding.root)
        val progressDialog = ProgressDialog(this)
        progressDialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
        progressDialog.setCancelable(false)
        progressDialog.show()
        initRecycler()
        lifecycleScope.launch {
            initDailySudokus()
            dailyShowUncompleted = getUserSettings().dailyShowUncompleted
            invalidateOptionsMenu()
            observeDailySudokus().flowWithLifecycle(lifecycle).collectLatest {
                dailySudokus = it
                sudokuListAdapter.submitList(it)
                binding.dailySudokuRecycler.visibility = View.VISIBLE
                progressDialog.dismiss()
            }
        }
    }

    private fun initRecycler() {
        binding.dailySudokuRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = SudokuListAdapter(context, errorLimit = Sudoku.MODE_DAILY_ERROR_LIMIT, mode = SudokuListAdapter.Mode.DAILY).also {
                it.setupOnClickListeners()
                sudokuListAdapter = it
            }
            itemAnimator = null
            addItemDecoration(
                SemItemDecoration(
                    context,
                    dividerRule = ItemDecorRule.SELECTED {
                        it.itemViewType == SudokuListItem.SudokuItem.VIEW_TYPE
                    },
                    subHeaderRule = ItemDecorRule.SELECTED {
                        it.itemViewType == SudokuListItem.SeparatorItem.VIEW_TYPE
                    }
                ).apply { setDividerInsetStart(64.dpToPx(resources)) }
            )
            enableCoreSeslFeatures()
        }
    }

    private fun SudokuListAdapter.setupOnClickListeners() {
        onClickItem = { position, sudokuListItem, viewHolder ->
            if (sudokuListItem is SudokuListItem.SudokuItem) {
                TransformationCompat.startActivity(
                    viewHolder.itemView as TransformationLayout,
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
        menu?.setGroupVisible(R.id.group_show_all_sudokus, !dailyShowUncompleted)
        menu?.setGroupVisible(R.id.group_show_only_completed_sudokus, dailyShowUncompleted)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuitem_daily_sudoku_info -> {
                InfoBottomSheet.newInstance(
                    title = getString(R.string.daily_sudoku),
                    message = getString(R.string.daily_sudoku_info_message),
                    textGravity = Gravity.START
                ).show(supportFragmentManager, "daily_sudoku_info")
            }

            R.id.menuitem_show_all_sudokus -> {
                lifecycleScope.launch {
                    updateUserSettings { it.copy(dailyShowUncompleted = true) }
                    invalidateOptionsMenu()
                }
            }

            R.id.menuitem_show_only_completed_sudokus -> {
                lifecycleScope.launch {
                    updateUserSettings { it.copy(dailyShowUncompleted = false) }
                    invalidateOptionsMenu()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
