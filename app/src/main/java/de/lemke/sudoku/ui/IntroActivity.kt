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
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.SingleLaunchResultLauncher
import de.lemke.commonutils.ui.utils.advanceOnboarding
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.onSingleLaunchClick
import de.lemke.commonutils.ui.utils.registerForSingleLaunchResult
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.showOnce
import de.lemke.commonutils.ui.utils.singleLaunchMenuItem
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.databinding.ActivityIntroBinding
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.tutorialSudoku
import de.lemke.sudoku.ui.board.CellAnimation
import de.lemke.sudoku.ui.game.BoardInput
import de.lemke.sudoku.ui.game.InputTap
import de.lemke.sudoku.ui.game.Selection
import de.lemke.sudoku.ui.game.boardRenderOf
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import de.lemke.commonutils.R as commonutilsR
import dev.oneuiproject.oneui.design.R as designR

private const val DEMO_BLOCK_CELL_6 = 6
private const val DEMO_ROW_1_CELL = 9
private const val DEMO_COLUMN_5_CELL = 5
private const val DEMO_ENTRY_CELL_21 = 21
private const val DEMO_ENTRY_DIGIT_8 = 8
private const val DEMO_ROW_FIRST_CELL_3 = 27
private const val DEMO_ROW_FIRST_CELL_4 = 36
private const val SHOW_MILLIS = 900
private const val ENTRY_MILLIS = 1200
private const val PULSE_PAUSE_MILLIS = 2000

private data class IntroDemo(
    val cells: Set<Int> = emptySet(),
    val input: BoardInput? = null,
)

