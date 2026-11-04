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
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.DialogCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.games.PlayGames
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.InAppReview
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.prepareActivityTransformationTo
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.share
import de.lemke.commonutils.ui.utils.shareText
import de.lemke.commonutils.ui.utils.showOnce
import de.lemke.commonutils.ui.utils.singleLaunch
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import de.lemke.commonutils.ui.utils.singleLaunchMenuItem
import de.lemke.commonutils.ui.utils.toast
import de.lemke.commonutils.ui.utils.transformTo
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.databinding.ActivitySudokuBinding
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.BoardMove
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.MoveResult
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.dateFormatShort
import de.lemke.sudoku.ui.utils.FieldRegion
import de.lemke.sudoku.ui.utils.PlayGamesSyncApplier
import de.lemke.sudoku.ui.utils.SelectionIndices
import de.lemke.sudoku.ui.utils.SelectionTarget
import de.lemke.sudoku.ui.utils.SudokuViewAdapter
import de.lemke.sudoku.ui.utils.animateFields
import de.lemke.sudoku.ui.utils.fieldRegions
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.dialog.ProgressDialog.ProgressStyle.CIRCLE
import dev.oneuiproject.oneui.ktx.setOnClickListenerWithProgress
import javax.inject.Inject
import de.lemke.commonutils.R as commonutilsR
import dev.oneuiproject.oneui.R as oneuiR
import dev.oneuiproject.oneui.design.R as designR

private const val GAME_BUTTONS_FADE_DURATION_MILLIS = 300L

@AndroidEntryPoint
class SudokuActivity : AppCompatActivity() {
    internal lateinit var binding: ActivitySudokuBinding
    private lateinit var loadingDialog: ProgressDialog
    private var shareDialog: AlertDialog? = null
    private var completionDialog: AlertDialog? = null
    private var gameOverDialog: AlertDialog? = null
    private val playing get() = viewModel.game.value as? SudokuGame.Playing
    private val SudokuGame.Playing.selectionIndices get() = SelectionIndices(sudoku.info.size)
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
    private val gameEnded get() = playing?.ended == true
    private var renderedMenuState: Pair<Boolean, Phase>? = null
    private val accelerateDecelerateInterpolator = AccelerateDecelerateInterpolator()

    internal val colorPrimary get() = ColorStateList.valueOf(getColor(R.color.primary_color_themed))
    internal val transparent get() = ColorStateList.valueOf(getColor(android.R.color.transparent))

    @Inject
    lateinit var userSettings: UserSettings

    @Inject
    lateinit var playGamesSyncApplier: PlayGamesSyncApplier

