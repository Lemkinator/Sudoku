/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.sudoku.ui.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuListItem
import de.lemke.sudoku.domain.model.SudokuListItem.SeparatorItem
import de.lemke.sudoku.domain.model.SudokuListItem.SudokuItem
import de.lemke.sudoku.domain.model.formatFull
import de.lemke.sudoku.ui.utils.SudokuListAdapter.Mode
import dev.oneuiproject.oneui.widget.Separator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val SIZE = 4

private fun solutionFor(index: Int): Int {
    val blockSize = 2
    val row = index / SIZE
    val col = index % SIZE
    return (blockSize * (row % blockSize) + row / blockSize + col) % SIZE + 1
}

private fun sudokuFixture(
    modeLevel: Int = Sudoku.MODE_NORMAL,
    errorsMade: Int = 0,
    hintsUsed: Int = 0,
    completed: Boolean = false,
    created: LocalDateTime = LocalDateTime.of(2024, 3, 15, 10, 30),
): Sudoku =
    Sudoku.create(
        size = SIZE,
        difficulty = Difficulty.EASY,
        modeLevel = modeLevel,
        errorsMade = errorsMade,
        hintsUsed = hintsUsed,
        created = created,
        fields =
            MutableList(SIZE * SIZE) { index ->
                val solution = solutionFor(index)
                Field(Position.create(index, SIZE), solution = solution, value = if (completed) solution else null, given = completed)
            },
    )

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SudokuListAdapterTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var context: AppCompatActivity

    @Before
    fun setUp() {
        context = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
    }

    private fun buildAdapter(
        mode: Mode = Mode.NORMAL,
        errorLimit: Int = 0,
    ) = SudokuListAdapter(context, errorLimit = errorLimit, mode = mode)

    private fun expectedSmallText(
        sudoku: Sudoku,
        errorLimit: Int,
        mode: Mode,
    ): String =
        buildString {
            append(context.getString(R.string.current_time, sudoku.timeString))
            if (!sudoku.completed) {
                append(" | ").append(context.getString(R.string.current_progress, sudoku.progress))
            }
            append(" | ").append(
                if (errorLimit == 0) {
                    context.getString(R.string.current_errors, sudoku.errorsMade)
                } else {
                    context.getString(R.string.current_errors_with_limit, sudoku.errorsMade, errorLimit)
                },
            )
            if (mode == Mode.NORMAL) {
                append(" | ").append(context.getString(R.string.current_hints, sudoku.hintsUsed))
            }
        }

    // Under Robolectric, separately inflated VectorDrawables never compare equal, so compare rendered pixels.
    private fun Drawable.toComparableBitmap(): Bitmap {
        val width = intrinsicWidth.coerceAtLeast(1)
        val height = intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, width, height)
        draw(Canvas(bitmap))
        return bitmap
    }

    private fun referenceIconBitmap(resId: Int): Bitmap {
        val referenceHolder =
            LayoutInflater
                .from(context)
                .inflate(R.layout.sudoku_list_item, FrameLayout(context), false)
        val referenceImageView = referenceHolder.findViewById<ImageView>(R.id.item_icon)
        referenceImageView.setImageDrawable(ContextCompat.getDrawable(context, resId))
        return referenceImageView.drawable.shouldNotBeNull().toComparableBitmap()
    }

    private fun assertIcon(
        holder: SudokuListAdapter.ViewHolder,
        expectedResId: Int,
    ) {
        val imageView = holder.itemView.findViewById<ImageView>(R.id.item_icon)
        val actual = imageView.drawable.shouldNotBeNull().toComparableBitmap()
        actual.sameAs(referenceIconBitmap(expectedResId)).shouldBeTrue()
    }

    private fun smallText(holder: SudokuListAdapter.ViewHolder): String =
        holder.itemView
            .findViewById<TextView>(R.id.item_text_small)
            .text
            .toString()

    @Test
    fun `getItemViewType distinguishes sudoku items from separators`() {
        val sudokuItem = SudokuItem(sudokuFixture(), "A")
        val separatorItem = SeparatorItem("Sep")
        val adapter = buildAdapter()
        adapter.submitList(listOf(sudokuItem, separatorItem))

        adapter.getItemViewType(0) shouldBe SudokuItem.VIEW_TYPE
        adapter.getItemViewType(1) shouldBe SeparatorItem.VIEW_TYPE
    }

    @Test
    fun `getItemId and getItemCount reflect the submitted list`() {
        val sudokuItem = SudokuItem(sudokuFixture(), "A")
        val separatorItem = SeparatorItem("Sep")
        val adapter = buildAdapter()
        adapter.submitList(listOf(sudokuItem, separatorItem))

        adapter.getItemId(0) shouldBe sudokuItem.stableId
        adapter.getItemId(1) shouldBe separatorItem.stableId
        adapter.itemCount shouldBe 2
    }

    @Test
    fun `onCreateViewHolder for a sudoku item view type builds a non-separator holder`() {
        val holder = buildAdapter().onCreateViewHolder(FrameLayout(context), SudokuItem.VIEW_TYPE)

        holder.isSeparator shouldBe false
        holder.selectableLayout.shouldNotBeNull()
    }

    @Test
    fun `onCreateViewHolder for a separator view type builds a separator holder`() {
        val holder = buildAdapter().onCreateViewHolder(FrameLayout(context), SeparatorItem.VIEW_TYPE)

        holder.isSeparator shouldBe true
        (holder.itemView is Separator) shouldBe true
        val layoutParams = holder.itemView.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.width shouldBe ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height shouldBe ViewGroup.LayoutParams.WRAP_CONTENT
    }

    @Test
    fun `onCreateViewHolder throws for an unknown view type`() {
        shouldThrow<IllegalArgumentException> {
            buildAdapter().onCreateViewHolder(FrameLayout(context), 999)
        }
    }

    @Test
    fun `onBindViewHolder shows the separator's index text`() {
        val separatorItem = SeparatorItem("Sep")
        val adapter = buildAdapter()
        adapter.submitList(listOf(separatorItem))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), SeparatorItem.VIEW_TYPE)

        adapter.onBindViewHolder(holder, 0)

        holder.textView.text shouldBe "Sep"
    }

    @Test
    fun `onBindViewHolder in NORMAL mode with no error limit shows size, difficulty and hints`() {
        val sudoku = sudokuFixture(hintsUsed = 2)
        val adapter = buildAdapter(mode = Mode.NORMAL, errorLimit = 0)
        adapter.submitList(listOf(SudokuItem(sudoku, "A")))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), SudokuItem.VIEW_TYPE)

        adapter.onBindViewHolder(holder, 0)

        holder.textView.text shouldBe "${sudoku.sizeString} | ${sudoku.difficulty.getLocalString(context.resources)}"
        smallText(holder) shouldBe expectedSmallText(sudoku, errorLimit = 0, mode = Mode.NORMAL)
        assertIcon(holder, dev.oneuiproject.oneui.R.drawable.ic_oui_time_outline)
    }

    @Test
    fun `onBindViewHolder with an error limit not yet reached keeps the time icon`() {
        val sudoku = sudokuFixture(errorsMade = 1)
        val adapter = buildAdapter(mode = Mode.NORMAL, errorLimit = 3)
        adapter.submitList(listOf(SudokuItem(sudoku, "A")))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), SudokuItem.VIEW_TYPE)

        adapter.onBindViewHolder(holder, 0)

        smallText(holder) shouldBe expectedSmallText(sudoku, errorLimit = 3, mode = Mode.NORMAL)
        assertIcon(holder, dev.oneuiproject.oneui.R.drawable.ic_oui_time_outline)
    }

    @Test
    fun `onBindViewHolder in LEVEL mode with the error limit reached shows the level and error icon`() {
        val sudoku = sudokuFixture(modeLevel = 5, errorsMade = 3)
        val adapter = buildAdapter(mode = Mode.LEVEL, errorLimit = 3)
        adapter.submitList(listOf(SudokuItem(sudoku, "A")))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), SudokuItem.VIEW_TYPE)

        adapter.onBindViewHolder(holder, 0)

        holder.textView.text shouldBe "${context.getString(R.string.level)} 5"
        smallText(holder) shouldBe expectedSmallText(sudoku, errorLimit = 3, mode = Mode.LEVEL)
        assertIcon(holder, dev.oneuiproject.oneui.R.drawable.ic_oui_error)
    }

    @Test
    fun `onBindViewHolder in DAILY mode for a completed sudoku shows the date and crown icon`() {
        val created = LocalDateTime.of(2024, 3, 15, 10, 30)
        val sudoku = sudokuFixture(completed = true, created = created)
        val adapter = buildAdapter(mode = Mode.DAILY, errorLimit = 3)
        adapter.submitList(listOf(SudokuItem(sudoku, "A")))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), SudokuItem.VIEW_TYPE)

        adapter.onBindViewHolder(holder, 0)

        holder.textView.text shouldBe created.toLocalDate().formatFull
        smallText(holder) shouldBe expectedSmallText(sudoku, errorLimit = 3, mode = Mode.DAILY)
        assertIcon(holder, dev.oneuiproject.oneui.R.drawable.ic_oui_crown_outline)
    }

    @Test
    fun `onBindViewHolder with empty payloads delegates to the plain bind`() {
        val sudoku = sudokuFixture()
        val adapter = buildAdapter()
        adapter.submitList(listOf(SudokuItem(sudoku, "A")))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), SudokuItem.VIEW_TYPE)

        adapter.onBindViewHolder(holder, 0, mutableListOf())

        holder.textView.text shouldBe "${sudoku.sizeString} | ${sudoku.difficulty.getLocalString(context.resources)}"
    }

    @Test
    fun `onBindViewHolder with a SELECTION_MODE payload updates action mode without rebinding content`() {
        val sudoku = sudokuFixture()
        val adapter = buildAdapter()
        adapter.submitList(listOf(SudokuItem(sudoku, "A")))
        // MultiSelectorDelegate's lateinit `adapter` is set only by configureWith().
        adapter.configureWith(layoutRecyclerViewWith(adapter))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), SudokuItem.VIEW_TYPE)
        holder.textView.text = "sentinel"
        adapter.toggleActionMode(true)

        adapter.onBindViewHolder(holder, 0, mutableListOf(SudokuListAdapter.Payload.SELECTION_MODE))

        holder.textView.text shouldBe "sentinel"
        holder.selectableLayout?.isSelectionMode shouldBe true
    }

    @Test
    fun `onBindViewHolder with a HIGHLIGHT payload rebinds a sudoku item's content`() {
        val sudoku = sudokuFixture()
        val adapter = buildAdapter(errorLimit = 3)
        adapter.submitList(listOf(SudokuItem(sudoku, "A")))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), SudokuItem.VIEW_TYPE)
        adapter.onBindViewHolder(holder, 0)
        sudoku.errorsMade = 3

        adapter.onBindViewHolder(holder, 0, mutableListOf(SudokuListAdapter.Payload.HIGHLIGHT))

        smallText(holder) shouldBe expectedSmallText(sudoku, errorLimit = 3, mode = Mode.NORMAL)
    }

    @Test
    fun `onBindViewHolder with a HIGHLIGHT payload leaves a separator untouched`() {
        val separatorItem = SeparatorItem("Sep")
        val adapter = buildAdapter()
        adapter.submitList(listOf(separatorItem))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), SeparatorItem.VIEW_TYPE)
        holder.textView.text = "sentinel"

        adapter.onBindViewHolder(holder, 0, mutableListOf(SudokuListAdapter.Payload.HIGHLIGHT))

        holder.textView.text shouldBe "sentinel"
    }

    // ListAdapter diffs every submitList after the first on a background thread.
    private fun SudokuListAdapter.submitListAndAwait(list: List<SudokuListItem>) {
        submitList(list)
        val deadline = System.currentTimeMillis() + 5_000
        while (currentList != list && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        check(currentList == list) { "submitList's background diff did not complete within 5000ms" }
    }

    @Test
    fun `submitList diffs sudoku and separator items across updates`() {
        val sudokuA = sudokuFixture()
        val itemA = SudokuItem(sudokuA, "A")
        val separator = SeparatorItem("Sep")
        val adapter = buildAdapter()
        adapter.submitListAndAwait(listOf(itemA, separator))

        adapter.currentList shouldBe listOf(itemA, separator)

        sudokuA.errorsMade = 5
        val itemAChanged = SudokuItem(sudokuA, "A")
        adapter.submitListAndAwait(listOf(itemAChanged, separator))

        adapter.currentList shouldBe listOf(itemAChanged, separator)

        val itemB = SudokuItem(sudokuFixture(), "B")
        adapter.submitListAndAwait(listOf(itemB))

        adapter.currentList shouldBe listOf(itemB)

        val otherSeparator = SeparatorItem("Other")
        adapter.submitListAndAwait(listOf(otherSeparator))

        adapter.currentList shouldBe listOf(otherSeparator)
    }

    @Suppress("UNCHECKED_CAST")
    private fun diffCallback(): DiffUtil.ItemCallback<SudokuListItem> {
        // Kotlin stores this private companion val's backing field as a static on SudokuListAdapter itself.
        val field = SudokuListAdapter::class.java.getDeclaredField("diffCallback").apply { isAccessible = true }
        return field.get(null) as DiffUtil.ItemCallback<SudokuListItem>
    }

    @Test
    fun `diffCallback matches separators by stable id independently of sudoku items`() {
        val callback = diffCallback()
        val separatorA = SeparatorItem("A")
        val separatorASame = SeparatorItem("A")
        val separatorB = SeparatorItem("B")

        callback.areItemsTheSame(separatorA, separatorASame) shouldBe true
        callback.areItemsTheSame(separatorA, separatorB) shouldBe false
        callback.areContentsTheSame(separatorA, separatorASame) shouldBe true
        callback.areContentsTheSame(separatorA, separatorB) shouldBe false
    }

    @Test
    fun `diffCallback falls through to else for a sudoku item and separator pair`() {
        val callback = diffCallback()
        val sudokuItem = SudokuItem(sudokuFixture(), "A")
        val separator = SeparatorItem("A")

        callback.areItemsTheSame(sudokuItem, separator) shouldBe false
        callback.areItemsTheSame(separator, sudokuItem) shouldBe false
        callback.areContentsTheSame(sudokuItem, separator) shouldBe false
        callback.areContentsTheSame(separator, sudokuItem) shouldBe false
    }

    private fun layoutRecyclerViewWith(adapter: SudokuListAdapter): RecyclerView {
        val recyclerView =
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter
            }
        context.setContentView(recyclerView)
        shadowOf(Looper.getMainLooper()).idle()
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        recyclerView.layout(0, 0, 1080, 1920)
        shadowOf(Looper.getMainLooper()).idle()
        return recyclerView
    }

    @Test
    fun `the click listener wired in onCreateViewHolder reports the bound position`() {
        val sudoku = sudokuFixture()
        val item = SudokuItem(sudoku, "A")
        val adapter = buildAdapter()
        adapter.submitList(listOf(item))
        var clicked: Pair<Int, SudokuListItem>? = null
        adapter.onClickItem = { position, listItem, _ -> clicked = position to listItem }

        val recyclerView = layoutRecyclerViewWith(adapter)
        val holder = recyclerView.findViewHolderForAdapterPosition(0).shouldNotBeNull()
        holder.itemView.performClick()

        clicked shouldBe (0 to item)
    }

    @Test
    fun `the long-click listener wired in onCreateViewHolder invokes onLongClickItem and consumes the event`() {
        val sudoku = sudokuFixture()
        val item = SudokuItem(sudoku, "A")
        val adapter = buildAdapter()
        adapter.submitList(listOf(item))
        var longClicked = false
        adapter.onLongClickItem = { longClicked = true }

        val recyclerView = layoutRecyclerViewWith(adapter)
        val holder = recyclerView.findViewHolderForAdapterPosition(0).shouldNotBeNull()
        holder.itemView.performLongClick().shouldBeTrue()

        longClicked.shouldBeTrue()
    }

    @Test
    fun `a SELECTION_MODE payload on a separator holder is a no-op instead of crashing`() {
        val separatorItem = SeparatorItem("Sep")
        val adapter = buildAdapter()
        adapter.submitList(listOf(separatorItem))
        // MultiSelectorDelegate's lateinit `adapter` is set only by configureWith().
        adapter.configureWith(layoutRecyclerViewWith(adapter))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), SeparatorItem.VIEW_TYPE)
        holder.textView.text = "sentinel"
        adapter.toggleActionMode(true)

        adapter.onBindViewHolder(holder, 0, mutableListOf(SudokuListAdapter.Payload.SELECTION_MODE))

        holder.textView.text shouldBe "sentinel"
        holder.selectableLayout shouldBe null
    }
}
