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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityIntroBinding
import de.lemke.sudoku.domain.*
import de.lemke.sudoku.domain.model.*
import de.lemke.sudoku.ui.utils.FieldView
import de.lemke.sudoku.ui.utils.SudokuViewAdapter
import dev.oneuiproject.oneui.dialog.ProgressDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class IntroActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIntroBinding
    private lateinit var loadingDialog: ProgressDialog
    private lateinit var toolbarMenu: Menu
    private var colorPrimary: Int = 0
    lateinit var gameAdapter: SudokuViewAdapter
    private val sudokuButtons: MutableList<AppCompatButton> = mutableListOf()
    private var selected: Int? = null
    private var time: Long = 0
    private var introStep = -1
    private var animation: Job? = null
    private var notesEnabled = false
    private var openedFromSettings = false

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        }
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        openedFromSettings = intent.getBooleanExtra("openedFromSettings", false)

        if (!openedFromSettings) {
            setCustomOnBackPressedLogic { backPressed() }
        }

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
        binding.sudokuToolbarLayout.setNavigationButtonOnClickListener { backPressed() }
        binding.introContinueButton.setOnClickListener { lifecycleScope.launch { openMainActivity() } }
        binding.sudokuToolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        binding.introNextButton.setOnClickListener { nextIntroStep() }
        binding.noteButton.setOnClickListener { toggleOrSetNoteButton() }
        nextIntroStep()
    }

    private fun nextIntroStep() {
        introStep += 1
        when (introStep) {
            0 -> {
                binding.introTitleText.text = getString(R.string.intro_title)
                binding.introTextText.text = getString(R.string.intro_text0)
                startAnimation(0)
            }
            1 -> {
                stopAnimation(0)
                binding.introTextText.text = getString(R.string.intro_text1)
            }
            2 -> {
                binding.introTitle.visibility = View.GONE
                binding.introTextText.text = getString(R.string.intro_text2)
                startAnimation(2)
            }
            3 -> {
                binding.introTextText.text = getString(R.string.intro_text3)
                binding.otherButtons.visibility = View.GONE
                binding.gameButtons.visibility = View.VISIBLE
                stopAnimation(2)
            }
            4 -> {
                binding.introTextText.text = getString(R.string.intro_text4)
            }
            5 -> {
                binding.introTextText.text = getString(R.string.intro_text5)
                startAnimation(5)
            }
            6 -> {
                binding.introTextText.text = getString(R.string.intro_text6)
                stopAnimation(5)
                startAnimation(6)
            }
            7 -> {
                binding.introTitleText.text = getString(R.string.intro_title7)
                binding.introTitle.visibility = View.VISIBLE
                binding.introTextText.text = getString(R.string.intro_text7)
                stopAnimation(6)
            }
            8 -> {
                binding.introTitleText.text = getString(R.string.intro_title8)
                binding.introTextText.text = getString(R.string.intro_text8)
                startAnimation(8)
            }
            9 -> {
                binding.introTitleText.text = getString(R.string.intro_title9)
                binding.introTextText.text = getString(R.string.intro_text9)
                binding.otherButtons.visibility = View.VISIBLE
                binding.numberButtons.visibility = View.GONE
                stopAnimation(8)
            }
            10 -> {
                binding.introTitle.visibility = View.GONE
                binding.introTextText.text = getString(R.string.intro_text10)
                binding.introContinueLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun backPressed() {
        when {
            System.currentTimeMillis() - time < 3000 -> finishAffinity()
            else -> {
                Toast.makeText(this, resources.getString(R.string.press_again_to_exit), Toast.LENGTH_SHORT).show()
                time = System.currentTimeMillis()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_skip -> lifecycleScope.launch { openMainActivity() }
        }
        return true
    }


    private fun initSudoku() {
        binding.sudokuToolbarLayout.setTitle(getString(R.string.intro))
        refreshHintButton()
        binding.gameRecycler.layoutManager = GridLayoutManager(this, sudoku.size)
        gameAdapter = SudokuViewAdapter(this, sudoku)
        binding.gameRecycler.adapter = gameAdapter
        binding.gameRecycler.seslSetFillBottomEnabled(true)
        binding.gameRecycler.seslSetLastRoundedCorner(true)
        sudoku.gameListener = SudokuGameListener()
        sudoku.timer = Timer()
        initSudokuButtons()
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
            lifecycleScope.launch {
                checkRowColumnBlockCompleted(position)
            }
        }

        override fun onCompleted(position: Position) {}

        override fun onError() {}

        override fun onTimeChanged() {}
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

    private fun startAnimation(currentIntroStep: Int) {
        animation = lifecycleScope.launch {
            when (currentIntroStep) {
                0 -> {
                    while (introStep == 0) {
                        delay(900)
                        val block = gameAdapter.fieldViews.filter { (it?.position?.block == 0) }
                        val row = gameAdapter.fieldViews.filter { (it?.position?.row == 1) }
                        val column = gameAdapter.fieldViews.filter { (it?.position?.column == 5) }
                        column.forEach {
                            it?.isHighlighted = false
                            it?.setBackground()
                        }
                        block.forEach {
                            it?.isHighlighted = true
                            it?.setBackground()
                        }
                        block.forEach { animateIntroFieldText(it?.fieldViewValue) }
                        delay(900)
                        block.forEach {
                            it?.isHighlighted = false
                            it?.setBackground()
                        }
                        row.forEach {
                            it?.isHighlighted = true
                            it?.setBackground()
                        }
                        row.forEach { animateIntroFieldText(it?.fieldViewValue) }
                        delay(900)
                        row.forEach {
                            it?.isHighlighted = false
                            it?.setBackground()
                        }
                        column.forEach {
                            it?.isHighlighted = true
                            it?.setBackground()
                        }
                        column.forEach { animateIntroFieldText(it?.fieldViewValue) }
                    }
                }
                2 -> {
                    gameAdapter.fieldViews.filter { (it?.position?.row == 0) }.forEach {
                        it?.isHighlighted = true
                        it?.setBackground()
                    }
                    while (introStep == 2) animateIntroFieldView(gameAdapter.fieldViews[4])
                }
                5 -> {
                    gameAdapter.fieldViews.filter { (it?.position?.row == 3 || it?.position?.row == 4) }.forEach {
                        it?.isHighlighted = true
                        it?.setBackground()
                    }
                    while (introStep == 5) animateIntroFieldView(gameAdapter.fieldViews[49])
                }
                6 -> {
                    gameAdapter.fieldViews.filter { it?.position?.block == 2 }.forEach {
                        it?.isHighlighted = true
                        it?.setBackground()
                    }
                    while (introStep == 6) animateIntroFieldView(gameAdapter.fieldViews[24])
                }
                8 -> {
                    val delayMillis = 1200L
                    while (introStep == 8) {
                        delay(delayMillis)
                        selectButton(null)
                        gameAdapter.selectFieldView(4, highlightNeighbors = true, highlightNumber = true)
                        delay(delayMillis)
                        gameAdapter.selectFieldView(null, highlightNeighbors = true, highlightNumber = true)
                        selectButton(7)
                        delay(delayMillis)
                        selectButton(4)
                        delay(delayMillis)
                        selectButton(null)
                        gameAdapter.selectFieldView(21, highlightNeighbors = true, highlightNumber = true)
                        delay(delayMillis)
                        gameAdapter.selectFieldView(null, highlightNeighbors = true, highlightNumber = true)
                        selectButton(1)
                    }
                }
            }
        }

    }

    private fun stopAnimation(currentIntroStep: Int) {
        animation?.cancel()
        when (currentIntroStep) {
            0,5,6 -> {
                gameAdapter.fieldViews.forEach {
                    it?.isHighlighted = false
                    it?.setBackground()
                }
            }
            8 -> {
                selectButton(null)
                gameAdapter.selectFieldView(null, highlightNeighbors = true, highlightNumber = true)
            }
        }


    }

    private suspend fun animateIntroFieldText(fieldTextView: TextView?, duration: Long = 450, delay: Long = 180L) {
        fieldTextView?.animate()
            ?.scaleX(2f)
            ?.scaleY(2f)
            ?.setDuration(duration)?.withEndAction {
                fieldTextView.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setDuration(duration)?.start()
            }?.start()
        delay(delay)
    }
    private suspend fun animateIntroFieldView(fieldView: FieldView?, duration: Long = 600, delay: Long = 2000) {
        fieldView?.animate()
            ?.scaleX(1.5f)
            ?.scaleY(1.5f)
            ?.setDuration(duration)?.withEndAction {
                fieldView.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setDuration(duration)?.start()
            }?.start()
        delay(delay)
    }

    private fun toggleOrSetNoteButton(enabled: Boolean? = null) {
        if (introStep != 10) return
        notesEnabled = enabled ?: !notesEnabled
        binding.noteButton.backgroundTintList = ColorStateList.valueOf(
            if (notesEnabled) colorPrimary
            else resources.getColor(android.R.color.transparent, theme)
        )
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
                        if (newSelected == 4 && introStep == 2) {
                            gameAdapter.selectFieldView(newSelected, highlightNeighbors = true, highlightNumber = true)
                            selected = newSelected
                            nextIntroStep()
                        }
                    }
                    //selected button
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> {
                        if (introStep == 4 && newSelected == sudoku.itemCount + 1) {
                            selectButton(newSelected - sudoku.itemCount)
                            nextIntroStep()
                        }
                    }
                    //selected nothing
                    else -> {}
                }
            }
            in 0 until sudoku.itemCount -> { //field is selected
                val position = Position.create(selected!!, sudoku.size)
                when (newSelected) {
                    //selected nothing
                    null -> selected = null
                    //selected number
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size -> {
                        if (introStep == 3 && newSelected == sudoku.itemCount + 4) {
                            sudoku.move(position, newSelected - sudoku.itemCount + 1, false)
                            selected = null
                            gameAdapter.selectFieldView(null, highlightNeighbors = true, highlightNumber = true)
                            nextIntroStep()
                        }
                    }
                }
            }
            in sudoku.itemCount until sudoku.itemCount + sudoku.size -> { //number button is selected
                when (newSelected) {
                    //selected nothing
                    null -> selectButton(null)
                    //selected field
                    in 0 until sudoku.itemCount -> {
                        val number = selected!! - sudoku.itemCount + 1
                        if (introStep == 5 && newSelected == 49 || introStep == 6 && newSelected == 24) {
                            sudoku.move(newSelected, selected!! - sudoku.itemCount + 1, false)
                            gameAdapter.highlightNumber(number)
                            nextIntroStep()
                        }
                    }
                }
            }
        }
    }

    private suspend fun openMainActivity() {
        if (!openedFromSettings) {
            updateUserSettings { it.copy(tosAccepted = true) }
            startActivity(Intent(applicationContext, MainActivity::class.java))
            if (Build.VERSION.SDK_INT < 34) {
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
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
                3 -> Field(position = Position.create(it, 9), value = 2, solution = 2, given = true)
                4 -> Field(position = Position.create(it, 9), value = null, solution = 5, given = false)
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
                44 -> Field(position = Position.create(it, 9), value = 2, solution = 2, given = true)

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