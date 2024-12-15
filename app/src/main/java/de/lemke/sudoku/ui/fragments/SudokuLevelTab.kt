package de.lemke.sudoku.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.skydoves.transformationlayout.TransformationCompat
import com.skydoves.transformationlayout.TransformationLayout
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.databinding.FragmentTabLevelBinding
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.InitSudokuLevelUseCase
import de.lemke.sudoku.domain.ObserveSudokuLevelUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.utils.SudokuListAdapter
import de.lemke.sudoku.ui.utils.SudokuListItem
import de.lemke.sudoku.ui.utils.SudokuListItem.SudokuItem
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule
import dev.oneuiproject.oneui.utils.SemItemDecoration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SudokuLevelTab(private val size: Int) : Fragment() {
    private lateinit var binding: FragmentTabLevelBinding
    private lateinit var sudokuListAdapter: SudokuListAdapter
    private lateinit var progressDialog: ProgressDialog
    private var sudokuLevel: List<SudokuListItem> = emptyList()
    private var nextLevelSudoku: Sudoku? = null

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentTabLevelBinding.inflate(inflater, container, false)
        progressDialog = ProgressDialog(requireContext())
        progressDialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
        progressDialog.setCancelable(false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            initRecycler()
            initSudokuLevel(size)
            observeAllSudokuLevel(size).flowWithLifecycle(lifecycle).collectLatest {
                sudokuLevel = it
                if (sudokuLevel.isEmpty() || (sudokuLevel.firstOrNull() as? SudokuItem)?.sudoku?.completed == true) {
                    progressDialog.show()
                    nextLevelSudoku = generateSudokuLevel(size, level = getMaxSudokuLevel(size) + 1)
                    sudokuLevel =
                        (listOf(SudokuItem(nextLevelSudoku!!, nextLevelSudoku!!.modeLevel.toString())) + sudokuLevel)
                    binding.sudokuLevelsRecycler.smoothScrollToPosition(0)
                    progressDialog.dismiss()
                } else nextLevelSudoku = null
                sudokuListAdapter.submitList(sudokuLevel)
            }
        }
    }

    private fun initRecycler() {
        binding.sudokuLevelsRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = SudokuListAdapter(
                context,
                errorLimit = Sudoku.MODE_LEVEL_ERROR_LIMIT,
                mode = SudokuListAdapter.Mode.LEVEL
            ).also {
                it.setupOnClickListeners()
                sudokuListAdapter = it
            }
            itemAnimator = null
            addItemDecoration(
                SemItemDecoration(context, dividerRule = ItemDecorRule.ALL, subHeaderRule = ItemDecorRule.NONE)
                    .apply { setDividerInsetStart(64.dpToPx(resources)) }
            )
            enableCoreSeslFeatures()
        }
    }

    private fun SudokuListAdapter.setupOnClickListeners() {
        onClickItem = { position, sudokuListItem, viewHolder ->
            if (sudokuListItem is SudokuItem) {
                progressDialog.show()
                lifecycleScope.launch {
                    if (position == 0 && nextLevelSudoku != null) saveSudoku(sudokuListItem.sudoku)
                    TransformationCompat.startActivity(
                        viewHolder.itemView as TransformationLayout,
                        Intent(requireContext(), SudokuActivity::class.java).putExtra(
                            KEY_SUDOKU_ID,
                            sudokuListItem.sudoku.id.value
                        )
                    )
                    progressDialog.dismiss()
                }
            }
        }
    }
}

