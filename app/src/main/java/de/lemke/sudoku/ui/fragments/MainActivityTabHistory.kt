package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabHistoryBinding
import de.lemke.sudoku.domain.DeleteSudokusUseCase
import de.lemke.sudoku.domain.ObserveSudokuHistoryUseCase
import de.lemke.sudoku.domain.ObserveUserSettingsUseCase
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.utils.ItemDecoration
import de.lemke.sudoku.ui.utils.SudokuListAdapter
import de.lemke.sudoku.ui.utils.SudokuListItem
import dev.oneuiproject.oneui.delegates.AllSelectorState
import dev.oneuiproject.oneui.delegates.AppBarAwareYTranslator
import dev.oneuiproject.oneui.delegates.ViewYTranslator
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.layout.startActionMode
import dev.oneuiproject.oneui.widget.MarginsTabLayout
import kotlinx.coroutines.delay
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentTabHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity()
        drawerLayout = activity.findViewById(R.id.drawer_layout_main)
        mainTabs = activity.findViewById(R.id.main_margins_tab_layout)
        binding.historyNoEntryView.translateYWithAppBar(drawerLayout.appBarLayout, this)
        initRecycler()
        lifecycleScope.launch {
            observeSudokuHistory().flowWithLifecycle(lifecycle).collectLatest {
                sudokuHistory = it
                updateRecyclerView()
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
            addItemDecoration(ItemDecoration(context))
            enableCoreSeslFeatures()
        }

        sudokuListAdapter.configure(
            binding.sudokuHistoryList,
            SudokuListAdapter.Payload.SELECTION_MODE,
            onAllSelectorStateChanged = { allSelectorStateFlow.value = it }
        )
        updateRecyclerView()
    }

    private fun updateRecyclerView() {
        if (sudokuHistory.isEmpty()) {
            binding.sudokuHistoryList.visibility = View.GONE
            binding.historyListLottie.cancelAnimation()
            binding.historyListLottie.progress = 0f
            binding.historyNoEntryScrollView.visibility = View.VISIBLE
            binding.historyListLottie.addValueCallback(
                KeyPath("**"),
                LottieProperty.COLOR_FILTER,
                LottieValueCallback(SimpleColorFilter(requireContext().getColor(R.color.primary_color_themed)))
            )
            binding.historyListLottie.postDelayed({ binding.historyListLottie.playAnimation() }, 400)
        } else {
            binding.historyNoEntryScrollView.visibility = View.GONE
            binding.sudokuHistoryList.visibility = View.VISIBLE
            sudokuListAdapter.submitList(sudokuHistory)
        }
    }

    private fun SudokuListAdapter.setupOnClickListeners() {
        onClickItem = { position, sudokuListItem, viewHolder ->
            if (isActionMode) onToggleItem(sudokuListItem.stableId, position)
            else {
                if (sudokuListItem is SudokuListItem.SudokuItem) {
                    startActivity(Intent(context, SudokuActivity::class.java).putExtra("sudokuId", sudokuListItem.sudoku.id.value))
                    lifecycleScope.launch {
                        delay(500)
                        binding.sudokuHistoryList.smoothScrollToPosition(0)
                    }
                }
            }
        }
        onLongClickItem = {
            if (!isActionMode) launchActionMode()
            binding.sudokuHistoryList.seslStartLongPressMultiSelection()
        }
    }

    private fun launchActionMode(initialSelected: Array<Long>? = null) {
        drawerLayout.startActionMode(
            onInflateMenu = { menu ->
                mainTabs.isEnabled = false
                sudokuListAdapter.onToggleActionMode(true, initialSelected)
                requireActivity().menuInflater.inflate(R.menu.delete_menu, menu)
            },
            onEnd = {
                sudokuListAdapter.onToggleActionMode(false)
                mainTabs.isEnabled = true
            },
            onSelectMenuItem = {
                when (it.itemId) {
                    R.id.menuButtonDelete -> {
                        val dialog = ProgressDialog(context)
                        dialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
                        dialog.setCancelable(false)
                        dialog.show()
                        lifecycleScope.launch {
                            deleteSudoku(sudokuHistory.filterIsInstance<SudokuListItem.SudokuItem>()
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
            allSelectorStateFlow = allSelectorStateFlow,
        )
    }
}
