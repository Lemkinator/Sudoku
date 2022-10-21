package de.lemke.sudoku.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivitySudokuBinding
import de.lemke.sudoku.domain.*
import de.lemke.sudoku.domain.model.GameListener
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.ui.utils.SudokuViewAdapter
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.utils.internal.ReflectUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.math.abs


@AndroidEntryPoint
class SudokuActivity : AppCompatActivity() {
    lateinit var sudoku: Sudoku
    lateinit var nextSudokuLevel: Sudoku
    lateinit var gameAdapter: SudokuViewAdapter
    lateinit var toolbarMenu: Menu
    private lateinit var binding: ActivitySudokuBinding
    private lateinit var loadingDialog: ProgressDialog
    private val selectButtons: MutableList<AppCompatButton> = mutableListOf()
    private var notesEnabled = false
    private val animationDuration = 50L
    private var selected: Int? = null

    companion object {
        var refreshView = false
    }

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var getSudoku: GetSudokuUseCase

    @Inject
    lateinit var generateSudokuLevel: GenerateSudokuLevelUseCase

    @Inject
    lateinit var saveSudoku: SaveSudokuUseCase

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySudokuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadingDialog = ProgressDialog(this)
        loadingDialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
        loadingDialog.setCancelable(false)
        loadingDialog.show()

        selectButtons.add(binding.numberButton1)
        selectButtons.add(binding.numberButton2)
        selectButtons.add(binding.numberButton3)
        selectButtons.add(binding.numberButton4)
        selectButtons.add(binding.numberButton5)
        selectButtons.add(binding.numberButton6)
        selectButtons.add(binding.numberButton7)
        selectButtons.add(binding.numberButton8)
        selectButtons.add(binding.numberButton9)
        selectButtons.add(binding.deleteButton)
        selectButtons.add(binding.hintButton)

        val id = intent.getStringExtra("sudokuId")
        if (id == null) {
            finish()
            return
        }
        lifecycleScope.launch { initSudoku(SudokuId(id)) }

