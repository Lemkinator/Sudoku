package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.ColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieProperty.COLOR_FILTER
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.transformToActivity
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabHistoryBinding
import de.lemke.sudoku.domain.DeleteSudokusUseCase
import de.lemke.sudoku.domain.ObserveSudokuHistoryUseCase
import de.lemke.sudoku.domain.ObserveUserSettingsUseCase
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.utils.SudokuListAdapter
import de.lemke.sudoku.ui.utils.SudokuListAdapter.Payload.SELECTION_MODE
import de.lemke.sudoku.ui.utils.SudokuListItem
import de.lemke.sudoku.ui.utils.SudokuListItem.SeparatorItem
import de.lemke.sudoku.ui.utils.SudokuListItem.SudokuItem
import dev.oneuiproject.oneui.delegates.AllSelectorState
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.dialog.ProgressDialog.Companion.STYLE_CIRCLE
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.ktx.setTabsEnabled
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.layout.startActionMode
import dev.oneuiproject.oneui.utils.ItemDecorRule.SELECTED
import dev.oneuiproject.oneui.utils.SemItemDecoration
import dev.oneuiproject.oneui.widget.MarginsTabLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivityTabHistory : Fragment(), ViewYTranslator by AppBarAwareYTranslator() {
    private lateinit var binding: FragmentTabHistoryBinding
    private lateinit var sudokuListAdapter: SudokuListAdapter
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var mainTabs: MarginsTabLayout
    private var sudokuHistory: List<SudokuListItem> = emptyList()
    private val allSelectorStateFlow: MutableStateFlow<AllSelectorState> = MutableStateFlow(AllSelectorState())

    @Inject
    lateinit var observeSudokuHistory: ObserveSudokuHistoryUseCase

    @Inject
    lateinit var deleteSudoku: DeleteSudokusUseCase

    @Inject
    lateinit var observeUserSettings: ObserveUserSettingsUseCase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentTabHistoryBinding.inflate(inflater, container, false).also { binding = it }.root

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity()
        drawerLayout = activity.findViewById(R.id.drawerLayout)
        mainTabs = activity.findViewById(R.id.mainTabLayout)
        binding.noEntryView.translateYWithAppBar(drawerLayout.appBarLayout, this)
        initRecycler()
        lifecycleScope.launch {
            observeSudokuHistory().flowWithLifecycle(lifecycle).collectLatest {
                val previousSize = sudokuHistory.size
                sudokuHistory = it
                updateRecyclerView()
                if (it.size > previousSize) binding.sudokuHistoryList.scrollToPosition(0)
            }
        }
        lifecycleScope.launch {
            observeUserSettings().flowWithLifecycle(lifecycle).collectLatest {
                if (it.errorLimit != sudokuListAdapter.errorLimit) {
                    sudokuListAdapter.errorLimit = it.errorLimit
                    sudokuListAdapter.notifyItemRangeChanged(0, sudokuHistory.size)
                }
            }
        }
    }

    private fun initRecycler() {
        binding.sudokuHistoryList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = SudokuListAdapter(context).also {
                it.setupOnClickListeners()
                sudokuListAdapter = it
            }
            itemAnimator = null
            addItemDecoration(
                SemItemDecoration(
                    context,
                    dividerRule = SELECTED { it.itemViewType == SudokuItem.VIEW_TYPE },
                    subHeaderRule = SELECTED { it.itemViewType == SeparatorItem.VIEW_TYPE }
                ).apply { setDividerInsetStart(64.dpToPx(resources)) })
            enableCoreSeslFeatures()
        }

        sudokuListAdapter.configure(
            binding.sudokuHistoryList,
            SELECTION_MODE,
            onAllSelectorStateChanged = { allSelectorStateFlow.value = it }
        )
        updateRecyclerView()
    }

    private fun updateRecyclerView() {
        if (sudokuHistory.isEmpty()) {
            binding.sudokuHistoryList.isVisible = false
            binding.noEntryLottie.cancelAnimation()
            binding.noEntryLottie.progress = 0f
            binding.noEntryScrollView.isVisible = true
            val callback = LottieValueCallback<ColorFilter>(SimpleColorFilter(requireContext().getColor(R.color.primary_color_themed)))
            binding.noEntryLottie.addValueCallback(KeyPath("**"), COLOR_FILTER, callback)
            binding.noEntryLottie.postDelayed({ binding.noEntryLottie.playAnimation() }, 400)
        } else {
            binding.noEntryScrollView.isVisible = false
            binding.sudokuHistoryList.isVisible = true
            sudokuListAdapter.submitList(sudokuHistory)
        }
    }

    private fun SudokuListAdapter.setupOnClickListeners() {
        onClickItem = { position, sudokuListItem, viewHolder ->
            if (isActionMode) onToggleItem(sudokuListItem.stableId, position)
            else {
                if (sudokuListItem is SudokuItem) {
                    viewHolder.itemView.transformToActivity(
                        Intent(requireActivity(), SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuListItem.sudoku.id.value)
                    )
                }
            }
        }
        onLongClickItem = {
            if (!isActionMode) launchActionMode()
            binding.sudokuHistoryList.seslStartLongPressMultiSelection()
        }
    }

    private fun launchActionMode(initialSelected: Array<Long>? = null) {
        mainTabs.setTabsEnabled(false)
        sudokuListAdapter.onToggleActionMode(true, initialSelected)
        drawerLayout.startActionMode(
            onInflateMenu = { menu, menuInflater -> menuInflater.inflate(R.menu.delete_menu, menu) },
            onEnd = {
                sudokuListAdapter.onToggleActionMode(false)
                mainTabs.setTabsEnabled(true)
            },
            onSelectMenuItem = {
                when (it.itemId) {
                    R.id.menuButtonDelete -> {
                        val dialog = ProgressDialog(requireContext())
                        dialog.setProgressStyle(STYLE_CIRCLE)
                        dialog.setCancelable(false)
                        dialog.show()
                        lifecycleScope.launch {
                            deleteSudoku(
                                sudokuHistory.filterIsInstance<SudokuItem>()
                                    .filter { it.stableId in sudokuListAdapter.getSelectedIds() }.map { it.sudoku })
                            drawerLayout.endActionMode()
                            dialog.dismiss()
                        }
                        true
                    }

                    else -> false
                }
            },
            onSelectAll = { isChecked: Boolean -> sudokuListAdapter.onToggleSelectAll(isChecked) },
            allSelectorStateFlow = allSelectorStateFlow
        )
    }
}
