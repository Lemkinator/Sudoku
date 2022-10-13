package de.lemke.sudoku.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.GetSudokuUseCase
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.SaveSudokuUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import de.lemke.sudoku.domain.model.GameListener
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.utils.internal.ReflectUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.math.abs


@AndroidEntryPoint
class SudokuActivity : AppCompatActivity(R.layout.activity_main) {
    lateinit var sudoku: Sudoku
    lateinit var gameAdapter: SudokuViewAdapter
    lateinit var toolbarMenu: Menu
    private lateinit var gameRecycler: RecyclerView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var loadingDialog: ProgressDialog
    private lateinit var resumeButtonLayout: LinearLayout
    private lateinit var gameLayout: LinearLayout
    private lateinit var noteButton: AppCompatButton
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
    lateinit var saveSudoku: SaveSudokuUseCase

    @SuppressLint("MissingInflatedId", "RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sudoku)
        loadingDialog = ProgressDialog(this)
        loadingDialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
        loadingDialog.setCancelable(false)
        loadingDialog.show()

        selectButtons.add(findViewById(R.id.number_button_1))
        selectButtons.add(findViewById(R.id.number_button_2))
        selectButtons.add(findViewById(R.id.number_button_3))
        selectButtons.add(findViewById(R.id.number_button_4))
        selectButtons.add(findViewById(R.id.number_button_5))
        selectButtons.add(findViewById(R.id.number_button_6))
        selectButtons.add(findViewById(R.id.number_button_7))
        selectButtons.add(findViewById(R.id.number_button_8))
        selectButtons.add(findViewById(R.id.number_button_9))
        selectButtons.add(findViewById(R.id.delete_button))
        selectButtons.add(findViewById(R.id.hint_button))

        findViewById<AppCompatButton>(R.id.auto_notes_button).setOnClickListener {
            AlertDialog.Builder(this@SudokuActivity)
                .setTitle(getString(R.string.use_auto_notes))
                .setMessage(getString(R.string.use_auto_notes_message))
                .setNegativeButton(getString(R.string.sesl_cancel), null)
                .setPositiveButton(R.string.ok) { _, _ -> sudoku.autoNotes() }
                .show()
        }
        noteButton = findViewById(R.id.note_button)
        noteButton.setOnClickListener { toggleOrSetNoteButton() }
        drawerLayout = findViewById(R.id.drawer_layout_sudoku)
        drawerLayout.setNavigationButtonIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_oui_back_24))
        drawerLayout.setNavigationButtonOnClickListener { onBackPressed() }
        drawerLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        gameRecycler = findViewById(R.id.game_recycler)
        gameLayout = findViewById(R.id.game_layout)
        resumeButtonLayout = findViewById(R.id.resume_button_layout)
        drawerLayout.appBarLayout.addOnOffsetChangedListener { layout: AppBarLayout, verticalOffset: Int ->
            val totalScrollRange = layout.totalScrollRange
            val inputMethodWindowVisibleHeight = ReflectUtils.genericInvokeMethod(
                InputMethodManager::class.java,
                getSystemService(INPUT_METHOD_SERVICE),
                "getInputMethodWindowVisibleHeight"
            ) as Int
            if (totalScrollRange != 0) resumeButtonLayout.translationY = (abs(verticalOffset) - totalScrollRange).toFloat() / 2.0f
            else resumeButtonLayout.translationY = (abs(verticalOffset) - inputMethodWindowVisibleHeight).toFloat() / 2.0f
        }
        drawerLayout.toolbar.inflateMenu(R.menu.sudoku_menu)
        toolbarMenu = drawerLayout.toolbar.menu
        setSupportActionBar(null)

        val id = intent.getStringExtra("sudokuId")
        if (id == null) {
            finish()
            return
        }
        lifecycleScope.launch { initSudoku(SudokuId(id)) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                lifecycleScope.launch {
                    saveSudoku(sudoku)
                    finish()
                }
            }
        })
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
        drawerLayout.setTitle(getString(R.string.app_name) + " (" + sudoku.difficulty.getLocalString(resources) + ")")
        setSubtitle()
        for (index in selectButtons.indices) {
            selectButtons[index].setOnClickListener {
                lifecycleScope.launch {
                    lifecycleScope.launch {
                        val errorLimit = getUserSettings().errorLimit
                        if (errorLimit != 0 && sudoku.errorsMade >= errorLimit) errorLimitDialog(errorLimit)
                        else select(sudoku.itemCount + index)
                    }
                }
            }
        }
        gameRecycler.layoutManager = GridLayoutManager(this@SudokuActivity, sudoku.size)
        gameAdapter = SudokuViewAdapter(this@SudokuActivity, sudoku)
        gameRecycler.adapter = gameAdapter
        gameRecycler.seslSetFillBottomEnabled(true)
        gameRecycler.seslSetLastRoundedCorner(true)
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
                    saveSudoku(sudoku)
                }
            }

            override fun onCompleted(position: Position) {
                animateSudoku(position).invokeOnCompletion {
                    lifecycleScope.launch {
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
                        AlertDialog.Builder(this@SudokuActivity)
                            .setTitle(R.string.completed_title)
                            .setMessage(completedMessage)
                            .setPositiveButton(R.string.shareResult) { _, _ ->
                                val sendIntent = Intent(Intent.ACTION_SEND)
                                sendIntent.type = "text/plain"
                                sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.textShare) + completedMessage)
                                sendIntent.putExtra(Intent.EXTRA_TITLE, getString(R.string.shareResult))
                                sendIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                startActivity(Intent.createChooser(sendIntent, "Share Via"))
                            }
                            .setNeutralButton(R.string.ok, null)
                            .show()
                    }
                }
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, false)
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_pause_play, false)
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_reset, true)
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

            override fun onTimeChanged(time: String?) {
                lifecycleScope.launch { setSubtitle() }
            }
        }


        resumeGame()
        loadingDialog.dismiss()
        if (sudoku.completed) {
            toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, false)
            toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_pause_play, false)
            toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_reset, true)
        }
        sudoku.updated = LocalDateTime.now()
        checkAnyNumberCompleted(null)
    }

    private fun errorLimitDialog(errorLimit: Int) {
        toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, false)
        toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_pause_play, false)
        toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_reset, true)
        AlertDialog.Builder(this@SudokuActivity)
            .setTitle(R.string.gameover)
            .setMessage(getString(R.string.error_limit_reached, errorLimit))
            .setPositiveButton(R.string.restart) { _, _ ->
                restartGame()
            }
            .setNeutralButton(R.string.ok, null)
            .show()
    }

    private fun restartGame() {
        lifecycleScope.launch {
            sudoku.reset()
            saveSudoku(sudoku)
            toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, false)
            toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_reset, false)
            toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_pause_play, true)
            initSudoku(sudoku.id)
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun setSubtitle() {
        lifecycleScope.launch {
            val errorLimit = getUserSettings().errorLimit
            val subtitle = getString(R.string.current_time, sudoku.timeString) + " | " +
                    getString(R.string.current_progress, sudoku.progress) + " | " +
                    if (errorLimit == 0) {
                        getString(R.string.current_errors, sudoku.errorsMade)
                    } else {
                        getString(R.string.current_errors_with_limit, sudoku.errorsMade, errorLimit)
                    } + " | " +
                    getString(R.string.current_hints, sudoku.hintsUsed)
            drawerLayout.setExpandedSubtitle(subtitle)
            drawerLayout.setCollapsedSubtitle(subtitle)
        }
    }

    private suspend fun select(newSelected: Int?) {
        if (drawerLayout.isExpanded) drawerLayout.setExpanded(false, true)
        val highlightSudokuNeighbors = getUserSettings().highlightRegional
        val highlightSelectedNumber = getUserSettings().highlightNumber
        Log.d("test", "selected: $selected, newSelected: $newSelected")
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
                    selectButtons[pair.first - 1].setTextColor(getColor(R.color.number_button_disabled_text_color))
                    if (position != null && sudoku[position].value == pair.first) {

                        if (selected in sudoku.itemCount until sudoku.itemCount + sudoku.size) selectNextButton(
                            pair.first,
                            completedNumbers
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
            gameAdapter.fieldViews.filter { it?.position?.index!! <= position.index }.reversed()
                .forEach { it?.flash(20) }
        }
        return lifecycleScope.launch {
            gameAdapter.fieldViews.filter { it?.position?.index!! >= position.index }
                .forEach { it?.flash(20) }
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
        noteButton.backgroundTintList = ColorStateList.valueOf(
            if (notesEnabled) resources.getColor(R.color.primary_color, theme)
            else resources.getColor(android.R.color.transparent, theme)
        )
    }

    @Suppress("unused_parameter")
    fun resumeGame(view: View? = null) {
        lifecycleScope.launch {
            resumeButtonLayout.visibility = View.GONE
            gameLayout.visibility = View.VISIBLE
            val errorLimit = getUserSettings().errorLimit
            if (!sudoku.completed && !(errorLimit != 0 && sudoku.errorsMade >= errorLimit)) {
                sudoku.startTimer()
                val itemPausePlay: MenuItem = toolbarMenu.findItem(R.id.menu_pause_play)
                itemPausePlay.icon = getDrawable(R.drawable.ic_oui_control_pause)
                itemPausePlay.title = getString(R.string.pause)
                toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, sudoku.history.isNotEmpty())
            }
        }
    }

    private fun pauseGame() {
        if (sudoku.completed) return
        gameLayout.visibility = View.GONE
        resumeButtonLayout.visibility = View.VISIBLE
        sudoku.stopTimer()
        val itemPausePlay: MenuItem = toolbarMenu.findItem(R.id.menu_pause_play)
        itemPausePlay.icon = getDrawable(R.drawable.ic_oui_control_play)
        itemPausePlay.title = getString(R.string.resume)
        toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_undo, false)
    }

    private fun toggleGameResumed() {
        if (sudoku.resumed) pauseGame() else resumeGame()
    }

}