package de.lemke.sudoku.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuCompat
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityDailySudokuBinding
import de.lemke.sudoku.domain.*
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.ui.utils.ItemDecoration
import de.lemke.sudoku.ui.utils.SudokuListAdapter
import de.lemke.sudoku.ui.utils.SudokuListItem
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.ktx.enableCoreSeslFeatures
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class DailySudokuActivity : AppCompatActivity(R.layout.activity_daily_sudoku) {
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
            observeDailySudokus().flowWithLifecycle(lifecycle).collectLatest {
                dailySudokus = it
                sudokuListAdapter.submitList(it)
                binding.dailySudokuRecycler.visibility = View.VISIBLE
                progressDialog.dismiss()
            }
        }
        binding.dailySudokuToolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        binding.dailySudokuToolbarLayout.setNavigationButtonOnClickListener { finishAfterTransition() }
    }

    private fun initRecycler() {
        binding.dailySudokuRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = SudokuListAdapter(context, errorLimit = Sudoku.MODE_DAILY_ERROR_LIMIT, isDailyList = true).also {
                it.setupOnClickListeners()
                sudokuListAdapter = it
            }
            itemAnimator = null
            addItemDecoration(ItemDecoration(context))
            enableCoreSeslFeatures()
        }
    }

    private fun SudokuListAdapter.setupOnClickListeners() {
        onClickItem = { position, sudokuListItem, viewHolder ->
            if (sudokuListItem is SudokuListItem.SudokuItem) {
                startActivity(
                    Intent(this@DailySudokuActivity, SudokuActivity::class.java).putExtra("sudokuId", sudokuListItem.sudoku.id.value)
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.daily_sudoku_menu, menu)
        MenuCompat.setGroupDividerEnabled(menu, true)
        lifecycleScope.launch {
            val showUncompleted = getUserSettings().dailyShowUncompleted
            binding.dailySudokuToolbarLayout.toolbar.menu.setGroupVisible(R.id.group_show_all_sudokus, !showUncompleted)
            binding.dailySudokuToolbarLayout.toolbar.menu.setGroupVisible(R.id.group_show_only_completed_sudokus, showUncompleted)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuitem_daily_sudoku_info -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.daily_sudoku)
                    .setMessage(R.string.daily_sudoku_info_message)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }

            R.id.menuitem_show_all_sudokus -> {
                lifecycleScope.launch {
                    updateUserSettings { it.copy(dailyShowUncompleted = true) }
                    binding.dailySudokuToolbarLayout.toolbar.menu.setGroupVisible(R.id.group_show_all_sudokus, false)
                    binding.dailySudokuToolbarLayout.toolbar.menu.setGroupVisible(R.id.group_show_only_completed_sudokus, true)
                }
            }

            R.id.menuitem_show_only_completed_sudokus -> {
                lifecycleScope.launch {
                    updateUserSettings { it.copy(dailyShowUncompleted = false) }
                    binding.dailySudokuToolbarLayout.toolbar.menu.setGroupVisible(R.id.group_show_all_sudokus, true)
                    binding.dailySudokuToolbarLayout.toolbar.menu.setGroupVisible(R.id.group_show_only_completed_sudokus, false)
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
