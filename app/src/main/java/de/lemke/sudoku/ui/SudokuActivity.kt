package de.lemke.sudoku.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
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
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.utils.internal.ReflectUtils
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs


@AndroidEntryPoint
class SudokuActivity : AppCompatActivity(R.layout.activity_main) {
    lateinit var sudoku: Sudoku
    lateinit var gameRecycler: RecyclerView
    lateinit var gameAdapter: SudokuViewAdapter
    lateinit var drawerLayout: DrawerLayout
    lateinit var toolbarMenu: Menu
    lateinit var loadingDialog: ProgressDialog
    lateinit var resumeButtonLayout: LinearLayout
    lateinit var gameLayout: LinearLayout
    lateinit var noteButton: AppCompatButton
    private val selectButtons: MutableList<AppCompatButton> = mutableListOf()
    private var notesEnabled = false

    var selected: Int? = null

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

        noteButton = findViewById(R.id.note_button)
        noteButton.setOnClickListener { toggleOrSetNote() }
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
            if (totalScrollRange != 0) {
                resumeButtonLayout.translationY = (abs(verticalOffset) - totalScrollRange).toFloat() / 2.0f
            } else {
                resumeButtonLayout.translationY = (abs(verticalOffset) - inputMethodWindowVisibleHeight).toFloat() / 2.0f
            }
        }
        drawerLayout.toolbar.inflateMenu(R.menu.sudoku_menu)
        toolbarMenu = drawerLayout.toolbar.menu
        setSupportActionBar(null)

        val id = intent.getStringExtra("sudokuId")
        if (id == null) {
            finish()
            return
        }
        val sudokuId = SudokuId(id)
        lifecycleScope.launch {
            sudoku = getSudoku(sudokuId)
            drawerLayout.setTitle(
                getString(R.string.app_name) + " (" + resources.getStringArray(R.array.difficuilty)[sudoku.difficulty.ordinal] + ")"
            )
            setSubtitle(
                getString(R.string.current_time, sudoku.getTimeString()) + " | " + getString(R.string.current_errors, sudoku.errorsMade)
            )

            for (index in selectButtons.indices) {
                selectButtons[index].setOnClickListener {
                    select(sudoku.itemCount + index)
                }
            }

            //recycler
            gameRecycler.layoutManager = GridLayoutManager(this@SudokuActivity, sudoku.size)
            gameAdapter = SudokuViewAdapter(this@SudokuActivity, sudoku)
            gameRecycler.adapter = gameAdapter
            gameRecycler.seslSetFillBottomEnabled(true)
            gameRecycler.seslSetLastRoundedCorner(true)

            sudoku.gameListener = object : GameListener {
                override fun onHistoryChange(length: Int) {
                    toolbarMenu.findItem(R.id.menu_undo).isEnabled = sudoku.history.isNotEmpty()
                }

                override fun onCompleted() {
                    //TODO
                }

                override fun onTimeChanged(time: String?) {
                    lifecycleScope.launch {
                        setSubtitle(getString(R.string.current_time, time) + " | " + getString(R.string.current_errors, sudoku.errorsMade))
                    }
                }
            }
            resumeGame()
            loadingDialog.dismiss()
        }



        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
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
        pauseGame()
        lifecycleScope.launch {
            saveSudoku(sudoku)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_undo -> sudoku.revertLastChange(gameAdapter)
            R.id.menu_pause_play -> toggleGameResumed()
        }
        return true
    }

    private fun setSubtitle(subtitle: CharSequence) {
        drawerLayout.setExpandedSubtitle(subtitle)
        drawerLayout.setCollapsedSubtitle(subtitle)
    }

    private fun select(newSelected: Int?) {
        when (selected) {
            null -> {//nothing is selected
                when (newSelected) {
                    null -> {} //selected nothing
                    in 0 until sudoku.itemCount -> { //selected field
                        gameAdapter.selectFieldView(newSelected)
                    }
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> { //selected button
                        selectButton(newSelected - sudoku.itemCount)
                    }
                    else -> {} //selected nothing
                }
            }
            in 0 until sudoku.itemCount -> { //field is selected
                when (newSelected) {
                    null -> { //selected nothing
                        gameAdapter.selectFieldView(null)
                    }
                    selected -> { //selected same field
                        selected = null
                        gameAdapter.selectFieldView(null)
                        return
                    }
                    in 0 until sudoku.itemCount -> { //selected field
                        gameAdapter.selectFieldView(newSelected)
                    }
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size -> { //selected number
                        if (notesEnabled) {
                            sudoku[selected!!].toggleNote(newSelected - sudoku.itemCount + 1)
                        } else {
                            sudoku[selected!!].value = newSelected - sudoku.itemCount + 1
                        }
                        gameAdapter.updateFieldView(selected!!)
                    }
                    sudoku.itemCount + sudoku.size -> { //selected delete
                        if (notesEnabled) {
                            sudoku[selected!!].notes.clear()
                        } else {
                            sudoku[selected!!].value = null
                        }
                        gameAdapter.updateFieldView(selected!!)
                    }
                    sudoku.itemCount + sudoku.size + 1 -> { //selected hint
                        sudoku[selected!!].setHint()
                        gameAdapter.updateFieldView(selected!!)
                    }
                }
            }
            in sudoku.itemCount until sudoku.itemCount + sudoku.size -> { //number button is selected
                when (newSelected) {
                    null -> { //selected nothing
                        selectButton(null)
                    }
                    selected -> { //selected same button
                        selected = null
                        selectButton(null)
                        return
                    }
                    in 0 until sudoku.itemCount -> { //selected field
                        if (notesEnabled) {
                            sudoku[selected!!].toggleNote(selected!! - sudoku.itemCount + 1)
                        } else {
                            sudoku[selected!!].value = selected!! - sudoku.itemCount + 1
                        }
                    }
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> { //selected button
                        selectButton(newSelected - sudoku.itemCount)
                    }
                }
            }
            sudoku.itemCount + sudoku.size -> { // delete button is selected
                when (newSelected) {
                    null -> { //selected nothing
                        selectButton(null)
                    }
                    selected -> { //selected same button
                        selected = null
                        selectButton(null)
                        return
                    }
                    in 0 until sudoku.itemCount -> { //selected field
                        if (notesEnabled) {
                            sudoku[newSelected].notes.clear()
                        } else {
                            sudoku[newSelected].value = null
                        }
                        gameAdapter.updateFieldView(newSelected)
                    }
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 2 -> { //selected button(not delete)
                        selectButton(newSelected - sudoku.itemCount)
                    }
                    else -> { //selected nothing
                        selectButton(null)
                    }
                }
            }
            sudoku.itemCount + sudoku.size + 1 -> { // hint button is selected
                when (newSelected) {
                    null -> { //selected nothing
                        selectButton(null)
                    }
                    selected -> { //selected same button
                        selected = null
                        selectButton(null)
                        return
                    }
                    in 0 until sudoku.itemCount -> { //selected field
                        sudoku[newSelected].setHint()
                        gameAdapter.updateFieldView(newSelected)
                    }
                    in sudoku.itemCount until sudoku.itemCount + sudoku.size + 1 -> { //selected button(not hint)
                        selectButton(newSelected - sudoku.itemCount)
                    }
                    else -> { //selected nothing
                        selectButton(null)
                    }
                }
            }
        }
        selected = newSelected
    }

    private fun selectButton(i: Int?) {
        for (button in selectButtons) {
            button.backgroundTintList = ColorStateList.valueOf(resources.getColor(android.R.color.transparent, theme))
        }
        if (i != null) {
            selectButtons[i].backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.primary_color, theme))
        }
    }

    private fun toggleOrSetNote(enabled: Boolean? = null) {
        notesEnabled = enabled ?: !notesEnabled
        noteButton.backgroundTintList = ColorStateList.valueOf(
            if (notesEnabled) resources.getColor(R.color.primary_color, theme)
            else resources.getColor(android.R.color.transparent, theme)
        )
    }

    @Suppress("unused_parameter")
    fun resumeGame(view: View? = null) {
        resumeButtonLayout.visibility = View.GONE
        if (!sudoku.completed) {
            sudoku.startTimer(1500)
            val itemPausePlay: MenuItem = toolbarMenu.findItem(R.id.menu_pause_play)
            itemPausePlay.icon = getDrawable(R.drawable.ic_oui_control_pause)
            itemPausePlay.title = getString(R.string.pause)
            toolbarMenu.findItem(R.id.menu_undo).isEnabled = sudoku.history.isNotEmpty()
        }
        gameLayout.visibility = View.VISIBLE
    }

    private fun pauseGame() {
        if (sudoku.completed) return
        gameLayout.visibility = View.GONE
        sudoku.stopTimer()
        val itemPausePlay: MenuItem = toolbarMenu.findItem(R.id.menu_pause_play)
        itemPausePlay.icon = getDrawable(R.drawable.ic_oui_control_play)
        itemPausePlay.title = getString(R.string.resume)
        toolbarMenu.findItem(R.id.menu_undo).isEnabled = false
        resumeButtonLayout.visibility = View.VISIBLE
    }

    private fun toggleGameResumed() {
        if (sudoku.resumed) pauseGame() else resumeGame()
    }

}