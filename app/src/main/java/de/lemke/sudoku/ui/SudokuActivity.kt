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
import de.lemke.sudoku.domain.model.BoardMove
import de.lemke.sudoku.domain.model.CompletedRegions
import de.lemke.sudoku.domain.model.GameInfo
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.MoveResult
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.dateFormatShort
import de.lemke.sudoku.ui.board.CellAnimation
import de.lemke.sudoku.ui.board.animationSteps
import de.lemke.sudoku.ui.game.BoardInput
import de.lemke.sudoku.ui.game.InputTap
import de.lemke.sudoku.ui.game.Selection
import de.lemke.sudoku.ui.game.boardRenderOf
import de.lemke.sudoku.ui.utils.PlayGamesSyncer
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.dialog.ProgressDialog.ProgressStyle.CIRCLE
import dev.oneuiproject.oneui.ktx.setOnClickListenerWithProgress
import javax.inject.Inject
import kotlinx.coroutines.launch
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
    private var winAnimating = false
    private var gameOverDialog: AlertDialog? = null
    private val playing get() = viewModel.game.value as? SudokuGame.Playing
    private var initialized = false
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
    private val gameEnded get() = playing?.ended == true
    private var renderedMenuState: Pair<Boolean, Phase>? = null
    private val accelerateDecelerateInterpolator = AccelerateDecelerateInterpolator()

    internal val colorPrimary get() = ColorStateList.valueOf(getColor(R.color.primary_color_themed))
    internal val transparent get() = ColorStateList.valueOf(getColor(android.R.color.transparent))

    @Inject
    lateinit var userSettings: UserSettings

    @Inject
    lateinit var playGamesSyncer: PlayGamesSyncer

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
        binding.noteButton.setOnClickListener { viewModel.onNotesToggled() }
        binding.board.onCellClick = { viewModel.onTap(InputTap.Cell(it)) }
        binding.board.onCellLongClick = { viewModel.onMove(BoardMove.Erase(it)) }
        collectState(viewModel.game, minActiveState = CREATED) { if (it == SudokuGame.NotFound) onSudokuNotFound() }
        collectState(viewModel.game) { renderLoadingDialog(it) }
        collectState(viewModel.game, minActiveState = RESUMED) { onGame(it) }
        collectState(viewModel.input) { renderGame() }
        collectState(viewModel.share, minActiveState = RESUMED) { if (it is SudokuShare.Result) onShareResult(it) }
        collectState(viewModel.completion) { invalidateOptionsMenu() }
        collectState(viewModel.completion, minActiveState = RESUMED) {
            if (it is SudokuCompletion.Result &&
                !winAnimating
            ) {
                onCompletionResult(it)
            }
        }
        collectState(viewModel.playGamesSync, minActiveState = RESUMED) { it?.let(::onPlayGamesSync) }
        if (viewModel.completion.value != SudokuCompletion.Idle) showWon()
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
        if (initialized && !isChangingConfigurations) pauseGame()
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
            in digitKeyDigit -> {
                val digit = digitKeyDigit.getValue(keyCode)
                (digit <= playing.sudoku.info.size.value).also { if (it) viewModel.onTap(InputTap.Digit(digit)) }
            }

            KeyEvent.KEYCODE_DEL -> {
                true.also { viewModel.onTap(InputTap.Delete) }
            }

            KeyEvent.KEYCODE_H -> {
                playing.sudoku.info.isHintAvailable
                    .also { if (it) viewModel.onTap(InputTap.Hint) }
            }

            KeyEvent.KEYCODE_N -> {
                true.also { viewModel.onNotesToggled() }
            }

            KeyEvent.KEYCODE_ESCAPE -> {
                true.also { viewModel.onTap(InputTap.Clear) }
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
                if (!initialized) initSudoku(game.sudoku, paused = !game.running && !game.ended)
                setSubtitle(game)
                renderGame()
                if (renderedMenuState != game.running to game.phase) {
                    renderedMenuState = game.running to game.phase
                    invalidateOptionsMenu()
                }
                game.lastMove?.let { showMove(game, it) }
            }
        }
    }

    private fun initSudoku(
        sudoku: Sudoku,
        paused: Boolean = false,
    ) {
        initialized = true
        setTitle(sudoku.info)
        initSudokuButtons(sudoku)
        renderGame()
        if (paused) animateGameButtonsVisibility(false) else resumeGame()
    }

    private fun initSudokuButtons(sudoku: Sudoku) {
        sudokuButtons.clear()
        sudokuButtons.addAll(allNumberButtons.take(sudoku.info.size.value))
        for (index in sudokuButtons.indices) {
            sudokuButtons[index].isVisible = true
            sudokuButtons[index].setOnClickListener { viewModel.onTap(InputTap.Digit(index + 1)) }
        }
        binding.deleteButton.setOnClickListener { viewModel.onTap(InputTap.Delete) }
        binding.hintButton.setOnClickListener { viewModel.onTap(InputTap.Hint) }
        binding.resumeButton.setOnClickListener { resumeGame() }
    }

    fun resumeGame() {
        if (!initialized) return
        binding.resumeButton.transformTo(binding.board)
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
        if (binding.board.isVisible) binding.board.transformTo(binding.resumeButton)
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

    private fun showWon() {
        animateGameButtonsVisibility(false)
        invalidateOptionsMenu()
        updateKeepScreenOn()
    }

    private fun onWinAnimationEnded() {
        winAnimating = false
        showWon()
        (viewModel.completion.value as? SudokuCompletion.Result)?.let(::onCompletionResult)
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
        viewModel.onPlayGamesSyncHandled(sync)
        inAppReview.showIfPossible(this)
        lifecycleScope.launch { playGamesSyncer(this@SudokuActivity, sync) }
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

    private fun showMove(
        playing: SudokuGame.Playing,
        result: MoveResult,
    ) {
        viewModel.onMoveShown(result)
        val size = playing.sudoku.info.size
        val index = result.move.index
        if (userSettings.animationsEnabled) {
            val regions = result.completed.copy(board = false)
            if (regions != CompletedRegions.NONE) binding.board.animateCells(animationSteps(size, index, regions), CellAnimation.WAVE)
        }
        if (result.completed.board) {
            if (userSettings.animationsEnabled) {
                winAnimating = true
                val steps = animationSteps(size, index, CompletedRegions.NONE.copy(board = true))
                binding.board.animateCells(steps, CellAnimation.BOARD_WAVE) { onWinAnimationEnded() }
            } else {
                showWon()
            }
        }
        if (playing.phase == Phase.Lost) onErrorLimitReached(playing)
    }

    private fun renderGame() {
        val playing = playing ?: return
        if (!initialized) return
        val input = viewModel.input.value
        val board = playing.sudoku.board
        binding.board.render = boardRenderOf(board, input, userSettings.highlightRegional, userSettings.highlightNumber)
        renderControls(input, board.completedDigits)
        refreshHintButton(playing.sudoku.info)
    }

    private fun renderControls(
        input: BoardInput,
        completedDigits: Set<Int>,
    ) {
        sudokuButtons.forEachIndexed { index, button ->
            val digit = index + 1
            button.backgroundTintList = tintOf(input.selection == Selection.Digit(digit))
            button.isEnabled = digit !in completedDigits
            button.setTextColor(
                getColor(
                    if (digit in completedDigits) {
                        commonutilsR.color.commonutils_secondary_text_icon_color
                    } else {
                        commonutilsR.color.commonutils_primary_text_icon_color
                    },
                ),
            )
        }
        binding.deleteButton.backgroundTintList = tintOf(input.selection == Selection.Delete)
        binding.hintButton.backgroundTintList = tintOf(input.selection == Selection.Hint)
        binding.noteButton.backgroundTintList = tintOf(input.notesMode)
    }

    private fun tintOf(selected: Boolean) = if (selected) colorPrimary else transparent

    private fun refreshHintButton(info: GameInfo) {
        binding.hintButton.isVisible = info.isHintAvailable
        binding.hintButton.text = getString(R.string.hint, info.availableHints)
    }

    private fun setTitle(info: GameInfo) {
        val detail =
            when (val mode = info.mode) {
                GameMode.Normal -> info.difficulty.getLocalString(resources)
                is GameMode.Daily -> mode.date.dateFormatShort
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
            singleLaunch { share(content, playing.sudoku) }
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

        private val digitKeyDigit: Map<Int, Int> =
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
            ).withIndex().associate { (index, keyCode) -> keyCode to index + 1 }
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
