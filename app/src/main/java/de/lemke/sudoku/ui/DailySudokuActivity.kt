package de.lemke.sudoku.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SectionIndexer
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.util.SeslRoundedCorner
import androidx.appcompat.util.SeslSubheaderRoundedCorner
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityDailySudokuBinding
import de.lemke.sudoku.domain.*
import de.lemke.sudoku.domain.model.Sudoku
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.widget.Separator
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class DailySudokuActivity : AppCompatActivity(R.layout.activity_daily_sudoku) {
    private lateinit var binding: ActivityDailySudokuBinding
    private lateinit var dailySudokus: List<Pair<Sudoku?, LocalDate>>
    private lateinit var sudokuListAdapter: SudokuListAdapter
    private lateinit var progressDialog: ProgressDialog
    private var savedPosition: Int? = null

    @Inject
    lateinit var getAllDailySudokus: GetDailySudokusUseCase

    @Inject
    lateinit var generateDailySudoku: GenerateDailySudokuUseCase

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDailySudokuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.dailySudokuToolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        binding.dailySudokuToolbarLayout.setNavigationButtonOnClickListener { finish() }
        progressDialog = ProgressDialog(this)
        progressDialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
        progressDialog.setCancelable(false)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            initList()
            savedPosition?.let { (binding.dailySudokuRecycler.layoutManager as LinearLayoutManager).scrollToPosition(it) }
            savedPosition = null
        }
    }

    override fun onPause() {
        super.onPause()
        savedPosition = (binding.dailySudokuRecycler.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
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
                    initList()
                    binding.dailySudokuToolbarLayout.toolbar.menu.setGroupVisible(R.id.group_show_all_sudokus, false)
                    binding.dailySudokuToolbarLayout.toolbar.menu.setGroupVisible(R.id.group_show_only_completed_sudokus, true)
                }
            }
            R.id.menuitem_show_only_completed_sudokus -> {
                lifecycleScope.launch {
                    updateUserSettings { it.copy(dailyShowUncompleted = false) }
                    initList()
                    binding.dailySudokuToolbarLayout.toolbar.menu.setGroupVisible(R.id.group_show_all_sudokus, true)
                    binding.dailySudokuToolbarLayout.toolbar.menu.setGroupVisible(R.id.group_show_only_completed_sudokus, false)
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initList() {
        progressDialog.show()
        lifecycleScope.launch {
            dailySudokus = getAllDailySudokus(getUserSettings().dailyShowUncompleted)
            binding.dailySudokuRecycler.layoutManager = LinearLayoutManager(this@DailySudokuActivity)
            sudokuListAdapter = SudokuListAdapter()
            binding.dailySudokuRecycler.adapter = sudokuListAdapter
            binding.dailySudokuRecycler.itemAnimator = null
            binding.dailySudokuRecycler.addItemDecoration(ItemDecoration(this@DailySudokuActivity))
            binding.dailySudokuRecycler.seslSetFastScrollerEnabled(true)
            binding.dailySudokuRecycler.seslSetIndexTipEnabled(true)
            binding.dailySudokuRecycler.seslSetFillBottomEnabled(true)
            binding.dailySudokuRecycler.seslSetGoToTopEnabled(true)
            binding.dailySudokuRecycler.seslSetLastRoundedCorner(true)
            binding.dailySudokuRecycler.seslSetSmoothScrollEnabled(true)
            progressDialog.dismiss()
        }
    }

    //Adapter for the Icon RecyclerView
    inner class SudokuListAdapter : RecyclerView.Adapter<SudokuListAdapter.ViewHolder>(), SectionIndexer {
        private var sections: MutableList<String> = mutableListOf()
        private var positionForSection: MutableList<Int> = mutableListOf()
        private var sectionForPosition: MutableList<Int> = mutableListOf()
        override fun getSections(): Array<Any> = sections.toTypedArray()
        override fun getPositionForSection(i: Int): Int = positionForSection.getOrElse(i) { 0 }
        override fun getSectionForPosition(i: Int): Int = sectionForPosition.getOrElse(i) { 0 }
        override fun getItemCount(): Int = dailySudokus.size
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getItemViewType(position: Int): Int = if (dailySudokus[position].first == null) 1 else 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
            0 -> ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.sudoku_list_item, parent, false), viewType)
            else -> ViewHolder(Separator(this@DailySudokuActivity), viewType)
        }


        @SuppressLint("SetTextI18n", "StringFormatInvalid")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sudoku = dailySudokus[position].first
            if (holder.isItem && sudoku != null) {
                holder.textView.text = dailySudokus[position].second.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
                holder.imageView.setImageDrawable(
                    ContextCompat.getDrawable(
                        this@DailySudokuActivity,
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
                            getString(R.string.current_errors_with_limit, sudoku.errorsMade, Sudoku.MODE_DAILY_ERROR_LIMIT)

                }
                holder.parentView.setOnClickListener {
                    startActivity(Intent(this@DailySudokuActivity, SudokuActivity::class.java).putExtra("sudokuId", sudoku.id.value))
                }
            }
            if (holder.isSeparator) {
                holder.textView.layoutParams =
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                holder.textView.text = dailySudokus[position].second.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " +
                        dailySudokus[position].second.year
            }
        }

        init {
            if (dailySudokus.size > 1) {
                dailySudokus.forEachIndexed { index, pair ->
                    val date: String = pair.second.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + pair.second.year
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
            lateinit var imageView: ImageView

            init {
                when {
                    isItem -> {
                        parentView = itemView as LinearLayout
                        textView = parentView.findViewById(R.id.item_text)
                        textViewSmall = parentView.findViewById(R.id.item_text_small)
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
                    binding.dailySudokuRecycler.getChildViewHolder(child) as SudokuListAdapter.ViewHolder
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
                    binding.dailySudokuRecycler.getChildViewHolder(child) as SudokuListAdapter.ViewHolder
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
