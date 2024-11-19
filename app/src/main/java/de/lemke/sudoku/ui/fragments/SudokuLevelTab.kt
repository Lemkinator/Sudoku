package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabLevelBinding
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.InitSudokuLevelUseCase
import de.lemke.sudoku.domain.ObserveSudokuLevelUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.ui.SudokuActivity
import de.lemke.sudoku.ui.utils.ItemDecoration
import de.lemke.sudoku.ui.utils.ItemDecorationViewHolder
import dev.oneuiproject.oneui.delegates.SectionIndexerDelegate
import dev.oneuiproject.oneui.delegates.SemSectionIndexer
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.ktx.enableCoreSeslFeatures
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SudokuLevelTab(private val size: Int) : Fragment() {
    private lateinit var binding: FragmentTabLevelBinding
    private lateinit var sudokuListAdapter: SudokuListAdapter
    private lateinit var progressDialog: ProgressDialog
    private var sudokuLevel: List<Sudoku> = emptyList()
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
                if (sudokuLevel.isEmpty() || sudokuLevel.firstOrNull()?.completed == true) {
                    progressDialog.show()
                    nextLevelSudoku = generateSudokuLevel(size, level = getMaxSudokuLevel(size) + 1)
                    sudokuLevel = (listOf(nextLevelSudoku!!) + sudokuLevel)
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
            adapter = SudokuListAdapter().also {
                sudokuListAdapter = it
            }
            itemAnimator = null
            addItemDecoration(ItemDecoration(context))
            enableCoreSeslFeatures()
        }
        sudokuListAdapter.submitList(sudokuLevel)
    }

    inner class SudokuListAdapter : RecyclerView.Adapter<SudokuListAdapter.ViewHolder>(),
        SemSectionIndexer<Sudoku> by SectionIndexerDelegate(requireContext(), labelExtractor = { it.modeLevel.toString() }) {

        init {
            setHasStableIds(true)
        }

        private val asyncListDiffer = AsyncListDiffer(this, object : DiffUtil.ItemCallback<Sudoku>() {
            override fun areItemsTheSame(oldItem: Sudoku, newItem: Sudoku): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Sudoku, newItem: Sudoku): Boolean = oldItem.contentEquals(newItem)
        })

        fun submitList(listItems: List<Sudoku>) {
            updateSections(listItems, false)
            asyncListDiffer.submitList(listItems)
        }

        private val currentList: List<Sudoku> get() = asyncListDiffer.currentList

        override fun getItemId(position: Int) = currentList[position].hashCode().toLong()

        override fun getItemCount(): Int = currentList.size

        override fun getItemViewType(position: Int): Int = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.sudoku_list_item, parent, false)).apply {
                itemView.setOnClickListener {
                    bindingAdapterPosition.let {
                        progressDialog.show()
                        lifecycleScope.launch {
                            val sudoku = currentList[it]
                            if (it == 0 && nextLevelSudoku != null) saveSudoku(sudoku)
                            startActivity(Intent(requireContext(), SudokuActivity::class.java).putExtra("sudokuId", sudoku.id.value))
                        }
                        progressDialog.dismiss()
                    }
                }
            }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindSudoku(currentList[position])
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), ItemDecorationViewHolder {
            override val isSeparator = false
            var textView: TextView = itemView.findViewById(R.id.item_text)
            private var textViewSmall: TextView = itemView.findViewById(R.id.item_text_small)
            private var imageView: ImageView = itemView.findViewById(R.id.item_icon)

            @SuppressLint("SetTextI18n", "StringFormatInvalid")
            fun bindSudoku(sudoku: Sudoku) {
                textView.text = "Level ${sudoku.modeLevel}"
                imageView.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        if (sudoku.completed) dev.oneuiproject.oneui.R.drawable.ic_oui_crown_outline
                        else dev.oneuiproject.oneui.R.drawable.ic_oui_time_outline
                    )
                )
                if (sudoku.errorLimitReached(3)) imageView.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), dev.oneuiproject.oneui.R.drawable.ic_oui_error)
                )
                textViewSmall.text = getString(R.string.current_time, sudoku.timeString) + " | " +
                        if (!sudoku.completed) {
                            getString(R.string.current_progress, sudoku.progress) + " | "
                        } else {
                            ""
                        } +
                        getString(R.string.current_errors_with_limit, sudoku.errorsMade, Sudoku.MODE_LEVEL_ERROR_LIMIT)
            }
        }
    }
}