@AndroidEntryPoint
class IntroActivity : AppCompatActivity() {
    internal lateinit var binding: ActivityIntroBinding
    internal var animation: Job? = null
    private var animatedStep: IntroStep? = null
    private var demo = IntroDemo()
    private val tutorial = tutorialSudoku()
    private val tutorialBoard: Board get() = tutorial.board
    internal val colorPrimary: Int
        get() = TypedValue().also { theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, it, true) }.data
    internal val viewModel: IntroViewModel by viewModels()

    @Inject
    lateinit var userSettings: UserSettings

    private val sudokuButtons: List<AppCompatButton>
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
                )
            }

    private val requestPermissionLauncher: SingleLaunchResultLauncher<String> =
        registerForSingleLaunchResult(RequestPermission()) { isGranted: Boolean ->
            viewModel.onNotificationPermissionResult(isGranted)
        }

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        collectState(viewModel.notificationChoice, minActiveState = RESUMED) {
            if (it is NotificationChoice.Result) onNotificationChoice(it)
        }
        binding.sudokuToolbarLayout.setTitle(getString(R.string.intro))
        binding.hintButton.isVisible = true
        binding.hintButton.text = getString(R.string.hint, tutorial.info.availableHints)
        sudokuButtons.forEachIndexed { index, button ->
            button.isVisible = true
            button.setOnClickListener { tap(InputTap.Digit(index + 1)) }
        }
        binding.board.onCellClick = { tap(InputTap.Cell(it)) }
        binding.board.onCellLongClick = viewModel::onErased
        binding.deleteButton.setOnClickListener { tap(InputTap.Delete) }
        binding.hintButton.setOnClickListener { tap(InputTap.Hint) }
        binding.noteButton.setOnClickListener { viewModel.onNotesToggled() }
        binding.introNextButton.setOnClickListener { viewModel.onNext() }
        binding.introContinueButton.onSingleLaunchClick { showNotificationsDialogOrFinish() }
        collectState(viewModel.state) { render(it) }
    }

    override fun onCreateOptionsMenu(menu: Menu?) = menuInflater.inflate(R.menu.intro_menu, menu).let { true }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.menu_skip -> singleLaunchMenuItem { showNotificationsDialogOrFinish() }
            else -> super.onOptionsItemSelected(item)
        }

    private fun tap(tap: InputTap) {
        if (binding.sudokuToolbarLayout.isExpanded) binding.sudokuToolbarLayout.setExpanded(expanded = false, animate = true)
        viewModel.onTap(tap)
    }

    private fun render(state: IntroState) {
        val step = state.step
        binding.introTitle.isVisible = step.titleRes != null
        step.titleRes?.let(binding.introTitleText::setText)
        binding.introTextText.setText(step.textRes)
        binding.gameButtons.isVisible = step >= IntroStep.ENTER_NUMBER
        binding.numberButtons.isVisible = step < IntroStep.TOOLS
        binding.otherButtons.isVisible = step >= IntroStep.TOOLS
        binding.introContinueLayout.isVisible = step == IntroStep.NOTES
        if (animatedStep != step) {
            animatedStep = step
            animation?.cancel()
            demo = IntroDemo()
            animation = lifecycleScope.launch { animate(step) }
        }
        val input = demo.input ?: state.input
        binding.board.render =
            boardRenderOf(state.board(tutorialBoard), input, highlightRegional = true, highlightNumber = true)
                .copy(highlighted = demo.cells)
        renderControls(input)
    }

    private fun renderControls(input: BoardInput) {
        sudokuButtons.forEachIndexed { index, button -> button.backgroundTintList = tintOf(input.selection == Selection.Digit(index + 1)) }
        binding.deleteButton.backgroundTintList = tintOf(input.selection == Selection.Delete)
        binding.hintButton.backgroundTintList = tintOf(input.selection == Selection.Hint)
        binding.noteButton.backgroundTintList = tintOf(input.notesMode)
    }

    private fun tintOf(selected: Boolean) = ColorStateList.valueOf(if (selected) colorPrimary else getColor(android.R.color.transparent))

    private fun showDemo(demo: IntroDemo) {
        this.demo = demo
        render(viewModel.state.value)
    }

    private fun pulse(cells: Collection<Int>) {
        if (userSettings.animationsEnabled) binding.board.animateCells(listOf(cells.toList()), CellAnimation.PULSE)
    }

    private suspend fun animate(step: IntroStep) {
        when (step) {
            IntroStep.BLOCKS -> {
                animateRegions(step)
            }

            IntroStep.SELECT_CELL -> {
                animateTarget(step, tutorialBoard.rowOf(0), DEMO_CELL_INDEX_4)
            }

            IntroStep.ENTER_ROW_COLUMN -> {
                val rows = tutorialBoard.rowOf(DEMO_ROW_FIRST_CELL_3) + tutorialBoard.rowOf(DEMO_ROW_FIRST_CELL_4)
                animateTarget(step, rows, DEMO_CELL_INDEX_49)
            }

            IntroStep.ENTER_BLOCK -> {
                animateTarget(step, tutorialBoard.blockOf(DEMO_BLOCK_CELL_6), DEMO_CELL_INDEX_24)
            }

            IntroStep.NUMBER_ENTRY_DEMO -> {
                animateNumberEntry(step)
            }

            IntroStep.SELECTION,
            IntroStep.ENTER_NUMBER,
            IntroStep.SELECT_NUMBER,
            IntroStep.NUMBER_ENTRY,
            IntroStep.TOOLS,
            IntroStep.NOTES,
            -> {}
        }
    }

    private suspend fun animateRegions(step: IntroStep) {
        val regions = listOf(tutorialBoard.blockOf(0), tutorialBoard.rowOf(DEMO_ROW_1_CELL), tutorialBoard.columnOf(DEMO_COLUMN_5_CELL))
        while (viewModel.state.value.step == step) {
            for (cells in regions) {
                delay(SHOW_MILLIS.milliseconds)
                showDemo(IntroDemo(cells = cells.toSet()))
                pulse(cells)
            }
        }
    }

    private suspend fun animateTarget(
        step: IntroStep,
        highlighted: Collection<Int>,
        target: Int,
    ) {
        showDemo(IntroDemo(cells = highlighted.toSet()))
        while (viewModel.state.value.step == step) {
            pulse(listOf(target))
            delay(PULSE_PAUSE_MILLIS.milliseconds)
        }
    }

    private suspend fun animateNumberEntry(step: IntroStep) {
        val frames =
            listOf(
                BoardInput(Selection.Cell(DEMO_CELL_INDEX_4)),
                BoardInput(Selection.Digit(DEMO_ENTRY_DIGIT_8)),
                BoardInput(Selection.Digit(DEMO_ENTER_DIGIT)),
                BoardInput(Selection.Cell(DEMO_ENTRY_CELL_21)),
                BoardInput(Selection.Digit(DEMO_SELECT_DIGIT)),
            )
        while (viewModel.state.value.step == step) {
            for (frame in frames) {
                delay(ENTRY_MILLIS.milliseconds)
                showDemo(IntroDemo(input = frame))
            }
        }
    }

    private fun onNotificationChoice(result: NotificationChoice.Result) {
        val settled =
            when (result) {
                NotificationChoice.Saved -> {
                    advanceOnboarding().isSettled
                }

                NotificationChoice.RequestPermission -> {
                    if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissionLauncher.tryLaunch(POST_NOTIFICATIONS)
                    } else {
                        true.also { viewModel.onNotificationPermissionResult(isGranted = true) }
                    }
                }
            }
        if (settled) viewModel.onNotificationChoiceHandled(result)
    }

    private fun showNotificationsDialogOrFinish() {
        when (val choice = viewModel.notificationChoice.value) {
            is NotificationChoice.Result -> onNotificationChoice(choice)
            NotificationChoice.Pending -> if (viewModel.openedFromSettings) finishAfterTransition() else notificationsDialog()
        }
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
                .showOnce(NOTIFICATIONS_DIALOG_TAG) ?: return
        dialog.getButton(BUTTON_NEGATIVE).setTextColor(getColor(designR.color.oui_des_functional_red_color))
    }

    companion object {
        const val KEY_OPENED_FROM_SETTINGS = "openedFromSettings"
        private const val NOTIFICATIONS_DIALOG_TAG = "notifications"
    }
}

private val IntroStep.titleRes: Int?
    @StringRes get() =
        when (this) {
            IntroStep.BLOCKS, IntroStep.SELECTION -> R.string.intro_title

            IntroStep.NUMBER_ENTRY -> R.string.intro_title7

            IntroStep.NUMBER_ENTRY_DEMO -> R.string.intro_title8

            IntroStep.TOOLS -> R.string.intro_title9

            IntroStep.SELECT_CELL,
            IntroStep.ENTER_NUMBER,
            IntroStep.SELECT_NUMBER,
            IntroStep.ENTER_ROW_COLUMN,
            IntroStep.ENTER_BLOCK,
            IntroStep.NOTES,
            -> null
        }

private val IntroStep.textRes: Int
    @StringRes get() =
        when (this) {
            IntroStep.BLOCKS -> R.string.intro_text0
            IntroStep.SELECTION -> R.string.intro_text1
            IntroStep.SELECT_CELL -> R.string.intro_text2
            IntroStep.ENTER_NUMBER -> R.string.intro_text3
            IntroStep.SELECT_NUMBER -> R.string.intro_text4
            IntroStep.ENTER_ROW_COLUMN -> R.string.intro_text5
            IntroStep.ENTER_BLOCK -> R.string.intro_text6
            IntroStep.NUMBER_ENTRY -> R.string.intro_text7
            IntroStep.NUMBER_ENTRY_DEMO -> R.string.intro_text8
            IntroStep.TOOLS -> R.string.intro_text9
            IntroStep.NOTES -> R.string.intro_text10
        }
