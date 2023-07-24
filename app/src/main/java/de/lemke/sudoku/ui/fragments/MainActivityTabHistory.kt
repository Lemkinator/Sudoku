package de.lemke.sudoku.ui.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.util.SeslRoundedCorner
import androidx.appcompat.util.SeslSubheaderRoundedCorner
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.FragmentTabHistoryBinding
import de.lemke.sudoku.domain.DeleteSudokusUseCase
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.GetSudokuHistoryUseCase
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.ui.SudokuActivity
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.utils.internal.ReflectUtils
import dev.oneuiproject.oneui.widget.MarginsTabLayout
import dev.oneuiproject.oneui.widget.Separator
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class MainActivityTabHistory : Fragment() {
    private lateinit var binding: FragmentTabHistoryBinding
    private lateinit var sudokuList: List<Sudoku>
    private lateinit var sudokuHistory: List<Pair<Sudoku?, LocalDateTime>>
    private lateinit var sudokuListAdapter: SudokuListAdapter
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var mainTabs: MarginsTabLayout
    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private lateinit var onBackInvokedCallback: OnBackInvokedCallback
    private var selected = HashMap<Int, Boolean>()
    private var selecting = false
    private var checkAllListening = true
    private var savedPosition: Int? = null
    private var onOffsetChangedListener: AppBarLayout.OnOffsetChangedListener? = null

    @Inject
    lateinit var getAllSudokus: GetAllSudokusUseCase

    @Inject
    lateinit var getSudokuHistory: GetSudokuHistoryUseCase

    @Inject
    lateinit var deleteSudoku: DeleteSudokusUseCase

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

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
        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                setSelecting(false)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedCallback = OnBackInvokedCallback {
                setSelecting(false)
            }
        }
        onOffsetChangedListener = AppBarLayout.OnOffsetChangedListener { layout: AppBarLayout, verticalOffset: Int ->
            val totalScrollRange = layout.totalScrollRange
            val inputMethodWindowVisibleHeight = ReflectUtils.genericInvokeMethod(
                InputMethodManager::class.java,
                activity.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE),
                "getInputMethodWindowVisibleHeight"
            ) as Int
            if (totalScrollRange != 0) binding.historyNoEntryView.translationY = (abs(verticalOffset) - totalScrollRange).toFloat() / 2.0f
            else binding.historyNoEntryView.translationY = (abs(verticalOffset) - inputMethodWindowVisibleHeight).toFloat() / 2.0f
        }
        drawerLayout.appBarLayout.addOnOffsetChangedListener(onOffsetChangedListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        drawerLayout.appBarLayout.removeOnOffsetChangedListener(onOffsetChangedListener)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            initList()
            savedPosition?.let { (binding.sudokuHistoryList.layoutManager as? LinearLayoutManager)?.scrollToPosition(it) }
            savedPosition = null
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onPause() {
        super.onPause()
        savedPosition = (binding.sudokuHistoryList.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()
    }

    private suspend fun initList() {
        sudokuList = getAllSudokus(GetAllSudokusUseCase.TYPE_NORMAL or GetAllSudokusUseCase.SIZE_ALL or GetAllSudokusUseCase.DIFFICULTY_ALL)
        sudokuHistory = getSudokuHistory(sudokuList)
        if (sudokuHistory.isEmpty()) {
            binding.sudokuHistoryList.visibility = View.GONE
            binding.historyNoEntryScrollView.visibility = View.VISIBLE
            binding.historyListLottie.cancelAnimation()
            binding.historyListLottie.progress = 0f
            binding.historyListLottie.addValueCallback(
                KeyPath("**"),
                LottieProperty.COLOR_FILTER,
                LottieValueCallback(SimpleColorFilter(requireContext().getColor(R.color.primary_color_themed)))
            )
            binding.historyListLottie.postDelayed({ binding.historyListLottie.playAnimation() }, 400)
            return
        } else {
            binding.historyNoEntryScrollView.visibility = View.GONE
            binding.sudokuHistoryList.visibility = View.VISIBLE
        }
        selected = HashMap()
        sudokuHistory.indices.forEach { i -> selected[i] = false }
        binding.sudokuHistoryList.layoutManager = LinearLayoutManager(context)
        sudokuListAdapter = SudokuListAdapter()
        binding.sudokuHistoryList.adapter = sudokuListAdapter
        binding.sudokuHistoryList.itemAnimator = null
        binding.sudokuHistoryList.addItemDecoration(ItemDecoration(requireContext()))
        binding.sudokuHistoryList.seslSetFastScrollerEnabled(true)
        binding.sudokuHistoryList.seslSetIndexTipEnabled(true)
        binding.sudokuHistoryList.seslSetFillBottomEnabled(true)
        binding.sudokuHistoryList.seslSetGoToTopEnabled(true)
        binding.sudokuHistoryList.seslSetLastRoundedCorner(true)
        binding.sudokuHistoryList.seslSetSmoothScrollEnabled(true)
        binding.sudokuHistoryList.seslSetLongPressMultiSelectionListener(object : RecyclerView.SeslLongPressMultiSelectionListener {
            override fun onItemSelected(view: RecyclerView, child: View, position: Int, id: Long) {
                if (sudokuListAdapter.getItemViewType(position) == 0) toggleItemSelected(position)
            }

            override fun onLongPressMultiSelectionStarted(x: Int, y: Int) {}
            override fun onLongPressMultiSelectionEnded(x: Int, y: Int) {}
        })
    }

    fun setSelecting(enabled: Boolean) {
        if (enabled) {
            selecting = true
            sudokuListAdapter.notifyItemRangeChanged(0, sudokuListAdapter.itemCount)
            drawerLayout.actionModeBottomMenu.clear()
            drawerLayout.setActionModeMenu(R.menu.remove_menu)
            drawerLayout.setActionModeMenuListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.menuButtonRemove -> {
                        val dialog = ProgressDialog(context)
                        dialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
                        dialog.setCancelable(false)
                        dialog.show()
                        lifecycleScope.launch {
                            deleteSudoku(sudokuHistory.filterIndexed { index, _ -> selected[index] ?: false }.mapNotNull { it.first })
                            initList()
                            dialog.dismiss()
                        }
                    }
                    else -> {
                        Toast.makeText(context, item.title, Toast.LENGTH_SHORT).show()
                    }
                }
                setSelecting(false)
                true
            }
            drawerLayout.showActionMode()
            drawerLayout.setActionModeCheckboxListener { _, isChecked ->
                if (checkAllListening) {
                    selected.replaceAll { _, _ -> isChecked }
                    sudokuHistory.forEachIndexed { index, pair -> if (pair.first == null) selected[index] = false }
                    selected.forEach { (index, _) -> sudokuListAdapter.notifyItemChanged(index) }
                }
                val count = selected.values.count { it }
                drawerLayout.setActionModeAllSelector(count, true, count == sudokuList.size)
            }
            mainTabs.isEnabled = false
            onBackPressedCallback.isEnabled = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireActivity().onBackInvokedDispatcher.registerOnBackInvokedCallback(PRIORITY_DEFAULT, onBackInvokedCallback)
            }
        } else {
            selecting = false
            for (i in 0 until sudokuListAdapter.itemCount) selected[i] = false
            sudokuListAdapter.notifyItemRangeChanged(0, sudokuListAdapter.itemCount)
            drawerLayout.setActionModeAllSelector(0, true, false)
            drawerLayout.dismissActionMode()
            mainTabs.isEnabled = true
            onBackPressedCallback.isEnabled = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireActivity().onBackInvokedDispatcher.unregisterOnBackInvokedCallback(onBackInvokedCallback)
            }
        }
    }

    fun toggleItemSelected(position: Int) {
        selected[position] = !selected[position]!!
        sudokuListAdapter.notifyItemChanged(position)
        checkAllListening = false
        val count = selected.values.count { it }
        drawerLayout.setActionModeAllSelector(count, true, count == sudokuList.size)
        checkAllListening = true
    }

    inner class SudokuListAdapter : RecyclerView.Adapter<SudokuListAdapter.ViewHolder>(), SectionIndexer {
        private var sections: MutableList<String> = mutableListOf()
        private var positionForSection: MutableList<Int> = mutableListOf()
        private var sectionForPosition: MutableList<Int> = mutableListOf()
        override fun getSections(): Array<Any> = sections.toTypedArray()
        override fun getPositionForSection(i: Int): Int = positionForSection.getOrElse(i) { 0 }
        override fun getSectionForPosition(i: Int): Int = sectionForPosition.getOrElse(i) { 0 }
        override fun getItemCount(): Int = sudokuHistory.size
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getItemViewType(position: Int): Int = if (sudokuHistory[position].first == null) 1 else 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
            0 -> ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.sudoku_list_item, parent, false), viewType)
            else -> ViewHolder(Separator(requireContext()), viewType)
        }


        @SuppressLint("SetTextI18n", "StringFormatInvalid")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sudoku = sudokuHistory[position].first
            if (holder.isItem && sudoku != null) {
                holder.checkBox.visibility = if (selecting) View.VISIBLE else View.GONE
                holder.checkBox.isChecked = selected[position]!!
                holder.textView.text = sudoku.sizeString + " | " + sudoku.difficulty.getLocalString(resources)
                holder.imageView.setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        if (sudoku.completed) dev.oneuiproject.oneui.R.drawable.ic_oui_crown_outline
                        else dev.oneuiproject.oneui.R.drawable.ic_oui_time_outline
                    )
                )
                lifecycleScope.launch {
                    val errorLimit = getUserSettings().errorLimit
                    if (sudoku.errorLimitReached(errorLimit)) holder.imageView.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            dev.oneuiproject.oneui.R.drawable.ic_oui_error
                        )
                    )
                    holder.textViewSmall.text = getString(R.string.current_time, sudoku.timeString) + " | " +
                            if (!sudoku.completed) {
                                getString(R.string.current_progress, sudoku.progress) + " | "
                            } else {
                                ""
                            } +
                            if (errorLimit == 0) {
                                getString(
                                    R.string.current_errors,
                                    sudoku.errorsMade
                                )
                            } else {
                                getString(R.string.current_errors_with_limit, sudoku.errorsMade, errorLimit)
                            } + " | " +
                            getString(R.string.current_hints, sudoku.hintsUsed)

                }
                holder.parentView.setOnClickListener {
                    if (selecting) toggleItemSelected(position)
                    else {
                        startActivity(Intent(context, SudokuActivity::class.java).putExtra("sudokuId", sudoku.id.value))
                    }
                }
                holder.parentView.setOnLongClickListener {
                    if (!selecting) setSelecting(true)
                    toggleItemSelected(position)
                    binding.sudokuHistoryList.seslStartLongPressMultiSelection()
                    true
                }
            }
            if (holder.isSeparator) {
                holder.textView.layoutParams =
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                holder.textView.text = sudokuHistory[position].second.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
            }
        }

        init {
            if (sudokuHistory.size > 1) {
                sudokuHistory.forEachIndexed { index, pair ->
                    val date: String = pair.second.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
                    if (getItemViewType(index) == 1) {
                        sections.add(date)
                        positionForSection.add(index)
                    }
                    sectionForPosition.add(sections.size - 1)
                }
            }
        }

        inner class ViewHolder internal constructor(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
            var isItem: Boolean = viewType == 0
            var isSeparator: Boolean = viewType == 1
            lateinit var parentView: LinearLayout
            lateinit var textView: TextView
            lateinit var textViewSmall: TextView
            lateinit var checkBox: CheckBox
            lateinit var imageView: ImageView

            init {
                when {
                    isItem -> {
                        parentView = itemView as LinearLayout
                        textView = parentView.findViewById(R.id.item_text)
                        textViewSmall = parentView.findViewById(R.id.item_text_small)
                        checkBox = parentView.findViewById(R.id.checkbox)
                        imageView = parentView.findViewById(R.id.item_icon)
                    }
                    isSeparator -> textView = itemView as TextView
                }
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
                    binding.sudokuHistoryList.getChildViewHolder(child) as SudokuListAdapter.ViewHolder
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
                    binding.sudokuHistoryList.getChildViewHolder(child) as SudokuListAdapter.ViewHolder
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
