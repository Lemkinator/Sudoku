package de.lemke.sudoku.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityIntroBinding
import de.lemke.sudoku.domain.*
import de.lemke.sudoku.domain.model.*
import de.lemke.sudoku.ui.utils.SudokuViewAdapter
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.utils.internal.ReflectUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs


@AndroidEntryPoint
class IntroActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIntroBinding
    private lateinit var loadingDialog: ProgressDialog
    private lateinit var toolbarMenu: Menu
    private var colorPrimary: Int = 0
    lateinit var gameAdapter: SudokuViewAdapter
    private val sudokuButtons: MutableList<AppCompatButton> = mutableListOf()
    private var notesEnabled = false
    private var selected: Int? = null
    private var time: Long = 0

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initOnBackPressed()

        loadingDialog = ProgressDialog(this)
        loadingDialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
        loadingDialog.setCancelable(false)
        loadingDialog.show()

        val typedValue = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        colorPrimary = typedValue.data

        binding.sudokuToolbarLayout.toolbar.inflateMenu(R.menu.intro_menu)
        toolbarMenu = binding.sudokuToolbarLayout.toolbar.menu
        setSupportActionBar(null)
        initSudoku()
        binding.noteButton.setOnClickListener { toggleOrSetNoteButton() }
        binding.sudokuToolbarLayout.setNavigationButtonOnClickListener { finish() }
        binding.sudokuToolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        binding.sudokuToolbarLayout.appBarLayout.addOnOffsetChangedListener { layout: AppBarLayout, verticalOffset: Int ->
            val totalScrollRange = layout.totalScrollRange
            val inputMethodWindowVisibleHeight = ReflectUtils.genericInvokeMethod(
                InputMethodManager::class.java, getSystemService(INPUT_METHOD_SERVICE), "getInputMethodWindowVisibleHeight"
            ) as Int
            if (totalScrollRange != 0) {
                binding.resumeButtonLayout.translationY = (abs(verticalOffset) - totalScrollRange) / 2f
            } else {
                binding.resumeButtonLayout.translationY = (abs(verticalOffset) - inputMethodWindowVisibleHeight) / 2f
            }
            /*val width = binding.sudokuLayout.measuredWidth
            val height = binding.sudokuLayout.measuredHeight
            val gameSize: Int
            if (totalScrollRange != 0) {
                gameSize = min(width, height - totalScrollRange - verticalOffset)
                binding.resumeButtonLayout.translationY = (abs(verticalOffset) - totalScrollRange) / 2f
            } else {
                gameSize = min(width, height - inputMethodWindowVisibleHeight - verticalOffset)
                binding.resumeButtonLayout.translationY = (abs(verticalOffset) - inputMethodWindowVisibleHeight) / 2f
            }
            val params: ViewGroup.LayoutParams = binding.roundedGameRecycler.layoutParams
            params.width = gameSize
            params.height = gameSize
            binding.roundedGameRecycler.translationX = max(0, width - gameSize) / 2f
            binding.roundedGameRecycler.layoutParams = params*/
        }
    }

    private fun initOnBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                if (System.currentTimeMillis() - time < 3000) finishAffinity()
                else {
                    Toast.makeText(this, resources.getString(R.string.press_again_to_exit), Toast.LENGTH_SHORT).show()
                    time = System.currentTimeMillis()
                }
            }
        else onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (System.currentTimeMillis() - time < 3000) finishAffinity()
                else {
                    Toast.makeText(this@IntroActivity, resources.getString(R.string.press_again_to_exit), Toast.LENGTH_SHORT).show()
                    time = System.currentTimeMillis()
                }
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_skip -> openMainActivity()
        }
        return true
    }


    private fun initSudoku() {
        binding.sudokuToolbarLayout.setTitle(getString(R.string.app_name) + " (" + getString(R.string.intro) + ")")
        refreshHintButton()
        binding.gameRecycler.layoutManager = GridLayoutManager(this, sudoku.size)
        gameAdapter = SudokuViewAdapter(this, sudoku)
        binding.gameRecycler.adapter = gameAdapter
        binding.gameRecycler.seslSetFillBottomEnabled(true)
        binding.gameRecycler.seslSetLastRoundedCorner(true)
        sudoku.gameListener = SudokuGameListener()
        initSudokuButtons()
        checkAnyNumberCompleted(null)
        loadingDialog.dismiss()
    }

    private fun initSudokuButtons() {
        sudokuButtons.add(binding.numberButton1)
        sudokuButtons.add(binding.numberButton2)
        sudokuButtons.add(binding.numberButton3)
        sudokuButtons.add(binding.numberButton4)
        sudokuButtons.add(binding.numberButton5)
        sudokuButtons.add(binding.numberButton6)
        sudokuButtons.add(binding.numberButton7)
        sudokuButtons.add(binding.numberButton8)
        sudokuButtons.add(binding.numberButton9)
        for (index in sudokuButtons.indices) {
            sudokuButtons[index].visibility = View.VISIBLE
            sudokuButtons[index].setOnClickListener {
                lifecycleScope.launch { select(sudoku.itemCount + index) }
            }
        }
        binding.deleteButton.setOnClickListener {
            lifecycleScope.launch { select(sudoku.itemCount + sudoku.size) }
        }
        binding.hintButton.setOnClickListener {
            lifecycleScope.launch { select(sudoku.itemCount + sudoku.size + 1) }
        }
    }


    inner class SudokuGameListener : GameListener {
        override fun onFieldClicked(position: Position) {
            lifecycleScope.launch { select(position.index) }
        }

        override fun onFieldChanged(position: Position) {
            gameAdapter.updateFieldView(position.index)
            checkAnyNumberCompleted(sudoku[position].value)
            lifecycleScope.launch {
                checkRowColumnBlockCompleted(position)
            }
        }

        override fun onCompleted(position: Position) {}

        override fun onError() {}

        override fun onTimeChanged() {}
    }

    private fun checkAnyNumberCompleted(currentNumber: Int?) {
        lifecycleScope.launch {
            val completedNumbers = sudoku.getCompletedNumbers()
            completedNumbers.forEach { pair ->
                if (pair.second) {
                    sudokuButtons[pair.first - 1].isEnabled = false
                    sudokuButtons[pair.first - 1].setTextColor(getColor(R.color.secondary_text_icon_color))
                    if (currentNumber == pair.first && selected in sudoku.itemCount until sudoku.itemCount + sudoku.size)
                        selectNextButton(pair.first, completedNumbers)
                } else {
                    sudokuButtons[pair.first - 1].isEnabled = true
                    sudokuButtons[pair.first - 1].setTextColor(getColor(dev.oneuiproject.oneui.design.R.color.oui_primary_text_color))
                }
            }
        }
    }

    private fun selectNextButton(n: Int, completedNumbers: List<Pair<Int, Boolean>>) {
        var number = n
        while (completedNumbers[number - 1].second) {
            number++
            if (number > completedNumbers.size) number = 1 //wrap around
            if (number == n) { //all numbers are completed
                selected = null
                selectButton(null)
                return
            }
        }
        selected = sudoku.itemCount + number - 1
        selectButton(number - 1)
    }

    private fun checkRowColumnBlockCompleted(position: Position) {
        animate(
            position,
            animateRow = sudoku.isRowCompleted(position.row),
            animateColumn = sudoku.isColumnCompleted(position.column),
            animateBlock = sudoku.isBlockCompleted(position.block)
        )
    }

    private fun animate(
        position: Position,
        animateRow: Boolean = false,
        animateColumn: Boolean = false,
        animateBlock: Boolean = false,
        animateSudoku: Boolean = false
    ): Job? {
        if (!animateRow && !animateColumn && !animateBlock && !animateSudoku) return null
        val delay = 60L / sudoku.blockSize
        lifecycleScope.launch {
            gameAdapter.fieldViews.filter {
                (animateRow && it?.position?.row == position.row && it.position.column <= position.column) ||
                        (animateColumn && it?.position?.column == position.column && it.position.row <= position.row) ||
                        (animateBlock && it?.position?.block == position.block && it.position.index <= position.index) ||
                        (animateSudoku && it?.position?.index!! <= position.index)
            }.reversed().forEach { if (animateSudoku) animateField(it?.fieldViewValue, 200L, delay) else animateField(it?.fieldViewValue) }
        }
        return lifecycleScope.launch {
            gameAdapter.fieldViews.filter {
                (animateRow && it?.position?.row == position.row && it.position.column > position.column) ||
                        (animateColumn && it?.position?.column == position.column && it.position.row > position.row) ||
                        (animateBlock && it?.position?.block == position.block && it.position.index > position.index) ||
                        (animateSudoku && it?.position?.index!! > position.index)
            }.forEach { if (animateSudoku) animateField(it?.fieldViewValue, 200L, delay) else animateField(it?.fieldViewValue) }
        }
    }

    private suspend fun animateField(fieldTextView: TextView?, duration: Long = 250L, delay: Long = 120L) {
        fieldTextView?.animate()
            ?.alpha(0.4f)
            ?.scaleX(1.6f)
            ?.scaleY(1.6f)
            ?.rotation(100f)
            ?.setDuration(duration)?.withEndAction {
                fieldTextView.animate()
                    ?.alpha(1f)
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.rotation(0f)
                    ?.setDuration(duration)?.start()
            }?.start()
        delay(delay / sudoku.blockSize)
    }

    private fun selectButton(i: Int?) {
        for (button in sudokuButtons) button.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.transparent))
        binding.deleteButton.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.transparent))
        binding.hintButton.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.transparent))
        if (i != null) {
            when (i) {
                sudoku.size -> binding.deleteButton.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                sudoku.size + 1 -> binding.hintButton.backgroundTintList =
                    ColorStateList.valueOf(colorPrimary)
                else -> {
                    sudokuButtons[i].backgroundTintList = ColorStateList.valueOf(colorPrimary)
                    gameAdapter.highlightNumber(i + 1)
                }
            }
            selected = sudoku.itemCount + i
        } else {
            selected = null
            gameAdapter.highlightNumber(null)
        }
    }

    private fun toggleOrSetNoteButton(enabled: Boolean? = null) {
        notesEnabled = enabled ?: !notesEnabled
        binding.noteButton.backgroundTintList = ColorStateList.valueOf(
            if (notesEnabled) colorPrimary
            else resources.getColor(android.R.color.transparent, theme)
        )
    }

    private fun refreshHintButton() {
        binding.hintButton.visibility = if (sudoku.isHintAvailable) View.VISIBLE else View.GONE
        binding.hintButton.text = getString(R.string.hint, sudoku.availableHints)
    }

    private fun select(newSelected: Int?) {
        if (binding.sudokuToolbarLayout.isExpanded) binding.sudokuToolbarLayout.setExpanded(false, true)
        when (selected) {
            null -> {//nothing is selected
                when (newSelected) {
                    //selected nothing
                    null -> {}
                    //selected field
                    in 0 until sudoku.itemCount -> {
                        gameAdapter.selectFieldView(newSelected, true, true)
                        selected = newSelected
                    }
                    //selected button
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 ->
                        selectButton(newSelected - sudoku.itemCount)
                    //selected nothing
                    else -> {}
                }
            }
            in 0 until sudoku.itemCount -> { //field is selected
                val position = Position.create(selected!!, sudoku.size)
                when (newSelected) {
                    //selected nothing
                    null -> selected = null
                    //selected same field
                    selected -> selected = null
                    //selected field
                    in 0 until sudoku.itemCount -> selected = newSelected
                    //selected number
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size -> {
                        if (sudoku[position].value == null) sudoku.move(position, newSelected - sudoku.itemCount + 1, notesEnabled)
                        selected = null
                    }
                    //selected delete
                    sudoku.itemCount + sudoku.size -> {
                        sudoku.move(position, null, notesEnabled)
                        selected = null
                    }
                    //selected hint
                    sudoku.itemCount + sudoku.size + 1 -> {
                        if (sudoku[position].value == null) sudoku.setHint(position)
                        selected = null
                        refreshHintButton()
                    }
                }
                gameAdapter.selectFieldView(selected, true, true)
            }
            in sudoku.itemCount until sudoku.itemCount + sudoku.size -> { //number button is selected
                when (newSelected) {
                    //selected nothing
                    null -> selectButton(null)
                    //selected same button
                    selected -> selectButton(null)
                    //selected field
                    in 0 until sudoku.itemCount -> {
                        val number = selected!! - sudoku.itemCount + 1
                        sudoku.move(newSelected, selected!! - sudoku.itemCount + 1, notesEnabled)
                        gameAdapter.highlightNumber(number)
                    }
                    //selected button
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> {
                        gameAdapter.selectFieldView(null, true, true)
                        selectButton(newSelected - sudoku.itemCount)
                    }
                    //selected nothing
                    else -> selectButton(null)
                }
            }
            sudoku.itemCount + sudoku.size -> { // delete button is selected
                when (newSelected) {
                    //selected nothing
                    null -> selectButton(null)
                    //selected same button
                    selected -> selectButton(null)
                    //selected field
                    in 0 until sudoku.itemCount -> sudoku.move(newSelected, null, notesEnabled)
                    //selected button(not delete)
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 ->
                        selectButton(newSelected - sudoku.itemCount)
                    //selected nothing
                    else -> selectButton(null)
                }
            }
            sudoku.itemCount + sudoku.size + 1 -> { // hint button is selected
                when (newSelected) {
                    //selected nothing
                    null -> selectButton(null)
                    //selected same button
                    selected -> selectButton(null)
                    //selected field
                    in 0 until sudoku.itemCount -> {
                        if (sudoku[newSelected].value == null) sudoku.setHint(newSelected)
                        if (!sudoku.isHintAvailable) selected = null
                        refreshHintButton()
                    }
                    //selected button(not hint)
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 1 ->
                        selectButton(newSelected - sudoku.itemCount)
                    //selected nothing
                    else -> selectButton(null)
                }
            }
        }
    }

    private fun openMainActivity() {
        startActivity(Intent(applicationContext, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    var sudoku: Sudoku = Sudoku.create(
        size = 9,
        difficulty = Difficulty.VERY_EASY,
        modeLevel = Sudoku.MODE_NORMAL,
        fields = MutableList(81) {
            when (it) {
                0 -> Field(position = Position.create(it, 9), value = 3, solution = 3, given = true)
                1 -> Field(position = Position.create(it, 9), value = 1, solution = 1, given = true)
                2 -> Field(position = Position.create(it, 9), value = 4, solution = 4, given = true)
                3 -> Field(position = Position.create(it, 9), value = null, solution = 2, given = false)
                4 -> Field(position = Position.create(it, 9), value = 5, solution = 5, given = true)
                5 -> Field(position = Position.create(it, 9), value = 9, solution = 9, given = true)
                6 -> Field(position = Position.create(it, 9), value = 6, solution = 6, given = true)
                7 -> Field(position = Position.create(it, 9), value = 8, solution = 8, given = true)
                8 -> Field(position = Position.create(it, 9), value = 7, solution = 7, given = true)

                9 -> Field(position = Position.create(it, 9), value = 8, solution = 8, given = true)
                10 -> Field(position = Position.create(it, 9), value = 2, solution = 2, given = true)
                11 -> Field(position = Position.create(it, 9), value = 5, solution = 5, given = true)
                12 -> Field(position = Position.create(it, 9), value = 4, solution = 4, given = true)
                13 -> Field(position = Position.create(it, 9), value = 6, solution = 6, given = true)
                14 -> Field(position = Position.create(it, 9), value = 7, solution = 7, given = true)
                15 -> Field(position = Position.create(it, 9), value = 1, solution = 1, given = true)
                16 -> Field(position = Position.create(it, 9), value = 9, solution = 9, given = true)
                17 -> Field(position = Position.create(it, 9), value = 3, solution = 3, given = true)

                18 -> Field(position = Position.create(it, 9), value = 7, solution = 7, given = true)
                19 -> Field(position = Position.create(it, 9), value = 9, solution = 9, given = true)
                20 -> Field(position = Position.create(it, 9), value = 6, solution = 6, given = true)
                21 -> Field(position = Position.create(it, 9), value = null, solution = 8, given = false)
                22 -> Field(position = Position.create(it, 9), value = null, solution = 1, given = false)
                23 -> Field(position = Position.create(it, 9), value = 3, solution = 3, given = true)
                24 -> Field(position = Position.create(it, 9), value = null, solution = 2, given = false)
                25 -> Field(position = Position.create(it, 9), value = 5, solution = 5, given = true)
                26 -> Field(position = Position.create(it, 9), value = 4, solution = 4, given = true)

                27 -> Field(position = Position.create(it, 9), value = 1, solution = 1, given = true)
                28 -> Field(position = Position.create(it, 9), value = null, solution = 5, given = false)
                29 -> Field(position = Position.create(it, 9), value = 2, solution = 2, given = true)
                30 -> Field(position = Position.create(it, 9), value = 3, solution = 3, given = true)
                31 -> Field(position = Position.create(it, 9), value = null, solution = 8, given = false)
                32 -> Field(position = Position.create(it, 9), value = 4, solution = 4, given = true)
                33 -> Field(position = Position.create(it, 9), value = 9, solution = 9, given = true)
                34 -> Field(position = Position.create(it, 9), value = null, solution = 7, given = false)
                35 -> Field(position = Position.create(it, 9), value = null, solution = 6, given = false)

                36 -> Field(position = Position.create(it, 9), value = 9, solution = 9, given = true)
                37 -> Field(position = Position.create(it, 9), value = 6, solution = 6, given = true)
                38 -> Field(position = Position.create(it, 9), value = 3, solution = 3, given = true)
                39 -> Field(position = Position.create(it, 9), value = 5, solution = 5, given = true)
                40 -> Field(position = Position.create(it, 9), value = null, solution = 7, given = false)
                41 -> Field(position = Position.create(it, 9), value = 1, solution = 1, given = true)
                42 -> Field(position = Position.create(it, 9), value = null, solution = 8, given = false)
                43 -> Field(position = Position.create(it, 9), value = 4, solution = 4, given = true)
                44 -> Field(position = Position.create(it, 9), value = null, solution = 2, given = false)

                45 -> Field(position = Position.create(it, 9), value = 4, solution = 4, given = true)
                46 -> Field(position = Position.create(it, 9), value = 7, solution = 7, given = true)
                47 -> Field(position = Position.create(it, 9), value = null, solution = 8, given = false)
                48 -> Field(position = Position.create(it, 9), value = 9, solution = 9, given = true)
                49 -> Field(position = Position.create(it, 9), value = null, solution = 2, given = false)
                50 -> Field(position = Position.create(it, 9), value = 6, solution = 6, given = true)
                51 -> Field(position = Position.create(it, 9), value = 5, solution = 5, given = true)
                52 -> Field(position = Position.create(it, 9), value = 3, solution = 3, given = true)
                53 -> Field(position = Position.create(it, 9), value = 1, solution = 1, given = true)

                54 -> Field(position = Position.create(it, 9), value = null, solution = 2, given = false)
                55 -> Field(position = Position.create(it, 9), value = 8, solution = 8, given = true)
                56 -> Field(position = Position.create(it, 9), value = null, solution = 7, given = false)
                57 -> Field(position = Position.create(it, 9), value = null, solution = 6, given = false)
                58 -> Field(position = Position.create(it, 9), value = null, solution = 3, given = false)
                59 -> Field(position = Position.create(it, 9), value = 5, solution = 5, given = true)
                60 -> Field(position = Position.create(it, 9), value = null, solution = 4, given = false)
                61 -> Field(position = Position.create(it, 9), value = null, solution = 1, given = false)
                62 -> Field(position = Position.create(it, 9), value = 9, solution = 9, given = true)

                63 -> Field(position = Position.create(it, 9), value = null, solution = 6, given = false)
                64 -> Field(position = Position.create(it, 9), value = null, solution = 4, given = false)
                65 -> Field(position = Position.create(it, 9), value = 1, solution = 1, given = true)
                66 -> Field(position = Position.create(it, 9), value = null, solution = 7, given = false)
                67 -> Field(position = Position.create(it, 9), value = null, solution = 9, given = false)
                68 -> Field(position = Position.create(it, 9), value = 8, solution = 8, given = true)
                69 -> Field(position = Position.create(it, 9), value = 3, solution = 3, given = true)
                70 -> Field(position = Position.create(it, 9), value = null, solution = 2, given = false)
                71 -> Field(position = Position.create(it, 9), value = 5, solution = 5, given = true)

                72 -> Field(position = Position.create(it, 9), value = null, solution = 5, given = false)
                73 -> Field(position = Position.create(it, 9), value = null, solution = 3, given = false)
                74 -> Field(position = Position.create(it, 9), value = 9, solution = 9, given = true)
                75 -> Field(position = Position.create(it, 9), value = 1, solution = 1, given = true)
                76 -> Field(position = Position.create(it, 9), value = 4, solution = 4, given = true)
                77 -> Field(position = Position.create(it, 9), value = 2, solution = 2, given = true)
                78 -> Field(position = Position.create(it, 9), value = 7, solution = 7, given = true)
                79 -> Field(position = Position.create(it, 9), value = 6, solution = 6, given = true)
                80 -> Field(position = Position.create(it, 9), value = 8, solution = 8, given = true)

                else -> throw IllegalArgumentException("Invalid index: $it")
            }
        }
    )
}