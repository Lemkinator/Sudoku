package de.lemke.sudoku.ui

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.games.PlayGames
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivitySudokuBinding
import de.lemke.sudoku.domain.*
import de.lemke.sudoku.domain.model.*
import de.lemke.sudoku.ui.utils.SudokuViewAdapter
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.utils.DialogUtils
import dev.oneuiproject.oneui.utils.internal.ReflectUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.math.abs


@AndroidEntryPoint
class SudokuActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySudokuBinding
    private lateinit var loadingDialog: ProgressDialog
    private lateinit var toolbarMenu: Menu
    private var colorPrimary: Int = 0
    lateinit var sudoku: Sudoku
    lateinit var gameAdapter: SudokuViewAdapter
    private val sudokuButtons: MutableList<AppCompatButton> = mutableListOf()
    private var notesEnabled = false
    private var selected: Int? = null

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var getSudoku: GetSudokuUseCase

    @Inject
    lateinit var generateSudoku: GenerateSudokuUseCase

    @Inject
    lateinit var generateSudokuLevel: GenerateSudokuLevelUseCase

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
            finish()
            return
        }

        loadingDialog = ProgressDialog(this)
        loadingDialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
        loadingDialog.setCancelable(false)
        loadingDialog.show()

        val typedValue = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        colorPrimary = typedValue.data

        binding.sudokuToolbarLayout.toolbar.inflateMenu(R.menu.sudoku_menu)
        toolbarMenu = binding.sudokuToolbarLayout.toolbar.menu
        setSupportActionBar(null)
        lifecycleScope.launch {
            initSudoku(SudokuId(id))
            if (getUserSettings().keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        binding.noteButton.setOnClickListener { toggleOrSetNoteButton() }
        binding.sudokuToolbarLayout.setNavigationButtonOnClickListener { finish() }
        binding.sudokuToolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        binding.sudokuToolbarLayout.appBarLayout.addOnOffsetChangedListener { layout: AppBarLayout, verticalOffset: Int ->
            val totalScrollRange = layout.totalScrollRange
            val inputMethodWindowVisibleHeight = ReflectUtils.genericInvokeMethod(
                InputMethodManager::class.java, getSystemService(INPUT_METHOD_SERVICE), "getInputMethodWindowVisibleHeight"
            ) as Int
            if (totalScrollRange != 0) {
                binding.resumeButtonLayout.translationY = (abs(verticalOffset) - totalScrollRange) / 2f
            } else {
                binding.resumeButtonLayout.translationY = (abs(verticalOffset) - inputMethodWindowVisibleHeight) / 2f
            }
            /*val width = binding.sudokuLayout.measuredWidth
            val height = binding.sudokuLayout.measuredHeight
            val gameSize: Int
            if (totalScrollRange != 0) {
                gameSize = min(width, height - totalScrollRange - verticalOffset)
                binding.resumeButtonLayout.translationY = (abs(verticalOffset) - totalScrollRange) / 2f
            } else {
                gameSize = min(width, height - inputMethodWindowVisibleHeight - verticalOffset)
                binding.resumeButtonLayout.translationY = (abs(verticalOffset) - inputMethodWindowVisibleHeight) / 2f
            }
            val params: ViewGroup.LayoutParams = binding.roundedGameRecycler.layoutParams
            params.width = gameSize
            params.height = gameSize
            binding.roundedGameRecycler.translationX = max(0, width - gameSize) / 2f
            binding.roundedGameRecycler.layoutParams = params*/
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
            R.id.menu_pause_play -> if (sudoku.resumed) pauseGame() else resumeGame()
            R.id.menu_reset -> restartGame()
            R.id.menu_share -> shareDialog()
        }
        return true
    }

    private fun shareDialog() {
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
                    R.id.radio_button_text -> {
                        val sendIntent = Intent(Intent.ACTION_SEND)
                        sendIntent.type = "text/plain"
                        sendIntent.putExtra(Intent.EXTRA_TEXT, sudoku.getLocalStatisticsStringShare(resources))
                        sendIntent.putExtra(Intent.EXTRA_TITLE, getString(R.string.share_sudoku))
                        sendIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_sudoku)))
                    }
                    R.id.radio_button_initial -> shareGame(sudoku.getInitialSudoku())
                    R.id.radio_button_current -> shareGame(sudoku.copy(modeLevel = Sudoku.MODE_NORMAL))
                }
                dialog.dismiss()
            }
        }
    }

    private suspend fun shareGame(sudoku: Sudoku) {
        PlayGames.getAchievementsClient(this@SudokuActivity).unlock(getString(R.string.achievement_share_sudoku))
        val uri = shareSudoku(sudoku)
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "application/sudoku" //octet-stream"
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        //for (ri in packageManager.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY))
        //    grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
    }

    private fun setToolbarMenuItemsVisible(pausePlay: Boolean = false, reset: Boolean = false) {
        toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_pause_play, pausePlay)
        toolbarMenu.setGroupVisible(R.id.sudoku_menu_group_reset, reset)
    }

    private suspend fun initSudoku(id: SudokuId) {
        val nullableSudoku = getSudoku(id)
        if (nullableSudoku == null) finish()
        else initSudoku(nullableSudoku)
    }

    private fun initSudoku(sudoku: Sudoku) {
        this.sudoku = sudoku
        setTitle()
        setSubtitle()
        refreshHintButton()
        binding.gameRecycler.layoutManager = GridLayoutManager(this@SudokuActivity, sudoku.size)
        gameAdapter = SudokuViewAdapter(this@SudokuActivity, sudoku)
        binding.gameRecycler.adapter = gameAdapter
        binding.gameRecycler.seslSetFillBottomEnabled(true)
        binding.gameRecycler.seslSetLastRoundedCorner(true)
        sudoku.gameListener = SudokuGameListener()
        initSudokuButtons()
        if (sudoku.isDailySudoku && sudoku.created.toLocalDate() != LocalDate.now()) {
            setToolbarMenuItemsVisible()
            binding.gameButtons.visibility = View.GONE
        } else resumeGame()
        checkAnyNumberCompleted(null)
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

    @Suppress("unused_parameter")
    fun resumeGame(view: View? = null) {
        lifecycleScope.launch {
            binding.resumeButtonLayout.visibility = View.GONE
            binding.gameLayout.visibility = View.VISIBLE
            when {
                sudoku.completed -> {
                    setToolbarMenuItemsVisible(reset = sudoku.isNormalSudoku)
                    binding.gameButtons.visibility = View.GONE
                }
                else -> {
                    sudoku.startTimer()
                    val itemPausePlay: MenuItem = toolbarMenu.findItem(R.id.menu_pause_play)
                    itemPausePlay.icon = getDrawable(dev.oneuiproject.oneui.R.drawable.ic_oui_control_pause)
                    itemPausePlay.title = getString(R.string.pause)
                    setToolbarMenuItemsVisible(pausePlay = true)
                    binding.gameButtons.visibility = View.VISIBLE
                }
            }
            checkErrorLimit()
        }
    }

    private fun pauseGame() {
        sudoku.stopTimer()
        if (sudoku.completed) return
        binding.gameLayout.visibility = View.GONE
        binding.gameButtons.visibility = View.GONE
        binding.resumeButtonLayout.visibility = View.VISIBLE
        val itemPausePlay: MenuItem = toolbarMenu.findItem(R.id.menu_pause_play)
        itemPausePlay.icon = getDrawable(dev.oneuiproject.oneui.R.drawable.ic_oui_control_play)
        itemPausePlay.title = getString(R.string.resume)
        setToolbarMenuItemsVisible(pausePlay = true)
    }

    inner class SudokuGameListener : GameListener {
        override fun onFieldClicked(position: Position) {
            lifecycleScope.launch { select(position.index) }
        }

        override fun onFieldChanged(position: Position) {
            gameAdapter.updateFieldView(position.index)
            checkAnyNumberCompleted(sudoku[position].value)
            lifecycleScope.launch {
                checkRowColumnBlockCompleted(position)
                saveSudoku(sudoku)
            }
        }

        override fun onCompleted(position: Position) {
            lifecycleScope.launch {
                if (getUserSettings().animationsEnabled) animate(position, animateSudoku = true)?.invokeOnCompletion { onSudokuCompleted() }
                else onSudokuCompleted()
            }
        }

        override fun onError() {
            lifecycleScope.launch { checkErrorLimit() }
        }

        override fun onTimeChanged() {
            lifecycleScope.launch {
                setSubtitle()
            }
        }
    }

    private fun onSudokuCompleted() {
        setSubtitle()
        val dialog = AlertDialog.Builder(this@SudokuActivity)
            .setTitle(R.string.completed_title)
            .setMessage(sudoku.getLocalStatisticsString(resources))
        dialog.setNeutralButton(R.string.ok, null)
        if (sudoku.isSudokuLevel) dialog.setPositiveButton(R.string.next_level) { _, _ ->
            lifecycleScope.launch {
                loadingDialog.show()
                val nextSudokuLevel = generateSudokuLevel(level = sudoku.modeLevel + 1)
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
        lifecycleScope.launch {
            dialog.show()
            updatePlayGames(this@SudokuActivity, sudoku)
        }
        setToolbarMenuItemsVisible(reset = sudoku.isNormalSudoku)
        binding.gameButtons.visibility = View.GONE
    }

    private suspend fun checkErrorLimit(): Boolean {
        val errorLimit = when {
            sudoku.isDailySudoku -> Sudoku.MODE_DAILY_ERROR_LIMIT
            sudoku.isSudokuLevel -> Sudoku.MODE_LEVEL_ERROR_LIMIT
            else -> getUserSettings().errorLimit
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
        lifecycleScope.launch {
            sudoku.reset()
            saveSudoku(sudoku)
            initSudoku(sudoku)
            selectButton(null, false)
        }
    }

    private fun setTitle() {
        when {
            sudoku.isNormalSudoku -> {
                binding.sudokuToolbarLayout.setTitle(getString(R.string.app_name) + " (" + sudoku.difficulty.getLocalString(resources) + ")")
            }
            sudoku.isDailySudoku -> {
                binding.sudokuToolbarLayout.setTitle(
                    getString(R.string.app_name) + " (" + sudoku.created.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)) + ")"
                )
                //binding.hintButton.visibility = View.GONE
                //binding.autoNotesButton.visibility = View.GONE
            }
            sudoku.isSudokuLevel -> {
                binding.sudokuToolbarLayout.setTitle(getString(R.string.app_name) + " (Level " + sudoku.modeLevel + ")")
                //binding.hintButton.visibility = View.GONE
                //binding.autoNotesButton.visibility = View.GONE
            }
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun setSubtitle() {
        lifecycleScope.launch {
            val errorLimit = getUserSettings().errorLimit
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
    }

    private fun checkAnyNumberCompleted(currentNumber: Int?) {
        lifecycleScope.launch {
            val completedNumbers = sudoku.getCompletedNumbers()
            completedNumbers.forEach { pair ->
                if (pair.second) {
                    sudokuButtons[pair.first - 1].isEnabled = false
                    sudokuButtons[pair.first - 1].setTextColor(getColor(R.color.secondary_text_icon_color))
                    if (currentNumber == pair.first && selected in sudoku.itemCount until sudoku.itemCount + sudoku.size)
                        selectNextButton(pair.first, completedNumbers)
                } else {
                    sudokuButtons[pair.first - 1].isEnabled = true
                    sudokuButtons[pair.first - 1].setTextColor(getColor(R.color.primary_text_icon_color))
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
        if (getUserSettings().animationsEnabled) animate(
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
                    if (highlightSelectedNumber) gameAdapter.highlightNumber(i + 1)
                }
            }
            selected = sudoku.itemCount + i
        } else {
            selected = null
            if (highlightSelectedNumber) gameAdapter.highlightNumber(null)
        }
    }

    private fun toggleOrSetNoteButton(enabled: Boolean? = null) {
        notesEnabled = enabled ?: !notesEnabled
        binding.noteButton.backgroundTintList = ColorStateList.valueOf(
            if (notesEnabled) colorPrimary
            else resources.getColor(android.R.color.transparent, theme)
        )
    }

    private fun refreshHintButton() {
        binding.hintButton.visibility = if (sudoku.isHintAvailable) View.VISIBLE else View.GONE
        binding.hintButton.text = getString(R.string.hint, sudoku.availableHints)
    }

    private suspend fun select(newSelected: Int?) {
        if (checkErrorLimit()) return
        if (binding.sudokuToolbarLayout.isExpanded) binding.sudokuToolbarLayout.setExpanded(false, true)
        val highlightSudokuNeighbors = getUserSettings().highlightRegional
        val highlightSelectedNumber = getUserSettings().highlightNumber
        when (selected) {
            null -> {//nothing is selected
                when (newSelected) {
                    //selected nothing
                    null -> {}
                    //selected field
                    in 0 until sudoku.itemCount -> {
                        gameAdapter.selectFieldView(newSelected, highlightSudokuNeighbors, highlightSelectedNumber)
                        selected = newSelected
                    }
                    //selected button
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 ->
                        selectButton(newSelected - sudoku.itemCount, highlightSelectedNumber)
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
                        if (sudoku[position].value == null) sudoku.move(position, newSelected - sudoku.itemCount + 1, notesEnabled)
                        selected = null
                    }
                    //selected delete
                    sudoku.itemCount + sudoku.size -> {
                        sudoku.move(position, null, notesEnabled)
                        selected = null
                    }
                    //selected hint
                    sudoku.itemCount + sudoku.size + 1 -> {
                        if (sudoku[position].value == null) sudoku.setHint(position)
                        selected = null
                        refreshHintButton()
                    }
                }
                gameAdapter.selectFieldView(selected, highlightSudokuNeighbors, highlightSelectedNumber)
            }
            in sudoku.itemCount until sudoku.itemCount + sudoku.size -> { //number button is selected
                when (newSelected) {
                    //selected nothing
                    null -> selectButton(null, highlightSelectedNumber)
                    //selected same button
                    selected -> selectButton(null, highlightSelectedNumber)
                    //selected field
                    in 0 until sudoku.itemCount -> {
                        val number = selected!! - sudoku.itemCount + 1
                        sudoku.move(newSelected, selected!! - sudoku.itemCount + 1, notesEnabled)
                        if (highlightSelectedNumber) gameAdapter.highlightNumber(number)
                    }
                    //selected button
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> {
                        gameAdapter.selectFieldView(null, highlightSudokuNeighbors, highlightSelectedNumber)
                        selectButton(newSelected - sudoku.itemCount, highlightSelectedNumber)
                    }
                    //selected nothing
                    else -> selectButton(null, highlightSelectedNumber)
                }
            }
            sudoku.itemCount + sudoku.size -> { // delete button is selected
                when (newSelected) {
                    //selected nothing
                    null -> selectButton(null, highlightSelectedNumber)
                    //selected same button
                    selected -> selectButton(null, highlightSelectedNumber)
                    //selected field
                    in 0 until sudoku.itemCount -> sudoku.move(newSelected, null, notesEnabled)
                    //selected button(not delete)
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 ->
                        selectButton(newSelected - sudoku.itemCount, highlightSelectedNumber)
                    //selected nothing
                    else -> selectButton(null, highlightSelectedNumber)
                }
            }
            sudoku.itemCount + sudoku.size + 1 -> { // hint button is selected
                when (newSelected) {
                    //selected nothing
                    null -> selectButton(null, highlightSelectedNumber)
                    //selected same button
                    selected -> selectButton(null, highlightSelectedNumber)
                    //selected field
                    in 0 until sudoku.itemCount -> {
                        if (sudoku[newSelected].value == null) sudoku.setHint(newSelected)
                        if (!sudoku.isHintAvailable) selected = null
                        refreshHintButton()
                    }
                    //selected button(not hint)
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 1 ->
                        selectButton(newSelected - sudoku.itemCount, highlightSelectedNumber)
                    //selected nothing
                    else -> selectButton(null, highlightSelectedNumber)
                }
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_1 -> {
                lifecycleScope.launch { select(sudoku.itemCount) }
                true
            }
            KeyEvent.KEYCODE_2 -> {
                lifecycleScope.launch { select(sudoku.itemCount + 1) }
                true
            }
            KeyEvent.KEYCODE_3 -> {
                lifecycleScope.launch { select(sudoku.itemCount + 2) }
                true
            }
            KeyEvent.KEYCODE_4 -> {
                lifecycleScope.launch { select(sudoku.itemCount + 3) }
                true
            }
            KeyEvent.KEYCODE_5 -> {
                if (sudoku.size > 4) {
                    lifecycleScope.launch { select(sudoku.itemCount + 4) }
                    true
                } else false
            }
            KeyEvent.KEYCODE_6 -> {
                if (sudoku.size > 4) {
                    lifecycleScope.launch { select(sudoku.itemCount + 5) }
                    true
                } else false
            }
            KeyEvent.KEYCODE_7 -> {
                if (sudoku.size > 4) {
                    lifecycleScope.launch { select(sudoku.itemCount + 6) }
                    true
                } else false
            }
            KeyEvent.KEYCODE_8 -> {
                if (sudoku.size > 4) {
                    lifecycleScope.launch { select(sudoku.itemCount + 7) }
                    true
                } else false
            }
            KeyEvent.KEYCODE_9 -> {
                if (sudoku.size > 4) {
                    lifecycleScope.launch { select(sudoku.itemCount + 8) }
                    true
                } else false
            }
            KeyEvent.KEYCODE_A -> {
                if (sudoku.size > 9) {
                    lifecycleScope.launch { select(sudoku.itemCount + 9) }
                    true
                } else false
            }
            KeyEvent.KEYCODE_B -> {
                if (sudoku.size > 9) {
                    lifecycleScope.launch { select(sudoku.itemCount + 10) }
                    true
                } else false
            }
            KeyEvent.KEYCODE_C -> {
                if (sudoku.size > 9) {
                    lifecycleScope.launch { select(sudoku.itemCount + 11) }
                    true
                } else false
            }
            KeyEvent.KEYCODE_D -> {
                if (sudoku.size > 9) {
                    lifecycleScope.launch { select(sudoku.itemCount + 12) }
                } else lifecycleScope.launch { select(sudoku.itemCount + sudoku.size) }
                true
            }
            KeyEvent.KEYCODE_E -> {
                if (sudoku.size > 9) {
                    lifecycleScope.launch { select(sudoku.itemCount + 13) }
                    true
                } else false
            }
            KeyEvent.KEYCODE_F -> {
                if (sudoku.size > 9) {
                    lifecycleScope.launch { select(sudoku.itemCount + 14) }
                    true
                } else false
            }
            KeyEvent.KEYCODE_G -> {
                if (sudoku.size > 9) {
                    lifecycleScope.launch { select(sudoku.itemCount + 15) }
                    true
                } else false
            }
            KeyEvent.KEYCODE_DEL -> {
                lifecycleScope.launch { select(sudoku.itemCount + sudoku.size) }
                true
            }
            KeyEvent.KEYCODE_H -> {
                lifecycleScope.launch { select(sudoku.itemCount + sudoku.size + 1) }
                true
            }
            KeyEvent.KEYCODE_N -> {
                toggleOrSetNoteButton()
                true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                lifecycleScope.launch { select(null) }
                true
            }

            else -> super.onKeyUp(keyCode, event)
        }
    }
}