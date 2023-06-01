package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SectionIndexer
import android.widget.TextView
import androidx.appcompat.util.SeslRoundedCorner
import androidx.appcompat.util.SeslSubheaderRoundedCorner
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabLevelBinding
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.GetSudokuLevelUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.ui.OnDataChangedListener
import de.lemke.sudoku.ui.SudokuActivity
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.widget.Separator
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SudokuLevelTab(private val size: Int) : Fragment(), OnDataChangedListener {
    private lateinit var binding: FragmentTabLevelBinding
    private lateinit var sudokuLevel: List<Sudoku>
    private var nextLevelSudoku: Sudoku? = null
    private lateinit var sudokuListAdapter: SudokuListAdapter
    private lateinit var progressDialog: ProgressDialog
    private var savedPosition: Int? = null

    @Inject
    lateinit var getAllSudokuLevel: GetSudokuLevelUseCase

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
        onDataChanged()
    }

    override fun onDataChanged() {
        lifecycleScope.launch { initList() }
    }

    override fun onPause() {
        super.onPause()
        savedPosition = (binding.sudokuLevelsRecycler.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()
    }

    private suspend fun initList() {
        progressDialog.show()
        sudokuLevel = getAllSudokuLevel(size)
        if (sudokuLevel.isEmpty() || sudokuLevel.firstOrNull()?.completed == true) {
            nextLevelSudoku = generateSudokuLevel(size, level = getMaxSudokuLevel(size) + 1)
            sudokuLevel = (listOf(nextLevelSudoku!!) + sudokuLevel)
        } else nextLevelSudoku = null

        binding.sudokuLevelsRecycler.layoutManager = LinearLayoutManager(requireContext())
        sudokuListAdapter = SudokuListAdapter()
        binding.sudokuLevelsRecycler.adapter = sudokuListAdapter
        binding.sudokuLevelsRecycler.itemAnimator = null
        binding.sudokuLevelsRecycler.addItemDecoration(ItemDecoration(requireContext()))
        binding.sudokuLevelsRecycler.seslSetFastScrollerEnabled(true)
        binding.sudokuLevelsRecycler.seslSetIndexTipEnabled(true)
        binding.sudokuLevelsRecycler.seslSetFillBottomEnabled(true)
        binding.sudokuLevelsRecycler.seslSetGoToTopEnabled(true)
        binding.sudokuLevelsRecycler.seslSetLastRoundedCorner(true)
        binding.sudokuLevelsRecycler.seslSetSmoothScrollEnabled(true)
        savedPosition?.let { (binding.sudokuLevelsRecycler.layoutManager as LinearLayoutManager).scrollToPosition(it) }
        savedPosition = null
        progressDialog.dismiss()
    }

    //Adapter for the Icon RecyclerView
    inner class SudokuListAdapter : RecyclerView.Adapter<SudokuListAdapter.ViewHolder>(), SectionIndexer {
        private var sections: MutableList<String> = mutableListOf()
        private var positionForSection: MutableList<Int> = mutableListOf()
        private var sectionForPosition: MutableList<Int> = mutableListOf()
        override fun getSections(): Array<Any> = sections.toTypedArray()
        override fun getPositionForSection(i: Int): Int = positionForSection.getOrElse(i) { 0 }
        override fun getSectionForPosition(i: Int): Int = sectionForPosition.getOrElse(i) { 0 }
        override fun getItemCount(): Int = sudokuLevel.size
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getItemViewType(position: Int): Int = 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
            0 -> ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.sudoku_list_item, parent, false), viewType)
            else -> ViewHolder(Separator(requireContext()), viewType)
        }


        @SuppressLint("SetTextI18n", "StringFormatInvalid")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sudoku = sudokuLevel[position]
            if (holder.isItem) {
                holder.textView.text = "Level ${sudoku.modeLevel}"
                holder.imageView.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        if (sudoku.completed) dev.oneuiproject.oneui.R.drawable.ic_oui_crown_outline
                        else dev.oneuiproject.oneui.R.drawable.ic_oui_time_outline
                    )
                )
                lifecycleScope.launch {
                    holder.textViewSmall.text = getString(R.string.current_time, sudoku.timeString) + " | " +
                            if (!sudoku.completed) {
                                getString(R.string.current_progress, sudoku.progress) + " | "
                            } else {
                                ""
                            } +
                            getString(R.string.current_errors_with_limit, sudoku.errorsMade, Sudoku.MODE_LEVEL_ERROR_LIMIT)
                }
                holder.parentView.setOnClickListener {
                    progressDialog.show()
                    lifecycleScope.launch {
                        if (position == 0 && nextLevelSudoku != null) saveSudoku(sudoku)
                        startActivity(Intent(requireContext(), SudokuActivity::class.java).putExtra("sudokuId", sudoku.id.value))
                    }
                    progressDialog.dismiss()
                }
            }
        }

        init {
            sudokuLevel.forEachIndexed { index, _ ->
                sections.add((sudokuLevel.size - index).toString())
                positionForSection.add(index)
                sectionForPosition.add(sections.size)
            }
        }

        inner class ViewHolder internal constructor(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
            var isItem: Boolean = viewType == 0
            var parentView: LinearLayout
            var textView: TextView
            var textViewSmall: TextView
            var imageView: ImageView

            init {
                parentView = itemView as LinearLayout
                textView = parentView.findViewById(R.id.item_text)
                textViewSmall = parentView.findViewById(R.id.item_text_small)
                imageView = parentView.findViewById(R.id.item_icon)
            }
        }
    }

    inner class ItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
        private val divider: Drawable?
        private val roundedCorner: SeslSubheaderRoundedCorner
        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            super.onDraw(c, parent, state)
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val holder: SudokuListAdapter.ViewHolder =
                    binding.sudokuLevelsRecycler.getChildViewHolder(child) as SudokuListAdapter.ViewHolder
                if (holder.isItem) {
                    val top = (child.bottom + (child.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
                    val bottom = divider!!.intrinsicHeight + top
                    divider.setBounds(parent.left, top, parent.right, bottom)
                    divider.draw(c)
                }
            }
        }

        override fun seslOnDispatchDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val holder: SudokuListAdapter.ViewHolder =
                    binding.sudokuLevelsRecycler.getChildViewHolder(child) as SudokuListAdapter.ViewHolder
                if (!holder.isItem) roundedCorner.drawRoundedCorner(child, c)
            }
        }

        init {
            val outValue = TypedValue()
            context.theme.resolveAttribute(androidx.appcompat.R.attr.isLightTheme, outValue, true)
            divider = context.getDrawable(
                if (outValue.data == 0) androidx.appcompat.R.drawable.sesl_list_divider_dark
                else androidx.appcompat.R.drawable.sesl_list_divider_light
            )!!
            roundedCorner = SeslSubheaderRoundedCorner(context)
            roundedCorner.roundedCorners = SeslRoundedCorner.ROUNDED_CORNER_ALL
        }
    }
}
