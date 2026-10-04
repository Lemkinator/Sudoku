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
import android.net.Uri
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
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.games.PlayGames
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.prepareActivityTransformationTo
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.showInAppReviewIfPossible
import de.lemke.commonutils.ui.utils.showOnce
import de.lemke.commonutils.ui.utils.singleLaunch
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import de.lemke.commonutils.ui.utils.singleLaunchMenuItem
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

private const val GAME_BUTTONS_FADE_DURATION_MILLIS = 300L
private const val SUDOKU_COMPLETED_ANIMATION_DURATION_MILLIS = 200L
private const val FIELD_ANIMATION_FADE_ALPHA = 0.4f
private const val FIELD_ANIMATION_SCALE = 1.6f
private const val FIELD_ANIMATION_ROTATION_DEGREES = 100f

@AndroidEntryPoint
class SudokuActivity : AppCompatActivity() {
    internal lateinit var binding: ActivitySudokuBinding
    private lateinit var loadingDialog: ProgressDialog
    private var shareDialog: AlertDialog? = null
    lateinit var sudoku: Sudoku
    lateinit var gameAdapter: SudokuViewAdapter
    internal val sudokuButtons: MutableList<AppCompatButton> = mutableListOf()
    private val allNumberButtons: List<AppCompatButton>
        get() =
            with(binding) {
                listOf(
                    numberButton1,
                    numberButton2,
                    numberButton3,
                    numberButton4,
                    numberButton5,
                    numberButton6,
                    numberButton7,
                    numberButton8,
                    numberButton9,
                    numberButtonA,
                    numberButtonB,
                    numberButtonC,
                    numberButtonD,
                    numberButtonE,
                    numberButtonF,
                    numberButtonG,
                )
            }
    internal var notesEnabled = false
    internal var selected: Int? = null
    private var menuPausePlayVisible = false
    private var menuResetVisible = false
    private val accelerateDecelerateInterpolator = AccelerateDecelerateInterpolator()

    internal val colorPrimary get() = ColorStateList.valueOf(getColor(R.color.primary_color_themed))
    internal val transparent get() = ColorStateList.valueOf(getColor(android.R.color.transparent))

    @Inject
    lateinit var userSettings: UserSettings

