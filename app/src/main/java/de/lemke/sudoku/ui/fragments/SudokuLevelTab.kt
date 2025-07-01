package de.lemke.sudoku.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.skydoves.bundler.bundle
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.transformToActivity
import de.lemke.sudoku.databinding.FragmentTabLevelBinding
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.InitSudokuLevelUseCase
import de.lemke.sudoku.domain.ObserveSudokuLevelUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_LEVEL_ERROR_LIMIT
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.utils.SudokuListAdapter
import de.lemke.sudoku.ui.utils.SudokuListAdapter.Mode.LEVEL
import de.lemke.sudoku.ui.utils.SudokuListItem
import de.lemke.sudoku.ui.utils.SudokuListItem.SudokuItem
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule.ALL
import dev.oneuiproject.oneui.utils.ItemDecorRule.NONE
import dev.oneuiproject.oneui.utils.SemItemDecoration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SudokuLevelTab() : Fragment() {
    private lateinit var binding: FragmentTabLevelBinding
    private lateinit var sudokuListAdapter: SudokuListAdapter
    private var sudokuLevel: List<SudokuListItem> = emptyList()
    private var nextLevelSudoku: Sudoku? = null
    private val size: Int by bundle("size", 4)

    @Inject
    lateinit var initSudokuLevel: InitSudokuLevelUseCase

    @Inject
    lateinit var observeAllSudokuLevel: ObserveSudokuLevelUseCase

    @Inject
    lateinit var getMaxSudokuLevel: GetMaxSudokuLevelUseCase

    @Inject
    lateinit var generateSudokuLevel: GenerateSudokuLevelUseCase

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentTabLevelBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            initRecycler()
            initSudokuLevel(size)
            observeAllSudokuLevel(size).flowWithLifecycle(lifecycle, RESUMED).collectLatest {
                sudokuLevel = it
                if (sudokuLevel.isEmpty() || (sudokuLevel.firstOrNull() as? SudokuItem)?.sudoku?.completed == true) {
                    binding.tabLevelProgressBar.isVisible = true
                    nextLevelSudoku = generateSudokuLevel(size, level = getMaxSudokuLevel(size) + 1)
                    sudokuLevel = listOf(SudokuItem(nextLevelSudoku!!, nextLevelSudoku!!.modeLevel.toString())) + sudokuLevel
                    lifecycleScope.launch { delay(200); binding.sudokuLevelsRecycler.smoothScrollToPosition(0) }
                } else nextLevelSudoku = null
                sudokuListAdapter.submitList(sudokuLevel)
                binding.sudokuLevelsRecycler.isVisible = true
                binding.tabLevelProgressBar.isVisible = false
            }
        }
    }

    private fun initRecycler() {
        binding.sudokuLevelsRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = SudokuListAdapter(context, errorLimit = MODE_LEVEL_ERROR_LIMIT, mode = LEVEL).also {
                it.setupOnClickListeners()
                sudokuListAdapter = it
            }
            itemAnimator = null
            addItemDecoration(
                SemItemDecoration(context, dividerRule = ALL, subHeaderRule = NONE).apply { setDividerInsetStart(64.dpToPx(resources)) }
            )
            enableCoreSeslFeatures()
        }
    }

    private fun SudokuListAdapter.setupOnClickListeners() {
        onClickItem = { position, sudokuListItem, viewHolder ->
            if (sudokuListItem is SudokuItem) {
                lifecycleScope.launch {
                    if (position == 0 && nextLevelSudoku != null) {
                        binding.tabLevelProgressBar.isVisible = true
                        saveSudoku(sudokuListItem.sudoku)
                        binding.tabLevelProgressBar.isVisible = false
                    }
                    viewHolder.itemView.transformToActivity(
                        Intent(requireActivity(), SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudokuListItem.sudoku.id.value)
                    )
                }
            }
        }
    }
}

