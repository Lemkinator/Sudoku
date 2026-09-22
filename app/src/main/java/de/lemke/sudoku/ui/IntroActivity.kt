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

package de.lemke.sudoku.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.R.anim.fade_in
import android.R.anim.fade_out
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.res.ColorStateList
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.advanceOnboarding
import de.lemke.commonutils.ui.utils.collectEvents
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityIntroBinding
import de.lemke.sudoku.domain.model.GameListener
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.tutorialSudoku
import de.lemke.sudoku.ui.utils.FieldView
import de.lemke.sudoku.ui.utils.SudokuViewAdapter
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.dialog.ProgressDialog.ProgressStyle.CIRCLE
import java.util.Timer
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import de.lemke.commonutils.R as commonutilsR
import dev.oneuiproject.oneui.design.R as designR

private const val INTRO_STEP_3 = 3
private const val INTRO_STEP_4 = 4
private const val INTRO_STEP_5 = 5
private const val INTRO_STEP_6 = 6
private const val INTRO_STEP_7 = 7
private const val INTRO_STEP_8 = 8
private const val INTRO_STEP_9 = 9
private const val INTRO_STEP_10 = 10

// Fixed cell/button indices on the tutorialSudoku() board that this scripted walkthrough highlights.
internal const val DEMO_CELL_INDEX_4 = 4
internal const val DEMO_CELL_INDEX_24 = 24
internal const val DEMO_CELL_INDEX_49 = 49
internal const val DEMO_NUMBER_BUTTON_INDEX_4 = 4
private const val DEMO_CELL_INDEX_21 = 21
private const val DEMO_NUMBER_BUTTON_INDEX_7 = 7
private const val DEMO_ROW_INDEX_3 = 3
private const val DEMO_ROW_INDEX_4 = 4
private const val DEMO_FIELD_HIGHLIGHT_SCALE = 1.5f

private const val ROW_COLUMN_BLOCK_ANIMATION_DURATION_MILLIS = 200L
private const val FIELD_ANIMATION_FADE_ALPHA = 0.4f
private const val FIELD_ANIMATION_SCALE = 1.6f
private const val FIELD_ANIMATION_ROTATION_DEGREES = 100f

