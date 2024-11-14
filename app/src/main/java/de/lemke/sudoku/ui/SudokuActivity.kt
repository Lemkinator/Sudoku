package de.lemke.sudoku.ui

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.games.PlayGames
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.databinding.ActivitySudokuBinding
import de.lemke.sudoku.domain.*
import de.lemke.sudoku.domain.model.*
import de.lemke.sudoku.ui.utils.SudokuViewAdapter
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.utils.DialogUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject


@AndroidEntryPoint
class SudokuActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySudokuBinding
    private lateinit var loadingDialog: ProgressDialog
    private lateinit var userSettings: UserSettings
    private lateinit var colorPrimary: ColorStateList
    lateinit var sudoku: Sudoku
    lateinit var gameAdapter: SudokuViewAdapter
    private val sudokuButtons: MutableList<AppCompatButton> = mutableListOf()
    private var notesEnabled = false
    private var selected: Int? = null

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var getSudoku: GetSudokuUseCase

    @Inject
    lateinit var generateSudoku: GenerateSudokuUseCase

    @Inject
    lateinit var generateSudokuLevel: GenerateSudokuLevelUseCase

    @Inject
    lateinit var getMaxSudokuLevel: GetMaxSudokuLevelUseCase

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @Inject
    lateinit var shareSudoku: ShareSudokuUseCase

    @Inject
    lateinit var updatePlayGames: UpdatePlayGamesUseCase

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySudokuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra("sudokuId")
        if (id == null) {
            finishAfterTransition()
            return
        }
        loadingDialog = ProgressDialog(this)
        loadingDialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
        loadingDialog.setCancelable(false)
        loadingDialog.show()

        val typedValue = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        colorPrimary = ColorStateList.valueOf(typedValue.data)

        binding.sudokuToolbarLayout.toolbar.inflateMenu(R.menu.sudoku_menu)
        lifecycleScope.launch {
            userSettings = getUserSettings()
            val nullableSudoku = getSudoku(SudokuId(id))
            if (nullableSudoku == null) finishAfterTransition()
            else initSudoku(nullableSudoku)
        }
        binding.noteButton.setOnClickListener { toggleOrSetNoteButton() }
        binding.sudokuToolbarLayout.setNavigationButtonOnClickListener { finishAfterTransition() }
        binding.sudokuToolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        setCustomBackPressAnimation(binding.root)
    }

    override fun onPause() {
        super.onPause()
        if (this::sudoku.isInitialized) {
            pauseGame()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.sudoku_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_pause_play -> if (sudoku.resumed) pauseGame() else resumeGame()
            R.id.menu_reset -> restartGame()
            R.id.menu_share -> shareDialog()
        }
        return true
    }

    private fun setToolbarMenuItemsVisible(pausePlay: Boolean = false, reset: Boolean = false) {
        binding.sudokuToolbarLayout.toolbar.menu.setGroupVisible(R.id.sudoku_menu_group_pause_play, pausePlay)
        binding.sudokuToolbarLayout.toolbar.menu.setGroupVisible(R.id.sudoku_menu_group_reset, reset)
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
        if (sudoku.isDailySudoku && sudoku.created.toLocalDate() != LocalDate.now()) {
            setToolbarMenuItemsVisible()
            binding.gameButtons.visibility = View.GONE
        } else resumeGame()
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
            sudokuButtons[index].visibility = View.VISIBLE
            sudokuButtons[index].setOnClickListener { select(sudoku.itemCount + index) }
        }
        binding.deleteButton.setOnClickListener { select(sudoku.itemCount + sudoku.size) }
        binding.hintButton.setOnClickListener { select(sudoku.itemCount + sudoku.size + 1) }
        selectButton(null, false)
        checkAnyNumberCompleted()
        refreshHintButton()
    }

    @Suppress("unused")
    fun resumeGame(view: View? = null) {
        binding.resumeButtonLayout.visibility = View.GONE
        binding.gameLayout.visibility = View.VISIBLE
        if (sudoku.completed) {
            setToolbarMenuItemsVisible(reset = !sudoku.isDailySudoku)
            binding.gameButtons.visibility = View.GONE
        } else {
            sudoku.startTimer()
            val itemPausePlay: MenuItem = binding.sudokuToolbarLayout.toolbar.menu.findItem(R.id.menu_pause_play)
            itemPausePlay.icon = AppCompatResources.getDrawable(this, dev.oneuiproject.oneui.R.drawable.ic_oui_control_pause)
            itemPausePlay.title = getString(R.string.pause)
            setToolbarMenuItemsVisible(pausePlay = true)
            binding.gameButtons.visibility = View.VISIBLE
        }
        checkErrorLimit()
        if (userSettings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun pauseGame() {
        sudoku.stopTimer()
        if (sudoku.completed) return
        binding.gameLayout.visibility = View.GONE
        binding.gameButtons.visibility = View.GONE
        binding.resumeButtonLayout.visibility = View.VISIBLE
        val itemPausePlay: MenuItem = binding.sudokuToolbarLayout.toolbar.menu.findItem(R.id.menu_pause_play)
        itemPausePlay.icon = AppCompatResources.getDrawable(this, dev.oneuiproject.oneui.R.drawable.ic_oui_control_play)
        itemPausePlay.title = getString(R.string.resume)
        setToolbarMenuItemsVisible(pausePlay = true)
        if (userSettings.keepScreenOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch { saveSudoku(sudoku) }
    }

    inner class SudokuGameListener : GameListener {
        override fun onFieldClicked(position: Position) {
            select(position.index)
        }

        override fun onFieldChanged(position: Position) {
            gameAdapter.updateFieldView(position.index)
            checkAnyNumberCompleted()
            checkRowColumnBlockCompleted(position)
            lifecycleScope.launch { saveSudoku(sudoku) }
        }

        override fun onCompleted(position: Position) {
            if (userSettings.animationsEnabled) animate(position, animateSudoku = true)?.invokeOnCompletion { onSudokuCompleted() }
            else onSudokuCompleted()
        }

        override fun onError() {
            checkErrorLimit()
        }

        override fun onTimeChanged() {
            lifecycleScope.launch { setSubtitle() }
        }
    }

    private fun onSudokuCompleted() {
        setSubtitle()
        val dialog = AlertDialog.Builder(this@SudokuActivity)
            .setTitle(R.string.completed_title)
            .setMessage(sudoku.getLocalStatisticsString(resources))
        dialog.setNeutralButton(R.string.ok, null)
        lifecycleScope.launch {
            if (sudoku.isSudokuLevel && getMaxSudokuLevel(sudoku.size) == sudoku.modeLevel) dialog.setPositiveButton(R.string.next_level) { _, _ ->
                lifecycleScope.launch {
                    loadingDialog.show()
                    val nextSudokuLevel = generateSudokuLevel(sudoku.size, level = sudoku.modeLevel + 1)
                    saveSudoku(nextSudokuLevel)
                    initSudoku(nextSudokuLevel)
                }
            }
            else if (sudoku.isNormalSudoku) dialog.setPositiveButton(R.string.new_game) { _, _ ->
                lifecycleScope.launch {
                    loadingDialog.show()
                    val newSudoku = generateSudoku(sudoku.size, sudoku.difficulty)
                    saveSudoku(newSudoku)
                    initSudoku(newSudoku)
                }
            }
            dialog.show()
            setToolbarMenuItemsVisible(reset = !sudoku.isDailySudoku)
            updatePlayGames(this@SudokuActivity, sudoku)
            binding.gameButtons.visibility = View.GONE
            try {
                opportunityToShowInAppReview()
            } catch (e: Exception) {
                Log.e("InAppReview", "Error: ${e.message}")
            }
            saveSudoku(sudoku)
        }
    }

    private suspend fun opportunityToShowInAppReview() {
        val lastInAppReviewRequest = getUserSettings().lastInAppReviewRequest
        val daysSinceLastRequest = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastInAppReviewRequest)
        if (daysSinceLastRequest < 14) return
        updateUserSettings { it.copy(lastInAppReviewRequest = System.currentTimeMillis()) }
        val manager = ReviewManagerFactory.create(this)
        //val manager = FakeReviewManager(context);
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener {}
            } else {
                // There was some problem, log or handle the error code.
                Log.e("InAppReview", "Review task failed: ${task.exception?.message}")
            }
        }
    }

    private fun checkErrorLimit(): Boolean {
        val errorLimit = when {
            sudoku.isDailySudoku -> Sudoku.MODE_DAILY_ERROR_LIMIT
            sudoku.isSudokuLevel -> Sudoku.MODE_LEVEL_ERROR_LIMIT
            else -> userSettings.errorLimit
        }
        if (sudoku.errorLimitReached(errorLimit)) {
            sudoku.stopTimer()
            setSubtitle()
            binding.gameButtons.visibility = View.GONE
            setToolbarMenuItemsVisible(reset = true)
            AlertDialog.Builder(this@SudokuActivity).setTitle(R.string.gameover)
                .setMessage(getString(R.string.error_limit_reached, errorLimit))
                .setPositiveButton(R.string.restart) { _, _ -> restartGame() }
                .setNeutralButton(R.string.ok, null)
                .show()
            return true
        }
        return false
    }

    private fun restartGame() {
        loadingDialog.show()
        sudoku.reset()
        lifecycleScope.launch {
            saveSudoku(sudoku)
            initSudoku(sudoku)
        }
    }

    private fun select(newSelected: Int?) {
        if (checkErrorLimit()) return
        if (binding.sudokuToolbarLayout.isExpanded) binding.sudokuToolbarLayout.setExpanded(false, true)
        when (selected) {
            null -> {//nothing is selected
                when (newSelected) {
                    //selected nothing
                    null -> {}
                    //selected field
                    in 0 until sudoku.itemCount -> {
                        gameAdapter.selectFieldView(newSelected, userSettings.highlightRegional, userSettings.highlightNumber)
                        selected = newSelected
                    }
                    //selected button
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 ->
                        selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
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
                        sudoku.move(position, newSelected - sudoku.itemCount + 1, notesEnabled)
                        selected = null
                    }
                    //selected delete
                    sudoku.itemCount + sudoku.size -> {
                        sudoku.move(position, null, notesEnabled)
                        selected = null
                    }
                    //selected hint
                    sudoku.itemCount + sudoku.size + 1 -> {
                        sudoku.setHint(position)
                        selected = null
                        refreshHintButton()
                    }
                }
                gameAdapter.selectFieldView(selected, userSettings.highlightRegional, userSettings.highlightNumber)
            }

            in sudoku.itemCount until sudoku.itemCount + sudoku.size -> { //number button is selected
                when (newSelected) {
                    //selected nothing
                    null -> selectButton(null, userSettings.highlightNumber)
                    //selected same button
                    selected -> selectButton(null, userSettings.highlightNumber)
                    //selected field
                    in 0 until sudoku.itemCount -> {
                        sudoku.move(newSelected, selected!! - sudoku.itemCount + 1, notesEnabled)
                        highlightCurrentNumber(selected!! - sudoku.itemCount + 1)
                    }
                    //selected button
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> {
                        gameAdapter.selectFieldView(null, userSettings.highlightRegional, userSettings.highlightNumber)
                        selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
                    }
                    //selected nothing
                    else -> selectButton(null, userSettings.highlightNumber)
                }
            }

            sudoku.itemCount + sudoku.size -> { // delete button is selected
                when (newSelected) {
                    //selected nothing
                    null -> selectButton(null, userSettings.highlightNumber)
                    //selected same button
                    selected -> selectButton(null, userSettings.highlightNumber)
                    //selected field
                    in 0 until sudoku.itemCount -> sudoku.move(newSelected, null, notesEnabled)
                    //selected button(not delete)
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 ->
                        selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
                    //selected nothing
                    else -> selectButton(null, userSettings.highlightNumber)
                }
            }

            sudoku.itemCount + sudoku.size + 1 -> { // hint button is selected
                when (newSelected) {
                    //selected nothing
                    null -> selectButton(null, userSettings.highlightNumber)
                    //selected same button
                    selected -> selectButton(null, userSettings.highlightNumber)
                    //selected field
                    in 0 until sudoku.itemCount -> {
                        sudoku.setHint(newSelected)
                        if (!sudoku.isHintAvailable) selected = null
                        refreshHintButton()
                    }
                    //selected button(not hint)
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 1 ->
                        selectButton(newSelected - sudoku.itemCount, userSettings.highlightNumber)
                    //selected nothing
                    else -> selectButton(null, userSettings.highlightNumber)
                }
            }
        }
    }

    private fun checkRowColumnBlockCompleted(position: Position) {
        if (userSettings.animationsEnabled) animate(
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

    private fun selectButton(i: Int?, highlightSelectedNumber: Boolean) {
        val transparent = ColorStateList.valueOf(getColor(android.R.color.transparent))
        for (button in sudokuButtons) button.backgroundTintList = transparent
        binding.deleteButton.backgroundTintList = transparent
        binding.hintButton.backgroundTintList = transparent
        if (i != null) {
            when (i) {
                sudoku.size -> binding.deleteButton.backgroundTintList = colorPrimary
                sudoku.size + 1 -> binding.hintButton.backgroundTintList = colorPrimary

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

    private fun selectNextButton(currentNumber: Int, completedNumbers: List<Pair<Int, Boolean>>) {
        var number = currentNumber
        while (completedNumbers[number - 1].second) {
            number++
            if (number > completedNumbers.size) number = 1 //wrap around
            if (number == currentNumber) { //all numbers are completed
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
                sudokuButtons[pair.first - 1].setTextColor(getColor(R.color.secondary_text_icon_color))
            } else {
                sudokuButtons[pair.first - 1].isEnabled = true
                sudokuButtons[pair.first - 1].setTextColor(getColor(R.color.primary_text_icon_color))
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
        binding.noteButton.backgroundTintList =
            if (notesEnabled) colorPrimary
            else ColorStateList.valueOf(resources.getColor(android.R.color.transparent, theme))
    }

    private fun refreshHintButton() {
        binding.hintButton.visibility = if (sudoku.isHintAvailable) View.VISIBLE else View.GONE
        binding.hintButton.text = getString(R.string.hint, sudoku.availableHints)
    }

    private fun setTitle() {
        binding.sudokuToolbarLayout.setTitle(
            getString(R.string.app_name) +
                    when {
                        sudoku.isNormalSudoku -> " (" + sudoku.difficulty.getLocalString(this.resources) + ")"
                        sudoku.isDailySudoku -> " (" + sudoku.created.dateFormatShort + ")"
                        sudoku.isSudokuLevel -> " (Level " + sudoku.modeLevel + ")"
                        else -> ""
                    }
        )
    }

    @SuppressLint("StringFormatInvalid")
    private fun setSubtitle() {
        val errorLimit = userSettings.errorLimit
        val subtitle = getString(R.string.current_time, sudoku.timeString) + " | " + getString(
            R.string.current_progress,
            sudoku.progress
        ) + " | " + when {
            sudoku.isDailySudoku -> getString(R.string.current_errors_with_limit, sudoku.errorsMade, Sudoku.MODE_DAILY_ERROR_LIMIT)
            sudoku.isSudokuLevel -> getString(R.string.current_errors_with_limit, sudoku.errorsMade, Sudoku.MODE_LEVEL_ERROR_LIMIT)
            errorLimit == 0 -> getString(R.string.current_errors, sudoku.errorsMade)
            else -> getString(R.string.current_errors_with_limit, sudoku.errorsMade, errorLimit)
        } + if (sudoku.isNormalSudoku) " | " + getString(R.string.current_hints, sudoku.hintsUsed) else ""
        binding.sudokuToolbarLayout.setExpandedSubtitle(subtitle)
        binding.sudokuToolbarLayout.setCollapsedSubtitle(subtitle)
    }

    private fun shareDialog() {
        pauseGame()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.share_sudoku)
            .setView(R.layout.dialog_share)
            .setPositiveButton(R.string.share, null)
            .setNegativeButton(R.string.sesl_cancel, null)
            .create()
        dialog.show()
        dialog.findViewById<TextView>(R.id.share_statistics)?.text = sudoku.getLocalStatisticsString(resources)
        DialogUtils.setDialogProgressForButton(dialog, DialogInterface.BUTTON_POSITIVE) {
            lifecycleScope.launch {
                when (dialog.findViewById<RadioGroup>(R.id.share_radio_group)?.checkedRadioButtonId) {
                    R.id.radio_button_text -> shareStats()
                    R.id.radio_button_initial -> shareGame(sudoku.getInitialSudoku())
                    R.id.radio_button_current -> shareGame(sudoku.copy(modeLevel = Sudoku.MODE_NORMAL))
                }
                dialog.dismiss()
            }
        }
    }

    private fun shareStats() {
        val sendIntent = Intent(Intent.ACTION_SEND)
        sendIntent.type = "text/plain"
        sendIntent.putExtra(Intent.EXTRA_TEXT, sudoku.getLocalStatisticsStringShare(resources))
        sendIntent.putExtra(Intent.EXTRA_TITLE, getString(R.string.share_sudoku))
        sendIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_sudoku)))
    }

    private suspend fun shareGame(sudoku: Sudoku) {
        PlayGames.getAchievementsClient(this@SudokuActivity).unlock(getString(R.string.achievement_share_sudoku))
        val uri = shareSudoku(sudoku)
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "application/sudoku" //octet-stream"
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_sudoku)))
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_1 -> select(sudoku.itemCount).let { true }
            KeyEvent.KEYCODE_2 -> select(sudoku.itemCount + 1).let { true }
            KeyEvent.KEYCODE_3 -> select(sudoku.itemCount + 2).let { true }
            KeyEvent.KEYCODE_4 -> select(sudoku.itemCount + 3).let { true }
            KeyEvent.KEYCODE_5 -> (sudoku.size > 4).takeIf { it }?.let { select(sudoku.itemCount + 4) }?.let { true } == true
            KeyEvent.KEYCODE_6 -> (sudoku.size > 4).takeIf { it }?.let { select(sudoku.itemCount + 5) }?.let { true } == true
            KeyEvent.KEYCODE_7 -> (sudoku.size > 4).takeIf { it }?.let { select(sudoku.itemCount + 6) }?.let { true } == true
            KeyEvent.KEYCODE_8 -> (sudoku.size > 4).takeIf { it }?.let { select(sudoku.itemCount + 7) }?.let { true } == true
            KeyEvent.KEYCODE_9 -> (sudoku.size > 4).takeIf { it }?.let { select(sudoku.itemCount + 8) }?.let { true } == true
            KeyEvent.KEYCODE_A -> (sudoku.size > 9).takeIf { it }?.let { select(sudoku.itemCount + 9) }?.let { true } == true
            KeyEvent.KEYCODE_B -> (sudoku.size > 9).takeIf { it }?.let { select(sudoku.itemCount + 10) }?.let { true } == true
            KeyEvent.KEYCODE_C -> (sudoku.size > 9).takeIf { it }?.let { select(sudoku.itemCount + 11) }?.let { true } == true
            KeyEvent.KEYCODE_D -> (sudoku.size > 9).takeIf { it }?.let { select(sudoku.itemCount + 12) }?.let { true } == true
            KeyEvent.KEYCODE_E -> (sudoku.size > 9).takeIf { it }?.let { select(sudoku.itemCount + 13) }?.let { true } == true
            KeyEvent.KEYCODE_F -> (sudoku.size > 9).takeIf { it }?.let { select(sudoku.itemCount + 14) }?.let { true } == true
            KeyEvent.KEYCODE_G -> (sudoku.size > 9).takeIf { it }?.let { select(sudoku.itemCount + 15) }?.let { true } == true
            KeyEvent.KEYCODE_DEL -> select(sudoku.itemCount + sudoku.size).let { true }
            KeyEvent.KEYCODE_H -> select(sudoku.itemCount + sudoku.size + 1).let { true }
            KeyEvent.KEYCODE_N -> toggleOrSetNoteButton().let { true }
            KeyEvent.KEYCODE_ESCAPE -> select(null).let { true }
            else -> super.onKeyUp(keyCode, event)
        }
    }
}