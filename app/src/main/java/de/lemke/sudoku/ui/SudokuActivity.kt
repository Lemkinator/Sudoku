package de.lemke.sudoku.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
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