        binding.autoNotesButton.setOnClickListener {
            AlertDialog.Builder(this@SudokuActivity).setTitle(getString(R.string.use_auto_notes))
                .setMessage(getString(R.string.use_auto_notes_message)).setNegativeButton(getString(R.string.sesl_cancel), null)
                .setPositiveButton(R.string.ok) { _, _ -> sudoku.autoNotes() }.show()
        }
        binding.noteButton.setOnClickListener { toggleOrSetNoteButton() }
        binding.sudokuDrawerLayout.setNavigationButtonIcon(
            AppCompatResources.getDrawable(this, dev.oneuiproject.oneui.R.drawable.ic_oui_back)
        )
        binding.sudokuDrawerLayout.setNavigationButtonOnClickListener { saveAndExit() }
        binding.sudokuDrawerLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        binding.sudokuDrawerLayout.appBarLayout.addOnOffsetChangedListener { layout: AppBarLayout, verticalOffset: Int ->
            val totalScrollRange = layout.totalScrollRange
            val inputMethodWindowVisibleHeight = ReflectUtils.genericInvokeMethod(
                InputMethodManager::class.java, getSystemService(INPUT_METHOD_SERVICE), "getInputMethodWindowVisibleHeight"
            ) as Int
            if (totalScrollRange != 0) binding.resumeButtonLayout.translationY = (abs(verticalOffset) - totalScrollRange).toFloat() / 2.0f
            else binding.resumeButtonLayout.translationY = (abs(verticalOffset) - inputMethodWindowVisibleHeight).toFloat() / 2.0f
        }
        binding.sudokuDrawerLayout.toolbar.inflateMenu(R.menu.sudoku_menu)
        toolbarMenu = binding.sudokuDrawerLayout.toolbar.menu
        setSupportActionBar(null)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveAndExit()
            }
        })
    }

    private fun saveAndExit() {
        lifecycleScope.launch {
            saveSudoku(sudoku)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (refreshView) {
            refreshView = false
            recreate()
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::sudoku.isInitialized) {
            pauseGame()
            lifecycleScope.launch { saveSudoku(sudoku) }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_undo -> sudoku.revertLastChange(gameAdapter)
            R.id.menu_pause_play -> toggleGameResumed()
            R.id.menu_reset -> restartGame()
        }
        return true
    }

    private suspend fun initSudoku(id: SudokuId) {
        val nullableSudoku = getSudoku(id)
        if (nullableSudoku == null) finish()
        else sudoku = nullableSudoku
        when (sudoku.modeLevel) {
            Sudoku.MODE_NORMAL -> {
                binding.sudokuDrawerLayout.setTitle(getString(R.string.app_name) + " (" + sudoku.difficulty.getLocalString(resources) + ")")
            }
            Sudoku.MODE_DAILY -> {
                binding.sudokuDrawerLayout.setTitle(
                    getString(R.string.app_name) + " (" + sudoku.created.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)) + ")"
                )
                binding.hintButton.visibility = View.GONE
                binding.autoNotesButton.visibility = View.GONE
            }
            else -> {
                binding.sudokuDrawerLayout.setTitle(getString(R.string.app_name) + " (Level " + sudoku.modeLevel + ")")
                binding.hintButton.visibility = View.GONE
                binding.autoNotesButton.visibility = View.GONE
            }
        }

        setSubtitle()
        binding.gameRecycler.layoutManager = GridLayoutManager(this@SudokuActivity, sudoku.size)
        gameAdapter = SudokuViewAdapter(this@SudokuActivity, sudoku)
        binding.gameRecycler.adapter = gameAdapter
        binding.gameRecycler.seslSetFillBottomEnabled(true)
        binding.gameRecycler.seslSetLastRoundedCorner(true)
        sudoku.gameListener = object : GameListener {
            override fun onHistoryChange(length: Int) {
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, sudoku.history.isNotEmpty())
            }

            override fun onFieldClicked(position: Position) {
                lifecycleScope.launch {
                    val errorLimit = getUserSettings().errorLimit
                    if (errorLimit != 0 && sudoku.errorsMade >= errorLimit) errorLimitDialog(errorLimit)
                    else select(position.index)
                }
            }

            override fun onFieldChanged(position: Position) {
                gameAdapter.updateFieldView(position.index)
                checkAnyNumberCompleted(position)
                lifecycleScope.launch {
                    checkRowColumnBlockCompleted(position)
                }
            }

            override fun onCompleted(position: Position) {
                setSubtitle()
                animateSudoku(position).invokeOnCompletion {
                    val completedMessage = getString(
                        R.string.completed_message,
                        sudoku.size,
                        sudoku.difficulty.getLocalString(resources),
                        sudoku.timeString,
                        sudoku.errorsMade,
                        sudoku.hintsUsed,
                        sudoku.notesMade,
                        getString(if (sudoku.regionalHighlightingUsed) R.string.yes else R.string.no),
                        getString(if (sudoku.numberHighlightingUsed) R.string.yes else R.string.no),
                        getString(if (sudoku.autoNotesUsed) R.string.yes else R.string.no),
                        sudoku.created.format(
                            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
                        ),
                        sudoku.updated.format(
                            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
                        ),
                    )
                    val dialog = AlertDialog.Builder(this@SudokuActivity).setTitle(R.string.completed_title).setMessage(completedMessage)
                    dialog.setNeutralButton(R.string.ok, null)
                    when (sudoku.modeLevel) {
                        Sudoku.MODE_DAILY, Sudoku.MODE_NORMAL -> {
                            dialog.setPositiveButton(R.string.share_result) { _, _ ->
                                val sendIntent = Intent(Intent.ACTION_SEND)
                                sendIntent.type = "text/plain"
                                sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.text_share) + completedMessage)
                                sendIntent.putExtra(Intent.EXTRA_TITLE, getString(R.string.share_result))
                                sendIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                startActivity(Intent.createChooser(sendIntent, "Share Via"))
                            }
                        }
                        else -> {
                            dialog.setPositiveButton(R.string.next_level) { _, _ ->
                                lifecycleScope.launch {
                                    loadingDialog.show()
                                    saveSudoku(nextSudokuLevel)
                                    saveSudoku(sudoku)
                                    initSudoku(nextSudokuLevel.id)
                                }
                            }
                        }
                    }
                    lifecycleScope.launch {
                        dialog.show()
                    }
                }
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, false)
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_pause_play, false)
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_reset, sudoku.modeLevel == Sudoku.MODE_NORMAL)
            }

            override fun onError() {
                lifecycleScope.launch {
                    val errorLimit = getUserSettings().errorLimit
                    if (errorLimit != 0 && sudoku.errorsMade >= errorLimit) {
                        sudoku.stopTimer()
                        setSubtitle()
                        errorLimitDialog(errorLimit)
                    }
                }
            }

            override fun onTimeChanged() {
                lifecycleScope.launch {
                    setSubtitle()
                    saveSudoku(sudoku)
                }
            }
        }
        for (index in selectButtons.indices) {
            selectButtons[index].setOnClickListener {
                lifecycleScope.launch {
                    val errorLimit = getUserSettings().errorLimit
                    if (errorLimit != 0 && sudoku.errorsMade >= errorLimit) errorLimitDialog(errorLimit)
                    else select(sudoku.itemCount + index)
                }
            }
        }
        resumeGame()
        loadingDialog.dismiss()
        checkAnyNumberCompleted(null)
        if (sudoku.modeLevel > 0) lifecycleScope.launch { nextSudokuLevel = generateSudokuLevel(level = sudoku.modeLevel + 1) }
    }

    private fun errorLimitDialog(errorLimit: Int) {
        toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, false)
        toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_pause_play, false)
        toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_reset, true)
        AlertDialog.Builder(this@SudokuActivity).setTitle(R.string.gameover).setMessage(getString(R.string.error_limit_reached, errorLimit))
            .setPositiveButton(R.string.restart) { _, _ -> restartGame() }.setNeutralButton(R.string.ok, null).show()
    }

    private fun restartGame() {
        lifecycleScope.launch {
            sudoku.reset()
            saveSudoku(sudoku)
            initSudoku(sudoku.id)
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun setSubtitle() {
        lifecycleScope.launch {
            val errorLimit = getUserSettings().errorLimit
            val subtitle = getString(R.string.current_time, sudoku.timeString) + " | " + getString(
                R.string.current_progress,
                sudoku.progress
            ) + " | " + if (errorLimit == 0) {
                getString(R.string.current_errors, sudoku.errorsMade)
            } else {
                getString(R.string.current_errors_with_limit, sudoku.errorsMade, errorLimit)
            } + " | " + getString(R.string.current_hints, sudoku.hintsUsed)
            binding.sudokuDrawerLayout.setExpandedSubtitle(subtitle)
            binding.sudokuDrawerLayout.setCollapsedSubtitle(subtitle)
        }
    }

    private suspend fun select(newSelected: Int?) {
        if (binding.sudokuDrawerLayout.isExpanded) binding.sudokuDrawerLayout.setExpanded(false, true)
        val highlightSudokuNeighbors = getUserSettings().highlightRegional
        val highlightSelectedNumber = getUserSettings().highlightNumber
        when (selected) {
            null -> {//nothing is selected
                when (newSelected) {
                    null -> {} //selected nothing
                    in 0 until sudoku.itemCount -> { //selected field
                        gameAdapter.selectFieldView(newSelected, highlightSudokuNeighbors, highlightSelectedNumber)
                        selected = newSelected
                    }
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> { //selected button
                        selectButton(newSelected - sudoku.itemCount, highlightSelectedNumber)
                    }
                    else -> {} //selected nothing
                }
            }
            in 0 until sudoku.itemCount -> { //field is selected
                val position = Position.create(selected!!, sudoku.size)
                when (newSelected) {
                    null -> { //selected nothing
                        gameAdapter.selectFieldView(null, highlightSudokuNeighbors, highlightSelectedNumber)
                        selected = null
                    }
                    selected -> { //selected same field
                        gameAdapter.selectFieldView(null, highlightSudokuNeighbors, highlightSelectedNumber)
                        selected = null
                    }
                    in 0 until sudoku.itemCount -> { //selected field
                        gameAdapter.selectFieldView(newSelected, highlightSudokuNeighbors, highlightSelectedNumber)
                        selected = newSelected
                    }
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size -> { //selected number
                        sudoku.move(position, newSelected - sudoku.itemCount + 1, notesEnabled)
                        if (highlightSelectedNumber) gameAdapter.highlightNumber(newSelected - sudoku.itemCount + 1)
                    }
                    sudoku.itemCount + sudoku.size -> { //selected delete
                        sudoku.move(position, null, notesEnabled)
                        gameAdapter.selectFieldView(selected, highlightSudokuNeighbors, highlightSelectedNumber)
                    }
                    sudoku.itemCount + sudoku.size + 1 -> { //selected hint
                        if (!sudoku[position].given) {
                            sudoku.setHint(position)
                        }
                    }
                }
            }
            in sudoku.itemCount until sudoku.itemCount + sudoku.size -> { //number button is selected
                when (newSelected) {
                    null -> { //selected nothing
                        selectButton(null, highlightSelectedNumber)
                    }
                    selected -> { //selected same button
                        selectButton(null, highlightSelectedNumber)
                    }
                    in 0 until sudoku.itemCount -> { //selected field
                        val number = selected!! - sudoku.itemCount + 1
                        sudoku.move(newSelected, selected!! - sudoku.itemCount + 1, notesEnabled)
                        if (highlightSelectedNumber) gameAdapter.highlightNumber(number)
                    }
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> { //selected button
                        selectButton(newSelected - sudoku.itemCount, highlightSelectedNumber)
                    }
                    else -> { //selected nothing
                        selectButton(null, highlightSelectedNumber)
                    }
                }
            }
            sudoku.itemCount + sudoku.size -> { // delete button is selected
                when (newSelected) {
                    null -> { //selected nothing
                        selectButton(null, highlightSelectedNumber)
                    }
                    selected -> { //selected same button
                        selectButton(null, highlightSelectedNumber)
                    }
                    in 0 until sudoku.itemCount -> { //selected field
                        sudoku.move(newSelected, null, notesEnabled)
                    }
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> { //selected button(not delete)
                        selectButton(newSelected - sudoku.itemCount, highlightSelectedNumber)
                    }
                    else -> { //selected nothing
                        selectButton(null, highlightSelectedNumber)
                    }
                }
            }
            sudoku.itemCount + sudoku.size + 1 -> { // hint button is selected
                when (newSelected) {
                    null -> { //selected nothing
                        selectButton(null, highlightSelectedNumber)
                    }
                    selected -> { //selected same button
                        selectButton(null, highlightSelectedNumber)
                    }
                    in 0 until sudoku.itemCount -> { //selected field
                        if (!sudoku[newSelected].given) {
                            sudoku.setHint(newSelected)
                        }
                    }
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 1 -> { //selected button(not hint)
                        selectButton(newSelected - sudoku.itemCount, highlightSelectedNumber)
                    }
                    else -> { //selected nothing
                        selectButton(null, highlightSelectedNumber)
                    }
                }
            }
        }
    }

    private fun checkAnyNumberCompleted(position: Position?) {
        lifecycleScope.launch {
            val completedNumbers = sudoku.getCompletedNumbers()
            completedNumbers.forEach { pair ->
                if (pair.second) {
                    selectButtons[pair.first - 1].isEnabled = false
                    selectButtons[pair.first - 1].setTextColor(getColor(R.color.oui_secondary_text_color))
                    if (position != null && sudoku[position].value == pair.first) {

                        if (selected in sudoku.itemCount until sudoku.itemCount + sudoku.size) selectNextButton(
                            pair.first, completedNumbers
                        )
                        else {
                            selected = null
                            val userSettings = getUserSettings()
                            gameAdapter.selectFieldView(null, userSettings.highlightRegional, userSettings.highlightNumber)
                            selectButton(null, userSettings.highlightNumber)
                        }
                    }
                } else {
                    selectButtons[pair.first - 1].isEnabled = true
                    selectButtons[pair.first - 1].setTextColor(getColor(dev.oneuiproject.oneui.R.color.oui_primary_text_color))
                }
            }
        }
    }

    private suspend fun selectNextButton(n: Int, completedNumbers: List<Pair<Int, Boolean>>) {
        var number = n
        while (completedNumbers[number - 1].second) {
            number++
            if (number > completedNumbers.size) number = 1 //wrap around
            if (number == n) { //all numbers are completed
                selected = null
                selectButton(null, getUserSettings().highlightNumber)
                return
            }
        }
        selected = sudoku.itemCount + number - 1
        selectButton(number - 1, getUserSettings().highlightNumber)
    }

    private suspend fun checkRowColumnBlockCompleted(position: Position) {
        if (getUserSettings().animationsEnabled) {
            if (sudoku.isRowCompleted(position.row)) animateRow(position)
            if (sudoku.isColumnCompleted(position.column)) animateColumn(position)
            if (sudoku.isBlockCompleted(position.block)) animateBlock(position)
        }
    }

    private fun animateRow(position: Position) {
        lifecycleScope.launch {
            gameAdapter.fieldViews.filter { it?.position?.row == position.row && it.position.column <= position.column }.reversed()
                .forEach { it?.flash(animationDuration) }
        }
        lifecycleScope.launch {
            gameAdapter.fieldViews.filter { it?.position?.row == position.row && it.position.column >= position.column }
                .forEach { it?.flash(animationDuration) }
        }
    }

    private fun animateColumn(position: Position) {
        lifecycleScope.launch {
            gameAdapter.fieldViews.filter { it?.position?.column == position.column && it.position.row <= position.row }.reversed()
                .forEach { it?.flash(animationDuration) }
        }
        lifecycleScope.launch {
            gameAdapter.fieldViews.filter { it?.position?.column == position.column && it.position.row >= position.row }
                .forEach { it?.flash(animationDuration) }
        }
    }

    private fun animateBlock(position: Position) {
        lifecycleScope.launch {
            gameAdapter.fieldViews.filter { it?.position?.block == position.block && it.position.index <= position.index }.reversed()
                .forEach { it?.flash(animationDuration) }
        }
        lifecycleScope.launch {
            gameAdapter.fieldViews.filter { it?.position?.block == position.block && it.position.index >= position.index }
                .forEach { it?.flash(animationDuration) }
        }
    }

    private fun animateSudoku(position: Position): Job {
        lifecycleScope.launch {
            gameAdapter.fieldViews.filter { it?.position?.index!! <= position.index }.reversed().forEach { it?.flash(20) }
        }
        return lifecycleScope.launch {
            gameAdapter.fieldViews.filter { it?.position?.index!! >= position.index }.forEach { it?.flash(20) }
        }
    }

    private fun selectButton(i: Int?, highlightSelectedNumber: Boolean) {
        for (button in selectButtons) {
            button.backgroundTintList = ColorStateList.valueOf(resources.getColor(android.R.color.transparent, theme))
        }
        if (i != null) {
            selectButtons[i].backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.primary_color, theme))
            if (highlightSelectedNumber && i in 0 until sudoku.size) gameAdapter.highlightNumber(i + 1)
            selected = sudoku.itemCount + i
        } else {
            selected = null
            if (highlightSelectedNumber) gameAdapter.highlightNumber(null)
        }
    }

    private fun toggleOrSetNoteButton(enabled: Boolean? = null) {
        notesEnabled = enabled ?: !notesEnabled
        binding.noteButton.backgroundTintList = ColorStateList.valueOf(
            if (notesEnabled) resources.getColor(R.color.primary_color, theme)
            else resources.getColor(android.R.color.transparent, theme)
        )
    }

    @Suppress("unused_parameter")
    fun resumeGame(view: View? = null) {
        lifecycleScope.launch {
            binding.resumeButtonLayout.visibility = View.GONE
            binding.gameLayout.visibility = View.VISIBLE
            val errorLimit = getUserSettings().errorLimit
            if (sudoku.completed) {
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, false)
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_pause_play, false)
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_reset, sudoku.modeLevel == Sudoku.MODE_NORMAL)
            } else if (errorLimit != 0 && sudoku.errorsMade >= errorLimit) {
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, false)
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_pause_play, false)
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_reset, true)
            } else if (sudoku.modeLevel == Sudoku.MODE_DAILY && sudoku.created.toLocalDate() != LocalDate.now()) {
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, false)
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_pause_play, false)
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_reset, false)
            } else {
                sudoku.startTimer()
                val itemPausePlay: MenuItem = toolbarMenu.findItem(R.id.menu_pause_play)
                itemPausePlay.icon = getDrawable(dev.oneuiproject.oneui.R.drawable.ic_oui_control_pause)
                itemPausePlay.title = getString(R.string.pause)
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, sudoku.history.isNotEmpty())
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_pause_play, true)
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_reset, false)
            }
        }
    }

    private fun pauseGame() {
        if (sudoku.completed) return
        binding.gameLayout.visibility = View.GONE
        binding.resumeButtonLayout.visibility = View.VISIBLE
        sudoku.stopTimer()
        val itemPausePlay: MenuItem = toolbarMenu.findItem(R.id.menu_pause_play)
        itemPausePlay.icon = getDrawable(dev.oneuiproject.oneui.R.drawable.ic_oui_control_play)
        itemPausePlay.title = getString(R.string.resume)
        toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, false)
        toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_pause_play, true)
        toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_reset, false)
    }

    private fun toggleGameResumed() {
        if (sudoku.resumed) pauseGame() else resumeGame()
    }

}