    internal val viewModel: SudokuViewModel by viewModels()

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivitySudokuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        loadingDialog = ProgressDialog(this)
        loadingDialog.setProgressStyle(CIRCLE)
        loadingDialog.setCancelable(false)
        binding.noteButton.setOnClickListener { toggleOrSetNoteButton() }
        collectState(viewModel.game, minActiveState = CREATED) { if (it == SudokuGame.NotFound) onSudokuNotFound() }
        collectState(viewModel.game) { renderLoadingDialog(it) }
        collectState(viewModel.game, minActiveState = RESUMED) { onGame(it) }
        collectState(viewModel.share, minActiveState = RESUMED) { if (it is SudokuShare.Result) onShareResult(it) }
    }

    override fun onDestroy() {
        loadingDialog.dismiss()
        shareDialog?.dismiss()
        super.onDestroy()
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
            R.id.menu_reset -> singleLaunchMenuItem { viewModel.onRestart() }
            R.id.menu_share -> singleLaunchMenuItem { shareDialog() }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        digitKeyButtonIndex[keyCode]?.let { buttonIndex ->
            return if (buttonIndex < sudoku.size.value) select(sudoku.itemCount + buttonIndex).let { true } else false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DEL -> select(sudoku.itemCount + sudoku.size.value).let { true }
            KeyEvent.KEYCODE_H -> if (sudoku.isHintAvailable) select(sudoku.itemCount + sudoku.size.value + 1).let { true } else false
            KeyEvent.KEYCODE_N -> toggleOrSetNoteButton().let { true }
            KeyEvent.KEYCODE_ESCAPE -> select(null).let { true }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    private fun renderLoadingDialog(game: SudokuGame) {
        if (game == SudokuGame.Generating) loadingDialog.showOnce(LOADING_DIALOG_TAG) else loadingDialog.dismiss()
    }

    private fun onSudokuNotFound() {
        Log.e("SudokuActivity", "Sudoku not found")
        toast(R.string.error_sudoku_not_found)
        finishAfterTransition()
    }

    private fun onGame(game: SudokuGame) {
        when (game) {
            SudokuGame.Loading, SudokuGame.NotFound, SudokuGame.Restarting, SudokuGame.Generating -> {
                Unit
            }

            is SudokuGame.Ready -> {
                initSudoku(game.sudoku)
                viewModel.onGameStarted(game)
            }

            is SudokuGame.Playing -> {
                if (!this::sudoku.isInitialized) initSudoku(game.sudoku)
            }
        }
    }

    private fun initSudoku(sudoku: Sudoku) {
        this.sudoku = sudoku
        setTitle()
        setSubtitle()
        binding.gameRecycler.layoutManager = GridLayoutManager(this@SudokuActivity, sudoku.size.value)
        gameAdapter = SudokuViewAdapter(this@SudokuActivity, sudoku)
        binding.gameRecycler.adapter = gameAdapter
        sudoku.gameListener = SudokuGameListener()
        initSudokuButtons()
        resumeGame()
    }

    private fun initSudokuButtons() {
        sudokuButtons.clear()
        sudokuButtons.addAll(allNumberButtons.take(sudoku.size.value))
        for (index in sudokuButtons.indices) {
            sudokuButtons[index].isVisible = true
            sudokuButtons[index].setOnClickListener { select(sudoku.itemCount + index) }
        }
        binding.deleteButton.setOnClickListener { select(sudoku.itemCount + sudoku.size.value) }
        binding.hintButton.setOnClickListener { select(sudoku.itemCount + sudoku.size.value + 1) }
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
        lifecycleScope.launch { viewModel.saveSudokuProgress(sudoku) }
    }

    private fun animateGameButtonsVisibility(visible: Boolean) {
        val value = if (visible) 1f else 0f
        binding.gameButtons
            .animate()
            .setInterpolator(accelerateDecelerateInterpolator)
            .alpha(value)
            .scaleX(value)
            .scaleY(value)
            .setDuration(GAME_BUTTONS_FADE_DURATION_MILLIS)
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
            viewModel.saveSudokuProgress(sudoku)
            if (sudoku.isSudokuLevel &&
                viewModel.isMaxSudokuLevel(sudoku.size, sudoku.modeLevel)
            ) {
                dialog.setPositiveButton(R.string.next_level) { _, _ -> singleLaunch { viewModel.onFollowUp(FollowUp.NEXT_LEVEL) } }
            } else if (sudoku.isNormalSudoku) {
                dialog.setPositiveButton(R.string.new_game) { _, _ -> singleLaunch { viewModel.onFollowUp(FollowUp.NEW_GAME) } }
            }
            dialog.showOnce(COMPLETED_DIALOG_TAG)
            applyPlayGamesSync(viewModel.syncPlayGames(sudoku))
            showInAppReviewIfPossible(userSettings)
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
                .setPositiveButton(R.string.restart) { _, _ -> singleLaunch { viewModel.onRestart() } }
                .setNeutralButton(commonutilsR.string.commonutils_ok, null)
                .showOnce(GAME_OVER_DIALOG_TAG)
            return true
        }
        return false
    }

    internal fun select(newSelected: Int?) {
        if (checkErrorLimit()) return
        if (binding.sudokuToolbarLayout.isExpanded) binding.sudokuToolbarLayout.setExpanded(expanded = false, animate = true)
        when (selected) {
            // nothing is selected
            null -> selectFromNothing(newSelected)

            // field is selected
            in 0 until sudoku.itemCount -> selectFromField(newSelected)

            // number button is selected
            in sudoku.itemCount until sudoku.itemCount + sudoku.size.value -> selectFromNumberButton(newSelected)

            // delete button is selected
            sudoku.itemCount + sudoku.size.value -> selectFromDeleteButton(newSelected)

            // hint button is selected
            sudoku.itemCount + sudoku.size.value + 1 -> selectFromHintButton(newSelected)
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
            in sudoku.itemCount until sudoku.itemCount + sudoku.size.value + 2 -> {
                selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
            }

            // selected nothing
            else -> {}
        }
    }

    private fun selectFromField(newSelected: Int?) {
        val selectedField = selected!!
        val position = Position.create(selectedField, sudoku.size)
        when (newSelected) {
            // selected nothing / selected same field
            null, selectedField -> {
                selected = null
            }

            // selected field
            in 0 until sudoku.itemCount -> {
                selected = newSelected
            }

            // selected number
            in sudoku.itemCount until sudoku.itemCount + sudoku.size.value -> {
                sudoku.move(position, newSelected - sudoku.itemCount + 1, notesEnabled)
                selected = null
            }

            // selected delete
            sudoku.itemCount + sudoku.size.value -> {
                sudoku.move(position, null, notesEnabled)
                selected = null
            }

            // selected hint
            sudoku.itemCount + sudoku.size.value + 1 -> {
                sudoku.setHint(position)
                selected = null
                refreshHintButton()
            }
        }
        gameAdapter.selectFieldView(selected, userSettings.highlightRegional, userSettings.highlightNumber)
    }

    private fun selectFromNumberButton(newSelected: Int?) {
        val selectedButton = selected!!
        when (newSelected) {
            // selected nothing / selected same button
            null, selectedButton -> {
                selectButton(null, userSettings.highlightNumber)
            }

            // selected field
            in 0 until sudoku.itemCount -> {
                sudoku.move(newSelected, selectedButton - sudoku.itemCount + 1, notesEnabled)
                highlightCurrentNumber(selectedButton - sudoku.itemCount + 1)
            }

            // selected button
            in sudoku.itemCount until sudoku.itemCount + sudoku.size.value + 2 -> {
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
        val selectedButton = selected!!
        when (newSelected) {
            // selected nothing / selected same button
            null, selectedButton -> {
                selectButton(null, userSettings.highlightNumber)
            }

            // selected field
            in 0 until sudoku.itemCount -> {
                sudoku.move(newSelected, null, notesEnabled)
            }

            // selected button(not delete)
            in sudoku.itemCount until sudoku.itemCount + sudoku.size.value + 2 -> {
                selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
            }

            // selected nothing
            else -> {
                selectButton(null, userSettings.highlightNumber)
            }
        }
    }

    private fun selectFromHintButton(newSelected: Int?) {
        val selectedButton = selected!!
        when (newSelected) {
            // selected nothing / selected same button
            null, selectedButton -> {
                selectButton(null, userSettings.highlightNumber)
            }

            // selected field
            in 0 until sudoku.itemCount -> {
                sudoku.setHint(newSelected)
                if (!sudoku.isHintAvailable) selected = null
                refreshHintButton()
            }

            // selected button(not hint)
            in sudoku.itemCount until sudoku.itemCount + sudoku.size.value + 1 -> {
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
        val delay = 60L / sudoku.size.blockSize
        lifecycleScope.launch {
            gameAdapter.fieldViews
                .filter { matchesAnimation(it, position, animateRow, animateColumn, animateBlock, animateSudoku) { a, b -> a <= b } }
                .reversed()
                .forEach {
                    if (animateSudoku) {
                        animateField(it.fieldViewValue, SUDOKU_COMPLETED_ANIMATION_DURATION_MILLIS, delay)
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
                        animateField(it.fieldViewValue, SUDOKU_COMPLETED_ANIMATION_DURATION_MILLIS, delay)
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
        delay((delay / sudoku.size.blockSize).milliseconds)
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
                sudoku.size.value -> {
                    binding.deleteButton.backgroundTintList = colorPrimary
                }

                sudoku.size.value + 1 -> {
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
        selectNextButton(currentNumber, sudoku.getCompletedNumbers())
    }

    private fun toggleOrSetNoteButton() {
        notesEnabled = !notesEnabled
        binding.noteButton.backgroundTintList = if (notesEnabled) colorPrimary else transparent
    }

    private fun refreshHintButton() {
        binding.hintButton.isVisible = sudoku.isHintAvailable
        binding.hintButton.text = getString(R.string.hint, sudoku.availableHints)
    }

    private fun setTitle() {
        val detail =
            when {
                sudoku.isNormalSudoku -> sudoku.difficulty.getLocalString(resources)
                sudoku.isDailySudoku -> sudoku.created.dateFormatShort
                sudoku.isSudokuLevel -> getString(R.string.level_number, sudoku.modeLevel)
                else -> null
            }
        val sudokuName = getString(R.string.sudoku)
        binding.sudokuToolbarLayout.setTitle(
            if (detail == null) sudokuName else getString(R.string.sudoku_title_detail, sudokuName, detail),
        )
    }

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
        val dialog =
            AlertDialog
                .Builder(this)
                .setTitle(R.string.share_sudoku)
                .setView(R.layout.dialog_share)
                .setPositiveButton(R.string.commonutils_share, null)
                .setNegativeButton(designR.string.oui_des_common_cancel, null)
                .showOnce(SHARE_DIALOG_TAG) ?: return
        shareDialog = dialog
        pauseGame()
        dialog.findViewById<TextView>(R.id.shareStatistics)?.text = sudoku.getLocalStatisticsString(resources)
        dialog.getButton(BUTTON_POSITIVE).setOnClickListenerWithProgress { _, _ ->
            val content = dialog.findViewById<RadioGroup>(R.id.shareRadioGroup)?.checkedRadioButtonId?.let(ShareContent::fromRadioButtonId)
            singleLaunch { share(content) }
        }
    }

    private fun share(content: ShareContent?) {
        when (content) {
            null -> {
                shareDialog?.dismiss()
            }

            ShareContent.STATISTICS -> {
                singleLaunchActivity(Intent.createChooser(statisticsShareIntent(), getString(R.string.share_sudoku)))
                shareDialog?.dismiss()
            }

            ShareContent.INITIAL_GAME -> {
                viewModel.onShare(sudoku.getInitialSudoku())
            }

            ShareContent.CURRENT_GAME -> {
                viewModel.onShare(sudoku.copy(sudokuId = SudokuId.generate(), modeLevel = MODE_NORMAL))
            }
        }
    }

    private fun onShareResult(result: SudokuShare.Result) {
        val handled =
            when (result) {
                is SudokuShare.File -> {
                    PlayGames.getAchievementsClient(this).unlock(getString(R.string.achievement_share_sudoku))
                    singleLaunchActivity(Intent.createChooser(gameShareIntent(result.uri), getString(R.string.share_sudoku)))
                }
            }
        shareDialog?.dismiss()
        if (handled) viewModel.onShareHandled(result)
    }

    private fun statisticsShareIntent(): Intent =
        Intent(ACTION_SEND)
            .setType("text/plain")
            .putExtra(EXTRA_TEXT, sudoku.getLocalStatisticsStringShare(resources))
            .putExtra(EXTRA_TITLE, getString(R.string.share_sudoku))
            .setFlags(FLAG_GRANT_READ_URI_PERMISSION)

    private fun gameShareIntent(uri: Uri): Intent =
        Intent(ACTION_SEND)
            .setType("application/sudoku")
            .addFlags(FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(EXTRA_STREAM, uri)

    companion object {
        const val KEY_SUDOKU_ID = "key_sudoku_id"
        private const val LOADING_DIALOG_TAG = "loading"
        private const val COMPLETED_DIALOG_TAG = "completed"
        private const val GAME_OVER_DIALOG_TAG = "gameOver"
        private const val SHARE_DIALOG_TAG = "share"

        private val digitKeyButtonIndex: Map<Int, Int> =
            listOf(
                KeyEvent.KEYCODE_1,
                KeyEvent.KEYCODE_2,
                KeyEvent.KEYCODE_3,
                KeyEvent.KEYCODE_4,
                KeyEvent.KEYCODE_5,
                KeyEvent.KEYCODE_6,
                KeyEvent.KEYCODE_7,
                KeyEvent.KEYCODE_8,
                KeyEvent.KEYCODE_9,
                KeyEvent.KEYCODE_A,
                KeyEvent.KEYCODE_B,
                KeyEvent.KEYCODE_C,
                KeyEvent.KEYCODE_D,
                KeyEvent.KEYCODE_E,
                KeyEvent.KEYCODE_F,
                KeyEvent.KEYCODE_G,
            ).withIndex().associate { (index, keyCode) -> keyCode to index }
    }

    inner class SudokuGameListener : GameListener {
        override fun onFieldClicked(position: Position) {
            select(position.index)
        }

        override fun onFieldChanged(position: Position) {
            gameAdapter.updateFieldView(position.index)
            checkAnyNumberCompleted()
            checkRowColumnBlockCompleted(position)
            lifecycleScope.launch { viewModel.saveSudokuProgress(sudoku) }
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

/** What the share dialog shares, one per radio button. */
private enum class ShareContent(
    @param:IdRes val radioButtonId: Int,
) {
    STATISTICS(R.id.radioButtonText),
    INITIAL_GAME(R.id.radioButtonInitial),
    CURRENT_GAME(R.id.radioButtonCurrent),
    ;

    companion object {
        fun fromRadioButtonId(
            @IdRes id: Int,
        ): ShareContent? = entries.firstOrNull { it.radioButtonId == id }
    }
}