@AndroidEntryPoint
class IntroActivity : AppCompatActivity() {
    internal lateinit var binding: ActivityIntroBinding
    private lateinit var loadingDialog: ProgressDialog
    internal var colorPrimary: Int = 0
    lateinit var gameAdapter: SudokuViewAdapter
    internal val sudokuButtons: MutableList<AppCompatButton> = mutableListOf()
    internal var selected: Int? = null
    internal var introStep = -1
    internal var animation: Job? = null
    internal var notesEnabled = false
    internal val viewModel: IntroViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
            viewModel.onNotificationPermissionResult(isGranted)
        }

    var sudoku: Sudoku = tutorialSudoku()

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        collectEvents(viewModel.events, minActiveState = RESUMED) { event ->
            when (event) {
                IntroEvent.AdvanceOnboarding -> {
                    advanceOnboarding()
                }

                IntroEvent.RequestNotificationPermission -> {
                    if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestPermissionLauncher.launch(POST_NOTIFICATIONS)
                }
            }
        }

        loadingDialog = ProgressDialog(this)
        loadingDialog.setProgressStyle(CIRCLE)
        loadingDialog.setCancelable(false)
        loadingDialog.show()

        val typedValue = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        colorPrimary = typedValue.data

        initSudoku()
        binding.introContinueButton.setOnClickListener { showNotificationsDialogOrFinish() }
        binding.introNextButton.setOnClickListener { nextIntroStep() }
        binding.noteButton.setOnClickListener { toggleOrSetNoteButton() }
        loadingDialog.dismiss()
        nextIntroStep()
    }

    override fun onCreateOptionsMenu(menu: Menu?) = menuInflater.inflate(R.menu.intro_menu, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.menu_skip -> showNotificationsDialogOrFinish().let { true }
            else -> super.onOptionsItemSelected(item)
        }

    internal fun nextIntroStep() {
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
                binding.introTitle.isVisible = false
                binding.introTextText.text = getString(R.string.intro_text2)
                startAnimation(2)
            }

            INTRO_STEP_3 -> {
                binding.introTextText.text = getString(R.string.intro_text3)
                binding.otherButtons.isVisible = false
                binding.gameButtons.isVisible = true
                stopAnimation(2)
            }

            INTRO_STEP_4 -> {
                binding.introTextText.text = getString(R.string.intro_text4)
            }

            INTRO_STEP_5 -> {
                binding.introTextText.text = getString(R.string.intro_text5)
                startAnimation(INTRO_STEP_5)
            }

            INTRO_STEP_6 -> {
                binding.introTextText.text = getString(R.string.intro_text6)
                stopAnimation(INTRO_STEP_5)
                startAnimation(INTRO_STEP_6)
            }

            INTRO_STEP_7 -> {
                binding.introTitleText.text = getString(R.string.intro_title7)
                binding.introTitle.isVisible = true
                binding.introTextText.text = getString(R.string.intro_text7)
                stopAnimation(INTRO_STEP_6)
            }

            INTRO_STEP_8 -> {
                binding.introTitleText.text = getString(R.string.intro_title8)
                binding.introTextText.text = getString(R.string.intro_text8)
                startAnimation(INTRO_STEP_8)
            }

            INTRO_STEP_9 -> {
                binding.introTitleText.text = getString(R.string.intro_title9)
                binding.introTextText.text = getString(R.string.intro_text9)
                binding.otherButtons.isVisible = true
                binding.numberButtons.isVisible = false
                stopAnimation(INTRO_STEP_8)
            }

            INTRO_STEP_10 -> {
                binding.introTitle.isVisible = false
                binding.introTextText.text = getString(R.string.intro_text10)
                binding.introContinueLayout.isVisible = true
            }
        }
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
            sudokuButtons[index].isVisible = true
            sudokuButtons[index].setOnClickListener { lifecycleScope.launch { select(sudoku.itemCount + index) } }
        }
        binding.deleteButton.setOnClickListener { lifecycleScope.launch { select(sudoku.itemCount + sudoku.size) } }
        binding.hintButton.setOnClickListener { lifecycleScope.launch { select(sudoku.itemCount + sudoku.size + 1) } }
    }

    internal fun toggleOrSetNoteButton() {
        if (introStep != INTRO_STEP_10) return
        notesEnabled = !notesEnabled
        binding.noteButton.backgroundTintList =
            ColorStateList.valueOf(
                if (notesEnabled) {
                    colorPrimary
                } else {
                    resources.getColor(android.R.color.transparent, theme)
                },
            )
    }

    internal fun selectButton(i: Int?) {
        for (button in sudokuButtons) button.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.transparent))
        binding.deleteButton.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.transparent))
        binding.hintButton.backgroundTintList = ColorStateList.valueOf(getColor(android.R.color.transparent))
        if (i != null) {
            when (i) {
                sudoku.size -> {
                    binding.deleteButton.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                }

                sudoku.size + 1 -> {
                    binding.hintButton.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                }

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
        binding.hintButton.isVisible = sudoku.isHintAvailable
        binding.hintButton.text = getString(R.string.hint, sudoku.availableHints)
    }

    internal fun select(newSelected: Int?) {
        if (binding.sudokuToolbarLayout.isExpanded) binding.sudokuToolbarLayout.setExpanded(expanded = false, animate = true)
        when (selected) {
            null -> selectFromNothing(newSelected)

            // nothing is selected
            in 0 until sudoku.itemCount -> selectFromField(newSelected)

            // field is selected
            in sudoku.itemCount until sudoku.itemCount + sudoku.size -> selectFromNumberButton(newSelected) // number button is selected
        }
    }

    private fun selectFromNothing(newSelected: Int?) {
        when (newSelected) {
            // selected field
            in 0 until sudoku.itemCount -> {
                val field = newSelected!!
                if (field == DEMO_CELL_INDEX_4 && introStep == 2) {
                    gameAdapter.selectFieldView(field, highlightNeighbors = true, highlightNumber = true)
                    selected = field
                    nextIntroStep()
                }
            }

            // selected button
            in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> {
                val button = newSelected!!
                if (introStep == INTRO_STEP_4 && button == sudoku.itemCount + 1) {
                    selectButton(button - sudoku.itemCount)
                    nextIntroStep()
                }
            }

            // selected nothing
            else -> {}
        }
    }

    private fun selectFromField(newSelected: Int?) {
        val position = Position.create(selected!!, sudoku.size)
        when (newSelected) {
            // selected nothing
            null -> {
                selected = null
            }

            // selected number
            in sudoku.itemCount until sudoku.itemCount + sudoku.size -> {
                val number = newSelected!!
                if (introStep == INTRO_STEP_3 && number == sudoku.itemCount + DEMO_NUMBER_BUTTON_INDEX_4) {
                    sudoku.move(position, number - sudoku.itemCount + 1, false)
                    selected = null
                    gameAdapter.selectFieldView(null, highlightNeighbors = true, highlightNumber = true)
                    nextIntroStep()
                }
            }
        }
    }

    private fun selectFromNumberButton(newSelected: Int?) {
        when (newSelected) {
            // selected nothing
            null -> {
                selectButton(null)
            }

            // selected field
            in 0 until sudoku.itemCount -> {
                val field = newSelected!!
                val number = selected!! - sudoku.itemCount + 1
                if ((introStep == INTRO_STEP_5 && field == DEMO_CELL_INDEX_49) ||
                    (introStep == INTRO_STEP_6 && field == DEMO_CELL_INDEX_24)
                ) {
                    sudoku.move(field, number, false)
                    gameAdapter.highlightNumber(number)
                    nextIntroStep()
                }
            }
        }
    }

    internal fun checkRowColumnBlockCompleted(position: Position) {
        animate(
            position,
            animateRow = sudoku.isRowCompleted(position.row),
            animateColumn = sudoku.isColumnCompleted(position.column),
            animateBlock = sudoku.isBlockCompleted(position.block),
        )
    }

    internal fun animate(
        position: Position,
        animateRow: Boolean = false,
        animateColumn: Boolean = false,
        animateBlock: Boolean = false,
        animateSudoku: Boolean = false,
    ): Job? {
        if (!animateRow && !animateColumn && !animateBlock && !animateSudoku) return null
        val delay = 60L / sudoku.blockSize
        lifecycleScope.launch {
            gameAdapter.fieldViews
                .filter { matchesAnimation(it, position, animateRow, animateColumn, animateBlock, animateSudoku) { a, b -> a <= b } }
                .reversed()
                .forEach {
                    if (animateSudoku) {
                        animateField(it.fieldViewValue, ROW_COLUMN_BLOCK_ANIMATION_DURATION_MILLIS, delay)
                    } else {
                        animateField(it.fieldViewValue)
                    }
                }
        }
        return lifecycleScope.launch {
            gameAdapter.fieldViews
                .filter { matchesAnimation(it, position, animateRow, animateColumn, animateBlock, animateSudoku) { a, b -> a > b } }
                .forEach {
                    if (animateSudoku) {
                        animateField(it.fieldViewValue, ROW_COLUMN_BLOCK_ANIMATION_DURATION_MILLIS, delay)
                    } else {
                        animateField(it.fieldViewValue)
                    }
                }
        }
    }

    private fun matchesAnimation(
        fieldView: FieldView,
        position: Position,
        animateRow: Boolean,
        animateColumn: Boolean,
        animateBlock: Boolean,
        animateSudoku: Boolean,
        compare: (Int, Int) -> Boolean,
    ): Boolean =
        (animateRow && fieldView.position.row == position.row && compare(fieldView.position.column, position.column)) ||
            (animateColumn && fieldView.position.column == position.column && compare(fieldView.position.row, position.row)) ||
            (animateBlock && fieldView.position.block == position.block && compare(fieldView.position.index, position.index)) ||
            (animateSudoku && compare(fieldView.position.index, position.index))

    private suspend fun animateField(
        fieldTextView: TextView?,
        duration: Long = 250L,
        delay: Long = 120L,
    ) {
        fieldTextView?.let {
            it
                .animate()
                .alpha(FIELD_ANIMATION_FADE_ALPHA)
                .scaleX(FIELD_ANIMATION_SCALE)
                .scaleY(FIELD_ANIMATION_SCALE)
                .rotation(FIELD_ANIMATION_ROTATION_DEGREES)
                .setDuration(duration)
                .withEndAction {
                    it
                        .animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .rotation(0f)
                        .setDuration(duration)
                        .start()
                }.start()
        }
        delay((delay / sudoku.blockSize).milliseconds)
    }

    internal fun startAnimation(currentIntroStep: Int) {
        animation =
            lifecycleScope.launch {
                when (currentIntroStep) {
                    0 -> {
                        animateIntroStepRowColumnBlock()
                    }

                    2 -> {
                        animateIntroStepField(rows = listOf(0), fieldIndex = DEMO_CELL_INDEX_4, introStep = 2)
                    }

                    INTRO_STEP_5 -> {
                        animateIntroStepField(
                            rows = listOf(DEMO_ROW_INDEX_3, DEMO_ROW_INDEX_4),
                            fieldIndex = DEMO_CELL_INDEX_49,
                            introStep = INTRO_STEP_5,
                        )
                    }

                    INTRO_STEP_6 -> {
                        animateIntroStepBlock()
                    }

                    INTRO_STEP_8 -> {
                        animateIntroStepNumberEntry()
                    }
                }
            }
    }

    private suspend fun animateIntroStepRowColumnBlock() {
        while (introStep == 0) {
            delay(900.milliseconds)
            val block = gameAdapter.fieldViews.filter { it.position.block == 0 }
            val row = gameAdapter.fieldViews.filter { it.position.row == 1 }
            val column = gameAdapter.fieldViews.filter { it.position.column == 5 }
            column.forEach {
                it.isHighlighted = false
                it.setBackground()
            }
            block.forEach {
                it.isHighlighted = true
                it.setBackground()
            }
            block.forEach { animateIntroFieldText(it.fieldViewValue) }
            delay(900.milliseconds)
            block.forEach {
                it.isHighlighted = false
                it.setBackground()
            }
            row.forEach {
                it.isHighlighted = true
                it.setBackground()
            }
            row.forEach { animateIntroFieldText(it.fieldViewValue) }
            delay(900.milliseconds)
            row.forEach {
                it.isHighlighted = false
                it.setBackground()
            }
            column.forEach {
                it.isHighlighted = true
                it.setBackground()
            }
            column.forEach { animateIntroFieldText(it.fieldViewValue) }
        }
    }

    private suspend fun animateIntroStepField(
        rows: List<Int>,
        fieldIndex: Int,
        introStep: Int,
    ) {
        gameAdapter.fieldViews.filter { it.position.row in rows }.forEach {
            it.isHighlighted = true
            it.setBackground()
        }
        while (this.introStep == introStep) animateIntroFieldView(gameAdapter.fieldViews[fieldIndex])
    }

    private suspend fun animateIntroStepBlock() {
        gameAdapter.fieldViews.filter { it.position.block == 2 }.forEach {
            it.isHighlighted = true
            it.setBackground()
        }
        while (introStep == INTRO_STEP_6) animateIntroFieldView(gameAdapter.fieldViews[DEMO_CELL_INDEX_24])
    }

    private suspend fun animateIntroStepNumberEntry() {
        val delayMillis = 1200L
        while (introStep == INTRO_STEP_8) {
            delay(delayMillis.milliseconds)
            selectButton(null)
            gameAdapter.selectFieldView(DEMO_CELL_INDEX_4, highlightNeighbors = true, highlightNumber = true)
            delay(delayMillis.milliseconds)
            gameAdapter.selectFieldView(null, highlightNeighbors = true, highlightNumber = true)
            selectButton(DEMO_NUMBER_BUTTON_INDEX_7)
            delay(delayMillis.milliseconds)
            selectButton(DEMO_NUMBER_BUTTON_INDEX_4)
            delay(delayMillis.milliseconds)
            selectButton(null)
            gameAdapter.selectFieldView(DEMO_CELL_INDEX_21, highlightNeighbors = true, highlightNumber = true)
            delay(delayMillis.milliseconds)
            gameAdapter.selectFieldView(null, highlightNeighbors = true, highlightNumber = true)
            selectButton(1)
        }
    }

    internal fun stopAnimation(currentIntroStep: Int) {
        animation?.cancel()
        when (currentIntroStep) {
            0, INTRO_STEP_5, INTRO_STEP_6 -> {
                gameAdapter.fieldViews.forEach {
                    it.isHighlighted = false
                    it.setBackground()
                }
            }

            INTRO_STEP_8 -> {
                selectButton(null)
                gameAdapter.selectFieldView(null, highlightNeighbors = true, highlightNumber = true)
            }
        }
    }

    private suspend fun animateIntroFieldText(
        fieldTextView: TextView?,
        duration: Long = 450,
        delay: Long = 180L,
    ) {
        fieldTextView?.let {
            it
                .animate()
                .scaleX(2f)
                .scaleY(2f)
                .setDuration(duration)
                .withEndAction {
                    it
                        .animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(duration)
                        .start()
                }.start()
        }
        delay(delay.milliseconds)
    }

    private suspend fun animateIntroFieldView(
        fieldView: FieldView,
        duration: Long = 600,
        delay: Long = 2000,
    ) {
        fieldView
            .animate()
            .scaleX(DEMO_FIELD_HIGHLIGHT_SCALE)
            .scaleY(DEMO_FIELD_HIGHLIGHT_SCALE)
            .setDuration(duration)
            .withEndAction {
                fieldView
                    .animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(duration)
                    .start()
            }.start()
        delay(delay.milliseconds)
    }

    private fun showNotificationsDialogOrFinish() {
        if (!viewModel.openedFromSettings) notificationsDialog() else finishAfterTransition()
    }

    private fun notificationsDialog() {
        val dialog =
            AlertDialog
                .Builder(this)
                .setTitle(getString(R.string.notifications_title))
                .setMessage(getString(R.string.daily_sudoku_notification_channel_description))
                .setNegativeButton(R.string.decline_notifications) { _: DialogInterface, _: Int ->
                    viewModel.onNotificationsDeclined()
                }.setPositiveButton(commonutilsR.string.commonutils_ok) { _: DialogInterface, _: Int ->
                    viewModel.onNotificationsAccepted()
                }.setCancelable(false)
                .create()
        dialog.show()
        dialog.getButton(BUTTON_NEGATIVE).setTextColor(getColor(designR.color.oui_des_functional_red_color))
    }

    companion object {
        const val KEY_OPENED_FROM_SETTINGS = "openedFromSettings"
    }

    inner class SudokuGameListener : GameListener {
        // Onboarding board has no timer/error/completion UI to update.
        override fun onTimeChanged() { /* no-op */ }

        override fun onError() { /* no-op */ }

        override fun onCompleted(position: Position) { /* no-op */ }

        override fun onFieldClicked(position: Position) {
            lifecycleScope.launch { select(position.index) }
        }

        override fun onFieldChanged(position: Position) {
            gameAdapter.updateFieldView(position.index)
            lifecycleScope.launch {
                checkRowColumnBlockCompleted(position)
            }
        }
    }
}
