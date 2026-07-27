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

import android.annotation.SuppressLint
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_STREAM
import android.content.Intent.EXTRA_TEXT
import android.content.Intent.EXTRA_TITLE
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.games.PlayGames
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.prepareActivityTransformationTo
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.showInAppReviewIfPossible
import de.lemke.commonutils.ui.utils.toast
import de.lemke.commonutils.ui.utils.transformTo
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.databinding.ActivitySudokuBinding
import de.lemke.sudoku.domain.model.GameListener
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_DAILY_ERROR_LIMIT
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_LEVEL_ERROR_LIMIT
import de.lemke.sudoku.domain.model.Sudoku.Companion.MODE_NORMAL
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.dateFormatShort
import de.lemke.sudoku.ui.utils.FieldView
import de.lemke.sudoku.ui.utils.SudokuViewAdapter
import de.lemke.sudoku.ui.utils.applyPlayGamesSync
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.dialog.ProgressDialog.ProgressStyle.CIRCLE
import dev.oneuiproject.oneui.ktx.setOnClickListenerWithProgress
import java.time.LocalDate
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import de.lemke.commonutils.R as commonutilsR
import dev.oneuiproject.oneui.R as oneuiR
import dev.oneuiproject.oneui.design.R as designR

@AndroidEntryPoint
class SudokuActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySudokuBinding
    private lateinit var loadingDialog: ProgressDialog
    lateinit var sudoku: Sudoku
    lateinit var gameAdapter: SudokuViewAdapter
    private val sudokuButtons: MutableList<AppCompatButton> = mutableListOf()
    private var notesEnabled = false
    private var selected: Int? = null
    private var menuPausePlayVisible = false
    private var menuResetVisible = false
    private val accelerateDecelerateInterpolator = AccelerateDecelerateInterpolator()

    private val colorPrimary get() = ColorStateList.valueOf(getColor(R.color.primary_color_themed))
    private val transparent get() = ColorStateList.valueOf(getColor(android.R.color.transparent))

    @Inject
    lateinit var userSettings: UserSettings

    private val viewModel: SudokuViewModel by viewModels()

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivitySudokuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        val id = intent.getStringExtra(KEY_SUDOKU_ID)
        if (id == null) {
            Log.e("SudokuActivity", "Sudoku ID not provided")
            toast(R.string.error_sudoku_not_found)
            finishAfterTransition()
            return
        }
        loadingDialog = ProgressDialog(this)
        loadingDialog.setProgressStyle(CIRCLE)
        loadingDialog.setCancelable(false)
        lifecycleScope.launch {
            val nullableSudoku = viewModel.loadSudoku(SudokuId(id))
            if (nullableSudoku == null) {
                Log.e("SudokuActivity", "Sudoku not found")
                toast(R.string.error_sudoku_not_found)
                finishAfterTransition()
            } else {
                initSudoku(nullableSudoku)
            }
        }
        binding.noteButton.setOnClickListener { toggleOrSetNoteButton() }
    }

    override fun onPause() {
        super.onPause()
        if (this::sudoku.isInitialized) pauseGame()
    }

    override fun onCreateOptionsMenu(menu: Menu?) = menuInflater.inflate(R.menu.sudoku_menu, menu).let { true }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (!this::sudoku.isInitialized) return false
        menu?.findItem(R.id.menu_pause_play)?.let {
            it.icon =
                AppCompatResources.getDrawable(
                    this,
                    if (sudoku.resumed) {
                        oneuiR.drawable.ic_oui_control_pause
                    } else {
                        oneuiR.drawable.ic_oui_control_play
                    },
                )
            it.title = getString(if (sudoku.resumed) R.string.commonutils_pause else R.string.commonutils_resume)
        }
        menu?.findItem(R.id.menu_reset)?.isVisible = menuResetVisible
        menu?.findItem(R.id.menu_pause_play)?.isVisible = menuPausePlayVisible
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.menu_pause_play -> (if (sudoku.resumed) pauseGame() else resumeGame()).let { true }
            R.id.menu_reset -> restartGame().let { true }
            R.id.menu_share -> shareDialog().let { true }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        digitKeyButtons[keyCode]?.let { (minSize, buttonIndex) ->
            return if (sudoku.size > minSize) select(sudoku.itemCount + buttonIndex).let { true } else false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DEL -> select(sudoku.itemCount + sudoku.size).let { true }
            KeyEvent.KEYCODE_H -> if (sudoku.isHintAvailable) select(sudoku.itemCount + sudoku.size + 1).let { true } else false
            KeyEvent.KEYCODE_N -> toggleOrSetNoteButton().let { true }
            KeyEvent.KEYCODE_ESCAPE -> select(null).let { true }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    private fun initSudoku(sudoku: Sudoku) {
        this.sudoku = sudoku
        setTitle()
        setSubtitle()
        binding.gameRecycler.layoutManager = GridLayoutManager(this@SudokuActivity, sudoku.size)
        gameAdapter = SudokuViewAdapter(this@SudokuActivity, sudoku)
        binding.gameRecycler.adapter = gameAdapter
        sudoku.gameListener = SudokuGameListener()
        initSudokuButtons()
        resumeGame()
        loadingDialog.dismiss()
    }

    private fun initSudokuButtons() {
        sudokuButtons.clear()
        if (sudoku.size >= 4) {
            sudokuButtons.add(binding.numberButton1)
            sudokuButtons.add(binding.numberButton2)
            sudokuButtons.add(binding.numberButton3)
            sudokuButtons.add(binding.numberButton4)
        }
        if (sudoku.size >= 9) {
            sudokuButtons.add(binding.numberButton5)
            sudokuButtons.add(binding.numberButton6)
            sudokuButtons.add(binding.numberButton7)
            sudokuButtons.add(binding.numberButton8)
            sudokuButtons.add(binding.numberButton9)
        }
        if (sudoku.size >= 16) {
            sudokuButtons.add(binding.numberButtonA)
            sudokuButtons.add(binding.numberButtonB)
            sudokuButtons.add(binding.numberButtonC)
            sudokuButtons.add(binding.numberButtonD)
            sudokuButtons.add(binding.numberButtonE)
            sudokuButtons.add(binding.numberButtonF)
            sudokuButtons.add(binding.numberButtonG)
        }
        for (index in sudokuButtons.indices) {
            sudokuButtons[index].isVisible = true
            sudokuButtons[index].setOnClickListener { select(sudoku.itemCount + index) }
        }
        binding.deleteButton.setOnClickListener { select(sudoku.itemCount + sudoku.size) }
        binding.hintButton.setOnClickListener { select(sudoku.itemCount + sudoku.size + 1) }
        binding.resumeButton.setOnClickListener { resumeGame() }
        selectButton(null, false)
        checkAnyNumberCompleted()
        refreshHintButton()
    }

    fun resumeGame() {
        if (!this::sudoku.isInitialized) return
        binding.resumeButton.transformTo(binding.gameLayout)
        if (sudoku.completed || (sudoku.isDailySudoku && sudoku.created.toLocalDate() != LocalDate.now())) {
            menuPausePlayVisible = false
            menuResetVisible = true
            animateGameButtonsVisibility(false)
        } else {
            sudoku.startTimer()
            menuPausePlayVisible = true
            menuResetVisible = false
            animateGameButtonsVisibility(true)
        }
        invalidateOptionsMenu()
        checkErrorLimit()
        if (userSettings.keepScreenOn) window.addFlags(FLAG_KEEP_SCREEN_ON)
    }

    private fun pauseGame() {
        sudoku.stopTimer()
        if (sudoku.completed) return
        if (binding.gameLayout.isVisible) binding.gameLayout.transformTo(binding.resumeButton)
        animateGameButtonsVisibility(false)
        menuResetVisible = false
        menuPausePlayVisible = true
        invalidateOptionsMenu()
        if (userSettings.keepScreenOn) window.clearFlags(FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch { viewModel.saveSudokuProgress(sudoku, onlyUpdate = true) }
    }

    private fun animateGameButtonsVisibility(visible: Boolean) {
        val value = if (visible) 1f else 0f
        binding.gameButtons
            .animate()
            .setInterpolator(accelerateDecelerateInterpolator)
            .alpha(value)
            .scaleX(value)
            .scaleY(value)
            .setDuration(300L)
            .start()
    }

    private fun onSudokuCompleted() {
        setSubtitle()
        menuResetVisible = true
        menuPausePlayVisible = false
        invalidateOptionsMenu()
        animateGameButtonsVisibility(false)
        val dialog =
            AlertDialog
                .Builder(this@SudokuActivity)
                .setTitle(R.string.completed_title)
                .setMessage(sudoku.getLocalStatisticsString(resources))
                .setNeutralButton(commonutilsR.string.commonutils_ok, null)
        lifecycleScope.launch {
            viewModel.saveSudokuProgress(sudoku, onlyUpdate = true)
            if (sudoku.isSudokuLevel &&
                viewModel.isMaxSudokuLevel(sudoku.size, sudoku.modeLevel)
            ) {
                dialog.setPositiveButton(R.string.next_level) { _, _ ->
                    lifecycleScope.launch {
                        loadingDialog.show()
                        val nextSudokuLevel = viewModel.generateNextLevelSudoku(sudoku.size, level = sudoku.modeLevel + 1)
                        viewModel.saveSudokuProgress(nextSudokuLevel)
                        initSudoku(nextSudokuLevel)
                    }
                }
            } else if (sudoku.isNormalSudoku) {
                dialog.setPositiveButton(R.string.new_game) { _, _ ->
                    lifecycleScope.launch {
                        loadingDialog.show()
                        val newSudoku = viewModel.generateNewSudoku(sudoku.size, sudoku.difficulty)
                        viewModel.saveSudokuProgress(newSudoku)
                        initSudoku(newSudoku)
                    }
                }
            }
            dialog.show()
            applyPlayGamesSync(viewModel.syncPlayGames(sudoku))
            showInAppReviewIfPossible()
        }
    }

    private fun checkErrorLimit(): Boolean {
        val errorLimit =
            when {
                sudoku.isDailySudoku -> MODE_DAILY_ERROR_LIMIT
                sudoku.isSudokuLevel -> MODE_LEVEL_ERROR_LIMIT
                else -> userSettings.errorLimit
            }
        if (sudoku.errorLimitReached(errorLimit)) {
            sudoku.stopTimer()
            setSubtitle()
            animateGameButtonsVisibility(false)
            menuResetVisible = true
            menuPausePlayVisible = false
            invalidateOptionsMenu()
            AlertDialog
                .Builder(this@SudokuActivity)
                .setTitle(R.string.gameover)
                .setMessage(getString(R.string.error_limit_reached, errorLimit))
                .setPositiveButton(R.string.restart) { _, _ -> restartGame() }
                .setNeutralButton(commonutilsR.string.commonutils_ok, null)
                .show()
            return true
        }
        return false
    }

    private fun restartGame() {
        loadingDialog.show()
        sudoku.reset()
        lifecycleScope.launch {
            viewModel.saveSudokuProgress(sudoku)
            initSudoku(sudoku)
        }
    }

    private fun select(newSelected: Int?) {
        if (checkErrorLimit()) return
        if (binding.sudokuToolbarLayout.isExpanded) binding.sudokuToolbarLayout.setExpanded(expanded = false, animate = true)
        when (selected) {
            null -> selectFromNothing(newSelected)

            // nothing is selected
            in 0 until sudoku.itemCount -> selectFromField(newSelected)

            // field is selected
            in sudoku.itemCount until sudoku.itemCount + sudoku.size -> selectFromNumberButton(newSelected)

            // number button is selected
            sudoku.itemCount + sudoku.size -> selectFromDeleteButton(newSelected)

            // delete button is selected
            sudoku.itemCount + sudoku.size + 1 -> selectFromHintButton(newSelected) // hint button is selected
        }
    }

    private fun selectFromNothing(newSelected: Int?) {
        when (newSelected) {
            // selected nothing
            null -> {}

            // selected field
            in 0 until sudoku.itemCount -> {
                gameAdapter.selectFieldView(newSelected, userSettings.highlightRegional, userSettings.highlightNumber)
                selected = newSelected
            }

            // selected button
            in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> {
                selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
            }

            // selected nothing
            else -> {}
        }
    }

    private fun selectFromField(newSelected: Int?) {
        val position = Position.create(selected!!, sudoku.size)
        when (newSelected) {
            // selected nothing / selected same field
            null, selected -> {
                selected = null
            }

            // selected field
            in 0 until sudoku.itemCount -> {
                selected = newSelected
            }

            // selected number
            in sudoku.itemCount until sudoku.itemCount + sudoku.size -> {
                sudoku.move(position, newSelected - sudoku.itemCount + 1, notesEnabled)
                selected = null
            }

            // selected delete
            sudoku.itemCount + sudoku.size -> {
                sudoku.move(position, null, notesEnabled)
                selected = null
            }

            // selected hint
            sudoku.itemCount + sudoku.size + 1 -> {
                sudoku.setHint(position)
                selected = null
                refreshHintButton()
            }
        }
        gameAdapter.selectFieldView(selected, userSettings.highlightRegional, userSettings.highlightNumber)
    }

    private fun selectFromNumberButton(newSelected: Int?) {
        when (newSelected) {
            // selected nothing / selected same button
            null, selected -> {
                selectButton(null, userSettings.highlightNumber)
            }

            // selected field
            in 0 until sudoku.itemCount -> {
                sudoku.move(newSelected, selected!! - sudoku.itemCount + 1, notesEnabled)
                highlightCurrentNumber(selected!! - sudoku.itemCount + 1)
            }

            // selected button
            in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> {
                gameAdapter.selectFieldView(null, userSettings.highlightRegional, userSettings.highlightNumber)
                selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
            }

            // selected nothing
            else -> {
                selectButton(null, userSettings.highlightNumber)
            }
        }
    }

    private fun selectFromDeleteButton(newSelected: Int?) {
        when (newSelected) {
            // selected nothing / selected same button
            null, selected -> {
                selectButton(null, userSettings.highlightNumber)
            }

            // selected field
            in 0 until sudoku.itemCount -> {
                sudoku.move(newSelected, null, notesEnabled)
            }

            // selected button(not delete)
            in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> {
                selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
            }

            // selected nothing
            else -> {
                selectButton(null, userSettings.highlightNumber)
            }
        }
    }

    private fun selectFromHintButton(newSelected: Int?) {
        when (newSelected) {
            // selected nothing / selected same button
            null, selected -> {
                selectButton(null, userSettings.highlightNumber)
            }

            // selected field
            in 0 until sudoku.itemCount -> {
                sudoku.setHint(newSelected)
                if (!sudoku.isHintAvailable) selected = null
                refreshHintButton()
            }

            // selected button(not hint)
            in sudoku.itemCount until sudoku.itemCount + sudoku.size + 1 -> {
                selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
            }

            // selected nothing
            else -> {
                selectButton(null, userSettings.highlightNumber)
            }
        }
    }

    private fun checkRowColumnBlockCompleted(position: Position) {
        if (userSettings.animationsEnabled) {
            animate(
                position,
                animateRow = sudoku.isRowCompleted(position.row),
                animateColumn = sudoku.isColumnCompleted(position.column),
                animateBlock = sudoku.isBlockCompleted(position.block),
            )
        }
    }

    private fun animate(
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
                .forEach { if (animateSudoku) animateField(it?.fieldViewValue, 200L, delay) else animateField(it?.fieldViewValue) }
        }
        return lifecycleScope.launch {
            gameAdapter.fieldViews
                .filter { matchesAnimation(it, position, animateRow, animateColumn, animateBlock, animateSudoku) { a, b -> a > b } }
                .forEach { if (animateSudoku) animateField(it?.fieldViewValue, 200L, delay) else animateField(it?.fieldViewValue) }
        }
    }

    private fun matchesAnimation(
        fieldView: FieldView?,
        position: Position,
        animateRow: Boolean,
        animateColumn: Boolean,
        animateBlock: Boolean,
        animateSudoku: Boolean,
        compare: (Int, Int) -> Boolean,
    ): Boolean =
        (animateRow && fieldView?.position?.row == position.row && compare(fieldView.position.column, position.column)) ||
            (animateColumn && fieldView?.position?.column == position.column && compare(fieldView.position.row, position.row)) ||
            (animateBlock && fieldView?.position?.block == position.block && compare(fieldView.position.index, position.index)) ||
            (animateSudoku && compare(fieldView?.position?.index!!, position.index))

    private suspend fun animateField(
        fieldTextView: TextView?,
        duration: Long = 250L,
        delay: Long = 120L,
    ) {
        fieldTextView
            ?.animate()
            ?.alpha(0.4f)
            ?.scaleX(1.6f)
            ?.scaleY(1.6f)
            ?.rotation(100f)
            ?.setDuration(duration)
            ?.withEndAction {
                fieldTextView
                    .animate()
                    ?.alpha(1f)
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.rotation(0f)
                    ?.setDuration(duration)
                    ?.start()
            }?.start()
        delay((delay / sudoku.blockSize).milliseconds)
    }

    private fun selectButton(
        i: Int?,
        highlightSelectedNumber: Boolean,
    ) {
        for (button in sudokuButtons) button.backgroundTintList = transparent
        binding.deleteButton.backgroundTintList = transparent
        binding.hintButton.backgroundTintList = transparent
        if (i != null) {
            when (i) {
                sudoku.size -> {
                    binding.deleteButton.backgroundTintList = colorPrimary
                }

                sudoku.size + 1 -> {
                    binding.hintButton.backgroundTintList = colorPrimary
                }

                else -> {
                    sudokuButtons[i].backgroundTintList = colorPrimary
                    if (highlightSelectedNumber) gameAdapter.highlightNumber(i + 1)
                }
            }
            selected = sudoku.itemCount + i
        } else {
            selected = null
            if (highlightSelectedNumber) gameAdapter.highlightNumber(null)
        }
    }

    private fun selectNextButton(
        currentNumber: Int,
        completedNumbers: List<Pair<Int, Boolean>>,
    ) {
        var number = currentNumber
        while (completedNumbers[number - 1].second) {
            number++
            if (number > completedNumbers.size) number = 1 // wrap around
            if (number == currentNumber) { // all numbers are completed
                selectButton(null, userSettings.highlightNumber)
                return
            }
        }
        selectButton(number - 1, userSettings.highlightNumber)
    }

    private fun checkAnyNumberCompleted() {
        sudoku.getCompletedNumbers().forEach { pair ->
            if (pair.second) {
                sudokuButtons[pair.first - 1].isEnabled = false
                sudokuButtons[pair.first - 1].setTextColor(getColor(commonutilsR.color.commonutils_secondary_text_icon_color))
            } else {
                sudokuButtons[pair.first - 1].isEnabled = true
                sudokuButtons[pair.first - 1].setTextColor(getColor(commonutilsR.color.commonutils_primary_text_icon_color))
            }
        }
    }

    private fun highlightCurrentNumber(currentNumber: Int) {
        val completedNumbers = sudoku.getCompletedNumbers()
        if (completedNumbers.find { it.first == currentNumber } != null) {
            if (selected in sudoku.itemCount until sudoku.itemCount + sudoku.size) {
                selectNextButton(currentNumber, completedNumbers)
            }
        } else {
            if (userSettings.highlightNumber) gameAdapter.highlightNumber(currentNumber)
        }
    }

    private fun toggleOrSetNoteButton(enabled: Boolean? = null) {
        notesEnabled = enabled ?: !notesEnabled
        binding.noteButton.backgroundTintList = if (notesEnabled) colorPrimary else transparent
    }

    private fun refreshHintButton() {
        binding.hintButton.isVisible = sudoku.isHintAvailable
        binding.hintButton.text = getString(R.string.hint, sudoku.availableHints)
    }

    private fun setTitle() {
        binding.sudokuToolbarLayout.setTitle(
            getString(R.string.sudoku) +
                when {
                    sudoku.isNormalSudoku -> " (${sudoku.difficulty.getLocalString(resources)})"
                    sudoku.isDailySudoku -> " (${sudoku.created.dateFormatShort})"
                    sudoku.isSudokuLevel -> " (${getString(R.string.level)} ${sudoku.modeLevel})"
                    else -> ""
                },
        )
    }

    @SuppressLint("StringFormatInvalid")
    private fun setSubtitle() {
        val errorLimit = userSettings.errorLimit
        val subtitle =
            getString(R.string.current_time, sudoku.timeString) + " | " +
                getString(
                    R.string.current_progress,
                    sudoku.progress,
                ) + " | " +
                when {
                    sudoku.isDailySudoku -> getString(R.string.current_errors_with_limit, sudoku.errorsMade, MODE_DAILY_ERROR_LIMIT)
                    sudoku.isSudokuLevel -> getString(R.string.current_errors_with_limit, sudoku.errorsMade, MODE_LEVEL_ERROR_LIMIT)
                    errorLimit == 0 -> getString(R.string.current_errors, sudoku.errorsMade)
                    else -> getString(R.string.current_errors_with_limit, sudoku.errorsMade, errorLimit)
                } + if (sudoku.isNormalSudoku) " | " + getString(R.string.current_hints, sudoku.hintsUsed) else ""
        binding.sudokuToolbarLayout.expandedSubtitle = subtitle
        binding.sudokuToolbarLayout.collapsedSubtitle = subtitle
    }

    private fun shareDialog() {
        pauseGame()
        val dialog =
            AlertDialog
                .Builder(this)
                .setTitle(R.string.share_sudoku)
                .setView(R.layout.dialog_share)
                .setPositiveButton(R.string.commonutils_share, null)
                .setNegativeButton(designR.string.oui_des_common_cancel, null)
                .create()
        dialog.show()
        dialog.findViewById<TextView>(R.id.shareStatistics)?.text = sudoku.getLocalStatisticsString(resources)
        dialog.getButton(BUTTON_POSITIVE).setOnClickListenerWithProgress { _, _ ->
            lifecycleScope.launch {
                when (dialog.findViewById<RadioGroup>(R.id.shareRadioGroup)?.checkedRadioButtonId) {
                    R.id.radioButtonText -> shareStats()
                    R.id.radioButtonInitial -> shareGame(sudoku.getInitialSudoku())
                    R.id.radioButtonCurrent -> shareGame(sudoku.copy(sudokuId = SudokuId.generate(), modeLevel = MODE_NORMAL))
                }
                dialog.dismiss()
            }
        }
    }

    private fun shareStats() {
        val sendIntent = Intent(ACTION_SEND)
        sendIntent.type = "text/plain"
        sendIntent.putExtra(EXTRA_TEXT, sudoku.getLocalStatisticsStringShare(resources))
        sendIntent.putExtra(EXTRA_TITLE, getString(R.string.share_sudoku))
        sendIntent.flags = FLAG_GRANT_READ_URI_PERMISSION
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_sudoku)))
    }

    private suspend fun shareGame(sudoku: Sudoku) {
        PlayGames.getAchievementsClient(this@SudokuActivity).unlock(getString(R.string.achievement_share_sudoku))
        val uri = viewModel.exportSudoku(sudoku)
        val shareIntent = Intent(ACTION_SEND)
        shareIntent.type = "application/sudoku" // octet-stream"
        shareIntent.addFlags(FLAG_GRANT_READ_URI_PERMISSION)
        shareIntent.putExtra(EXTRA_STREAM, uri)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_sudoku)))
    }

    companion object {
        const val KEY_SUDOKU_ID = "key_sudoku_id"

        // keyCode -> (minimum sudoku size to enable this key, number-button index)
        private val digitKeyButtons: Map<Int, Pair<Int, Int>> =
            mapOf(
                KeyEvent.KEYCODE_1 to (0 to 0),
                KeyEvent.KEYCODE_2 to (0 to 1),
                KeyEvent.KEYCODE_3 to (0 to 2),
                KeyEvent.KEYCODE_4 to (0 to 3),
                KeyEvent.KEYCODE_5 to (4 to 4),
                KeyEvent.KEYCODE_6 to (4 to 5),
                KeyEvent.KEYCODE_7 to (4 to 6),
                KeyEvent.KEYCODE_8 to (4 to 7),
                KeyEvent.KEYCODE_9 to (4 to 8),
                KeyEvent.KEYCODE_A to (9 to 9),
                KeyEvent.KEYCODE_B to (9 to 10),
                KeyEvent.KEYCODE_C to (9 to 11),
                KeyEvent.KEYCODE_D to (9 to 12),
                KeyEvent.KEYCODE_E to (9 to 13),
                KeyEvent.KEYCODE_F to (9 to 14),
                KeyEvent.KEYCODE_G to (9 to 15),
            )
    }

    inner class SudokuGameListener : GameListener {
        override fun onFieldClicked(position: Position) {
            select(position.index)
        }

        override fun onFieldChanged(position: Position) {
            gameAdapter.updateFieldView(position.index)
            checkAnyNumberCompleted()
            checkRowColumnBlockCompleted(position)
            lifecycleScope.launch { viewModel.saveSudokuProgress(sudoku, onlyUpdate = true) }
        }

        override fun onCompleted(position: Position) {
            if (userSettings.animationsEnabled) {
                animate(position, animateSudoku = true)?.invokeOnCompletion { onSudokuCompleted() }
            } else {
                onSudokuCompleted()
            }
        }

        override fun onError() {
            checkErrorLimit()
        }

        override fun onTimeChanged() {
            lifecycleScope.launch { setSubtitle() }
        }
    }
}