    @Inject
    lateinit var inAppReview: InAppReview

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
        collectState(viewModel.completion) { invalidateOptionsMenu() }
        collectState(viewModel.completion, minActiveState = RESUMED) { if (it is SudokuCompletion.Result) onCompletionResult(it) }
        collectState(viewModel.playGamesSync, minActiveState = RESUMED) { it?.let(::onPlayGamesSync) }
    }

    override fun onDestroy() {
        loadingDialog.dismiss()
        shareDialog?.dismiss()
        completionDialog?.dismiss()
        gameOverDialog?.dismiss()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        if (this::gameAdapter.isInitialized) pauseGame()
    }

    override fun onCreateOptionsMenu(menu: Menu?) = menuInflater.inflate(R.menu.sudoku_menu, menu).let { true }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val playing = playing
        val running = playing?.running == true
        menu?.findItem(R.id.menu_pause_play)?.let {
            it.icon =
                AppCompatResources.getDrawable(
                    this,
                    if (running) {
                        oneuiR.drawable.ic_oui_control_pause
                    } else {
                        oneuiR.drawable.ic_oui_control_play
                    },
                )
            it.title = getString(if (running) R.string.commonutils_pause else R.string.commonutils_resume)
            it.isVisible = playing?.ended == false
        }
        menu?.findItem(R.id.menu_reset)?.isVisible = playing?.canRestart == true && viewModel.completion.value == SudokuCompletion.Idle
        menu?.findItem(R.id.menu_share)?.isVisible = playing != null
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.menu_pause_play -> withPlaying { if (it.running) pauseGame() else resumeGame() }.let { true }
            R.id.menu_reset -> singleLaunchMenuItem { viewModel.onRestart() }
            R.id.menu_share -> singleLaunchMenuItem { withPlaying(::shareDialog) }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = playing?.let { onGameKey(keyCode, it) } == true || super.onKeyUp(keyCode, event)

    private fun withPlaying(action: (SudokuGame.Playing) -> Unit) {
        playing?.let(action)
    }

    private fun onGameKey(
        keyCode: Int,
        playing: SudokuGame.Playing,
    ): Boolean =
        when (keyCode) {
            in digitKeyButtonIndex -> {
                val buttonIndex = digitKeyButtonIndex.getValue(keyCode)
                (buttonIndex < playing.sudoku.info.size.value).also {
                    if (it) select(playing, playing.selectionIndices.numberButton(buttonIndex))
                }
            }

            KeyEvent.KEYCODE_DEL -> {
                true.also { select(playing, playing.selectionIndices.deleteButton) }
            }

            KeyEvent.KEYCODE_H -> {
                playing.sudoku.info.isHintAvailable
                    .also { if (it) select(playing, playing.selectionIndices.hintButton) }
            }

            KeyEvent.KEYCODE_N -> {
                true.also { toggleOrSetNoteButton() }
            }

            KeyEvent.KEYCODE_ESCAPE -> {
                true.also { select(playing, null) }
            }

            else -> {
                false
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
                renderedMenuState = null
                invalidateOptionsMenu()
            }

            is SudokuGame.Ready -> {
                viewModel.onGameStarted(game)
                initSudoku(game.sudoku)
            }

            is SudokuGame.Playing -> {
                if (!this::gameAdapter.isInitialized) initSudoku(game.sudoku)
                setSubtitle(game)
                if (renderedMenuState != game.running to game.phase) {
                    renderedMenuState = game.running to game.phase
                    invalidateOptionsMenu()
                }
                game.lastMove?.let { showMove(game, it) }
            }
        }
    }

    private fun initSudoku(sudoku: Sudoku) {
        setTitle(sudoku.info)
        binding.gameRecycler.layoutManager = GridLayoutManager(this@SudokuActivity, sudoku.info.size.value)
        gameAdapter =
            SudokuViewAdapter(
                this@SudokuActivity,
                sudoku.board,
                onFieldClicked = { select(it) },
                onFieldLongClicked = { viewModel.onMove(BoardMove.Erase(it)) != null },
                onHighlightingUsed = viewModel::onHighlightingUsed,
            )
        binding.gameRecycler.adapter = gameAdapter
        initSudokuButtons(sudoku)
        resumeGame()
    }

    private fun initSudokuButtons(sudoku: Sudoku) {
        sudokuButtons.clear()
        sudokuButtons.addAll(allNumberButtons.take(sudoku.info.size.value))
        for (index in sudokuButtons.indices) {
            sudokuButtons[index].isVisible = true
            sudokuButtons[index].setOnClickListener { selectControl { numberButton(index) } }
        }
        binding.deleteButton.setOnClickListener { selectControl { deleteButton } }
        binding.hintButton.setOnClickListener { selectControl { hintButton } }
        binding.resumeButton.setOnClickListener { resumeGame() }
        selectButton(null, false, SelectionIndices(sudoku.info.size))
        checkAnyNumberCompleted(sudoku.board)
        refreshHintButton(sudoku.info)
    }

    fun resumeGame() {
        if (!this::gameAdapter.isInitialized) return
        binding.resumeButton.transformTo(binding.gameLayout)
        viewModel.onResumed()
        animateGameButtonsVisibility(!gameEnded)
        invalidateOptionsMenu()
        updateKeepScreenOn()
    }

    private fun updateKeepScreenOn() {
        if (!userSettings.keepScreenOn) return
        if (gameEnded) window.clearFlags(FLAG_KEEP_SCREEN_ON) else window.addFlags(FLAG_KEEP_SCREEN_ON)
    }

    private fun pauseGame() {
        viewModel.onPaused()
        if (userSettings.keepScreenOn) window.clearFlags(FLAG_KEEP_SCREEN_ON)
        if (gameEnded) return
        if (binding.gameLayout.isVisible) binding.gameLayout.transformTo(binding.resumeButton)
        animateGameButtonsVisibility(false)
        invalidateOptionsMenu()
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
        animateGameButtonsVisibility(false)
        viewModel.onCompleted()
        invalidateOptionsMenu()
        updateKeepScreenOn()
    }

    private fun onCompletionResult(result: SudokuCompletion.Result) {
        when (result) {
            is SudokuCompletion.Summary -> {
                showCompletionSummary(result)
            }

            is SudokuCompletion.Failed -> {
                toast(R.string.error_saving_sudoku_failed)
                viewModel.onCompletionHandled(result)
            }
        }
    }

    private fun showCompletionSummary(summary: SudokuCompletion.Summary) {
        val dialog =
            AlertDialog
                .Builder(this@SudokuActivity)
                .setTitle(R.string.completed_title)
                .setMessage(summary.sudoku.getLocalStatisticsString(resources))
                .setNeutralButton(commonutilsR.string.commonutils_ok) { _, _ -> viewModel.onCompletionDismissed(summary) }
                .setOnCancelListener { viewModel.onCompletionDismissed(summary) }
        summary.followUp?.let { followUp ->
            dialog.setPositiveButton(followUp.buttonText) { _, _ ->
                viewModel.onCompletionDismissed(summary)
                singleLaunch { viewModel.onFollowUp(followUp) }
            }
        }
        dialog.showOnce(COMPLETED_DIALOG_TAG)?.let { completionDialog = it }
    }

    private fun onPlayGamesSync(sync: PlayGamesSync) {
        playGamesSyncApplier.apply(this, sync)
        inAppReview.showIfPossible(this)
        viewModel.onPlayGamesSyncHandled(sync)
    }

    private fun onErrorLimitReached(playing: SudokuGame.Playing) {
        animateGameButtonsVisibility(false)
        invalidateOptionsMenu()
        updateKeepScreenOn()
        showGameOver(playing)
    }

    private fun showGameOver(playing: SudokuGame.Playing) {
        AlertDialog
            .Builder(this@SudokuActivity)
            .setTitle(R.string.gameover)
            .setMessage(getString(R.string.error_limit_reached, playing.errorLimit))
            .setPositiveButton(R.string.restart) { _, _ -> singleLaunch { viewModel.onRestart() } }
            .setNeutralButton(commonutilsR.string.commonutils_ok, null)
            .showOnce(GAME_OVER_DIALOG_TAG)
            ?.let { gameOverDialog = it }
    }

    internal fun select(newSelected: Int?) = withPlaying { select(it, newSelected) }

    private fun selectControl(control: SelectionIndices.() -> Int) = withPlaying { select(it, it.selectionIndices.control()) }

    private fun select(
        playing: SudokuGame.Playing,
        newSelected: Int?,
    ) {
        if (playing.phase == Phase.Lost) return
        if (binding.sudokuToolbarLayout.isExpanded) binding.sudokuToolbarLayout.setExpanded(expanded = false, animate = true)
        val current = selected
        if (current == null) {
            selectFromNothing(playing, newSelected)
            return
        }
        when (playing.selectionIndices.targetOf(current)) {
            SelectionTarget.FIELD -> selectFromField(playing, newSelected)
            SelectionTarget.NUMBER_BUTTON -> selectFromNumberButton(playing, newSelected)
            SelectionTarget.DELETE_BUTTON -> selectFromDeleteButton(playing, newSelected)
            SelectionTarget.HINT_BUTTON -> selectFromHintButton(playing, newSelected)
        }
    }

    private fun selectFromNothing(
        playing: SudokuGame.Playing,
        newSelected: Int?,
    ) {
        val indices = playing.selectionIndices
        when (newSelected) {
            null -> {}

            in indices.fields -> {
                gameAdapter.selectFieldView(newSelected, userSettings.highlightRegional, userSettings.highlightNumber)
                selected = newSelected
            }

            in indices.buttons -> {
                selectButton(newSelected - indices.size.cellCount, userSettings.highlightNumber, indices)
            }

            else -> {}
        }
    }

    private fun selectFromField(
        playing: SudokuGame.Playing,
        newSelected: Int?,
    ) {
        val indices = playing.selectionIndices
        val selectedField = selected!!
        when (newSelected) {
            null, selectedField -> {
                selected = null
            }

            in indices.fields -> {
                selected = newSelected
            }

            in indices.numberButtons -> {
                enterOrToggleNote(playing.sudoku, selectedField, newSelected - indices.size.cellCount + 1)
                selected = null
            }

            indices.deleteButton -> {
                eraseOrClearNotes(playing.sudoku, selectedField)
                selected = null
            }

            indices.hintButton -> {
                move(playing.sudoku, BoardMove.RevealHint(selectedField))
                selected = null
            }
        }
        gameAdapter.selectFieldView(selected, userSettings.highlightRegional, userSettings.highlightNumber)
    }

    private fun selectFromNumberButton(
        playing: SudokuGame.Playing,
        newSelected: Int?,
    ) {
        val indices = playing.selectionIndices
        val selectedButton = selected!!
        when (newSelected) {
            null, selectedButton -> {
                selectButton(null, userSettings.highlightNumber, indices)
            }

            in indices.fields -> {
                val number = selectedButton - indices.size.cellCount + 1
                val moved = enterOrToggleNote(playing.sudoku, newSelected, number)
                selectNextButton(number, moved.board.completedDigits, indices)
            }

            in indices.buttons -> {
                gameAdapter.selectFieldView(null, userSettings.highlightRegional, userSettings.highlightNumber)
                selectButton(newSelected - indices.size.cellCount, userSettings.highlightNumber, indices)
            }

            else -> {
                selectButton(null, userSettings.highlightNumber, indices)
            }
        }
    }

    private fun selectFromDeleteButton(
        playing: SudokuGame.Playing,
        newSelected: Int?,
    ) {
        val indices = playing.selectionIndices
        val selectedButton = selected!!
        when (newSelected) {
            null, selectedButton -> {
                selectButton(null, userSettings.highlightNumber, indices)
            }

            in indices.fields -> {
                eraseOrClearNotes(playing.sudoku, newSelected)
            }

            in indices.buttons -> {
                selectButton(newSelected - indices.size.cellCount, userSettings.highlightNumber, indices)
            }

            else -> {
                selectButton(null, userSettings.highlightNumber, indices)
            }
        }
    }

    private fun selectFromHintButton(
        playing: SudokuGame.Playing,
        newSelected: Int?,
    ) {
        val indices = playing.selectionIndices
        val selectedButton = selected!!
        when (newSelected) {
            null, selectedButton -> {
                selectButton(null, userSettings.highlightNumber, indices)
            }

            in indices.fields -> {
                val moved = move(playing.sudoku, BoardMove.RevealHint(newSelected))
                if (!moved.info.isHintAvailable) selected = null
            }

            in indices.buttons -> {
                selectButton(newSelected - indices.size.cellCount, userSettings.highlightNumber, indices)
            }

            else -> {
                selectButton(null, userSettings.highlightNumber, indices)
            }
        }
    }

    private fun enterOrToggleNote(
        sudoku: Sudoku,
        index: Int,
        digit: Int,
    ) = move(sudoku, if (notesEnabled) BoardMove.ToggleNote(index, digit) else BoardMove.Enter(index, digit))

    private fun eraseOrClearNotes(
        sudoku: Sudoku,
        index: Int,
    ) = move(sudoku, if (notesEnabled) BoardMove.ClearNotes(index) else BoardMove.Erase(index))

    private fun move(
        sudoku: Sudoku,
        move: BoardMove,
    ): Sudoku = viewModel.onMove(move) ?: sudoku

    private fun showMove(
        playing: SudokuGame.Playing,
        result: MoveResult,
    ) {
        viewModel.onMoveShown(result)
        gameAdapter.render(playing.sudoku.board)
        checkAnyNumberCompleted(playing.sudoku.board)
        refreshHintButton(playing.sudoku.info)
        val position = result.board.position(result.move.index)
        if (userSettings.animationsEnabled) {
            lifecycleScope.animateFields(gameAdapter.fieldViews, position, result.completed.fieldRegions())
        }
        if (result.completed.board) {
            if (userSettings.animationsEnabled) {
                lifecycleScope
                    .animateFields(gameAdapter.fieldViews, position, setOf(FieldRegion.BOARD))
                    .invokeOnCompletion { onSudokuCompleted() }
            } else {
                onSudokuCompleted()
            }
        }
        if (playing.phase == Phase.Lost) onErrorLimitReached(playing)
    }

    private fun selectButton(
        i: Int?,
        highlightSelectedNumber: Boolean,
        indices: SelectionIndices,
    ) {
        for (button in sudokuButtons) button.backgroundTintList = transparent
        binding.deleteButton.backgroundTintList = transparent
        binding.hintButton.backgroundTintList = transparent
        if (i != null) {
            when (i) {
                indices.size.value -> {
                    binding.deleteButton.backgroundTintList = colorPrimary
                }

                indices.size.value + 1 -> {
                    binding.hintButton.backgroundTintList = colorPrimary
                }

                else -> {
                    sudokuButtons[i].backgroundTintList = colorPrimary
                    if (highlightSelectedNumber) gameAdapter.highlightNumber(i + 1)
                }
            }
            selected = indices.numberButton(i)
        } else {
            selected = null
            if (highlightSelectedNumber) gameAdapter.highlightNumber(null)
        }
    }

    private fun selectNextButton(
        currentNumber: Int,
        completedDigits: Set<Int>,
        indices: SelectionIndices,
    ) {
        var number = currentNumber
        while (number in completedDigits) {
            number++
            if (number > indices.size.value) number = 1
            if (number == currentNumber) {
                selectButton(null, userSettings.highlightNumber, indices)
                return
            }
        }
        selectButton(number - 1, userSettings.highlightNumber, indices)
    }

    private fun checkAnyNumberCompleted(board: Board) {
        val completedDigits = board.completedDigits
        sudokuButtons.forEachIndexed { index, button ->
            if (index + 1 in completedDigits) {
                button.isEnabled = false
                button.setTextColor(getColor(commonutilsR.color.commonutils_secondary_text_icon_color))
            } else {
                button.isEnabled = true
                button.setTextColor(getColor(commonutilsR.color.commonutils_primary_text_icon_color))
            }
        }
    }

    private fun toggleOrSetNoteButton() {
        notesEnabled = !notesEnabled
        binding.noteButton.backgroundTintList = if (notesEnabled) colorPrimary else transparent
    }

    private fun refreshHintButton(info: GameInfo) {
        binding.hintButton.isVisible = info.isHintAvailable
        binding.hintButton.text = getString(R.string.hint, info.availableHints)
    }

    private fun setTitle(info: GameInfo) {
        val detail =
            when (val mode = info.mode) {
                GameMode.Normal -> info.difficulty.getLocalString(resources)
                GameMode.Daily -> info.created.dateFormatShort
                is GameMode.Level -> getString(R.string.level_number, mode.number)
            }
        binding.sudokuToolbarLayout.setTitle(getString(R.string.sudoku_title_detail, getString(R.string.sudoku), detail))
    }

    private fun setSubtitle(playing: SudokuGame.Playing) {
        val errorLimit = playing.errorLimit
        val info = playing.sudoku.info
        val subtitle =
            getString(R.string.current_time, info.timeString) + " | " +
                getString(
                    R.string.current_progress,
                    playing.sudoku.progress,
                ) + " | " +
                when {
                    errorLimit == 0 -> getString(R.string.current_errors, info.errorsMade)
                    else -> getString(R.string.current_errors_with_limit, info.errorsMade, errorLimit)
                } + if (info.mode == GameMode.Normal) " | " + getString(R.string.current_hints, info.hintsUsed) else ""
        binding.sudokuToolbarLayout.expandedSubtitle = subtitle
        binding.sudokuToolbarLayout.collapsedSubtitle = subtitle
    }

    private fun shareDialog(playing: SudokuGame.Playing) {
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
        (DialogCompat.requireViewById(dialog, R.id.shareStatistics) as TextView).text = playing.sudoku.getLocalStatisticsString(resources)
        dialog.getButton(BUTTON_POSITIVE).setOnClickListenerWithProgress { _, _ ->
            val radioGroup = DialogCompat.requireViewById(dialog, R.id.shareRadioGroup) as RadioGroup
            val content = ShareContent.fromRadioButtonId(radioGroup.checkedRadioButtonId)
            singleLaunch { withPlaying { share(content, it.sudoku) } }
        }
    }

    private fun share(
        content: ShareContent,
        sudoku: Sudoku,
    ) {
        when (content) {
            ShareContent.STATISTICS -> {
                shareText(sudoku.getLocalStatisticsStringShare(resources), getString(R.string.share_sudoku))
                shareDialog?.dismiss()
            }

            ShareContent.INITIAL_GAME -> {
                viewModel.onShare(sudoku.initialCopy())
            }

            ShareContent.CURRENT_GAME -> {
                viewModel.onShare(sudoku.copyAsNormal())
            }
        }
    }

    private fun onShareResult(result: SudokuShare.Result) {
        val handled =
            when (result) {
                is SudokuShare.File -> {
                    PlayGames.getAchievementsClient(this).unlock(getString(R.string.achievement_share_sudoku))
                    result.file.share(this, MIME_TYPE_SUDOKU, getString(R.string.share_sudoku)).isSettled
                }
            }
        shareDialog?.dismiss()
        if (handled) viewModel.onShareHandled(result)
    }

    companion object {
        const val KEY_SUDOKU_ID = "key_sudoku_id"
        private const val LOADING_DIALOG_TAG = "loading"
        private const val COMPLETED_DIALOG_TAG = "completed"
        private const val GAME_OVER_DIALOG_TAG = "gameOver"
        private const val SHARE_DIALOG_TAG = "share"
        private const val MIME_TYPE_SUDOKU = "application/sudoku"

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
}

@get:StringRes
private val FollowUp.buttonText: Int
    get() =
        when (this) {
            FollowUp.NEW_GAME -> R.string.new_game
            FollowUp.NEXT_LEVEL -> R.string.next_level
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
        ): ShareContent = entries.first { it.radioButtonId == id }
    }
}
