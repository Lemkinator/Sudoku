package de.lemke.sudoku.ui.utils

import android.annotation.SuppressLint
import android.app.ActionBar.LayoutParams
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.MarginLayoutParams
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.ui.utils.SudokuListItem.SeparatorItem
import de.lemke.sudoku.ui.utils.SudokuListItem.SudokuItem
import dev.oneuiproject.oneui.delegates.MultiSelector
import dev.oneuiproject.oneui.delegates.MultiSelectorDelegate
import dev.oneuiproject.oneui.delegates.SectionIndexerDelegate
import dev.oneuiproject.oneui.delegates.SemSectionIndexer
import dev.oneuiproject.oneui.widget.Separator
import kotlin.apply
import kotlin.collections.map
import kotlin.collections.toSet
import kotlin.let

class SudokuListAdapter(
    private val context: Context,
    var errorLimit: Int = 0
) : RecyclerView.Adapter<SudokuListAdapter.ViewHolder>(),
    MultiSelector<Long> by MultiSelectorDelegate(isSelectable = { it != SeparatorItem.VIEW_TYPE }),
    SemSectionIndexer<SudokuListItem> by SectionIndexerDelegate(context, labelExtractor = { it.label }) {

    init {
        setHasStableIds(true)
    }

    private val asyncListDiffer = AsyncListDiffer(this, object : DiffUtil.ItemCallback<SudokuListItem>() {
        override fun areItemsTheSame(oldItem: SudokuListItem, newItem: SudokuListItem): Boolean = when {
            oldItem is SudokuItem && newItem is SudokuItem -> {
                oldItem.sudoku == newItem.sudoku
            }

            oldItem is SeparatorItem && newItem is SeparatorItem -> {
                oldItem.indexText == newItem.indexText
            }

            else -> false
        }

        override fun areContentsTheSame(oldItem: SudokuListItem, newItem: SudokuListItem): Boolean = when {
            oldItem is SudokuItem && newItem is SudokuItem -> {
                oldItem.sudoku.contentEquals(newItem.sudoku)
            }

            oldItem is SeparatorItem && newItem is SeparatorItem -> {
                oldItem.indexText == newItem.indexText
            }

            else -> false
        }
    })

    var onClickItem: ((Int, SudokuListItem, ViewHolder) -> Unit)? = null

    var onLongClickItem: (() -> Unit)? = null

    fun submitList(listItems: List<SudokuListItem>) {
        updateSections(listItems, false)
        asyncListDiffer.submitList(listItems)
        updateSelectableIds(listItems.filter { it !is SeparatorItem }.map { it.stableId })
    }

    private val currentList: List<SudokuListItem> get() = asyncListDiffer.currentList

    override fun getItemId(position: Int) = currentList[position].stableId

    override fun getItemCount(): Int = currentList.size

    override fun getItemViewType(position: Int): Int = when (currentList[position]) {
        is SudokuItem -> SudokuItem.VIEW_TYPE
        is SeparatorItem -> SeparatorItem.VIEW_TYPE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
        SudokuItem.VIEW_TYPE -> {
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.sudoku_list_item, parent, false), false).apply {
                itemView.setOnClickListener {
                    bindingAdapterPosition.let {
                        onClickItem?.invoke(it, currentList[it], this@apply)
                    }
                }

                itemView.setOnLongClickListener {
                    onLongClickItem?.invoke()
                    true
                }
            }
        }


        SeparatorItem.VIEW_TYPE -> {
            ViewHolder(Separator(parent.context), true).apply {
                itemView.layoutParams = MarginLayoutParams(MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }
        }

        else -> throw IllegalArgumentException("Invalid view type")
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) onBindViewHolder(holder, position)
        else {
            for (payload in payloads.toSet()) {
                when (payload) {
                    Payload.SELECTION_MODE -> holder.bindActionMode(getItemId(position))
                    Payload.HIGHLIGHT -> when (val item = currentList[position]) {
                        is SudokuItem -> holder.bindSudoku(item.sudoku)
                        is SeparatorItem -> Unit
                    }
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = currentList[position]) {
            is SeparatorItem -> holder.textView.text = item.indexText
            is SudokuItem -> {
                holder.bindSudoku(item.sudoku)
                holder.bindActionMode(getItemId(position))
            }
        }
    }

    inner class ViewHolder(itemView: View, var isSeparator: Boolean) : RecyclerView.ViewHolder(itemView) {
        var textView: TextView
        private var textViewSmall: TextView? = null
        private var checkBox: CheckBox? = null
        private var imageView: ImageView? = null

        init {
            if (isSeparator) {
                textView = itemView as TextView
            } else {
                textView = itemView.findViewById(R.id.item_text)
                textViewSmall = itemView.findViewById(R.id.item_text_small)
                checkBox = itemView.findViewById(R.id.checkbox)
                imageView = itemView.findViewById(R.id.item_icon)
            }
        }

        @SuppressLint("SetTextI18n", "StringFormatInvalid")
        fun bindSudoku(sudoku: Sudoku) {
            textView.text = sudoku.sizeString + " | " + sudoku.difficulty.getLocalString(context.resources)
            imageView?.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    if (sudoku.completed) dev.oneuiproject.oneui.R.drawable.ic_oui_crown_outline
                    else dev.oneuiproject.oneui.R.drawable.ic_oui_time_outline
                )
            )
            if (sudoku.errorLimitReached(errorLimit)) imageView?.setImageDrawable(
                ContextCompat.getDrawable(context, dev.oneuiproject.oneui.R.drawable.ic_oui_error)
            )
            textViewSmall?.text = context.getString(R.string.current_time, sudoku.timeString) + " | " +
                    if (!sudoku.completed) {
                        context.getString(R.string.current_progress, sudoku.progress) + " | "
                    } else {
                        ""
                    } +
                    if (errorLimit == 0) {
                        context.getString(R.string.current_errors, sudoku.errorsMade)
                    } else {
                        context.getString(R.string.current_errors_with_limit, sudoku.errorsMade, errorLimit)
                    } +
                    " | " + context.getString(R.string.current_hints, sudoku.hintsUsed)
        }

        fun bindActionMode(itemId: Long) {
            checkBox?.apply {
                isVisible = isActionMode
                isChecked = isSelected(itemId)
            }
        }
    }

    enum class Payload {
        SELECTION_MODE,
        HIGHLIGHT
    }
}