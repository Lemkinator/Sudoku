package de.lemke.sudoku.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.*
import de.lemke.sudoku.domain.model.Sudoku
import dev.oneuiproject.oneui.layout.DrawerLayout
import javax.inject.Inject

@AndroidEntryPoint
class SudokuActivity : AppCompatActivity(R.layout.activity_main) {
    lateinit var currentSudoku: Sudoku
    lateinit var gameRecycler: RecyclerView
    lateinit var gameAdapter: SudokuViewAdapter
    lateinit var drawerLayout: DrawerLayout
    lateinit var toolbarMenu: Menu
    lateinit var resumeButtonLayout: LinearLayout
    lateinit var playLayout: LinearLayout

    companion object {
        var refreshView = false
    }

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sudoku)
        drawerLayout = findViewById(R.id.drawer_layout_sudoku)
        drawerLayout.setNavigationButtonIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_oui_back_24))
        drawerLayout.setNavigationButtonOnClickListener { onBackPressed() }
        drawerLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        gameRecycler = findViewById(R.id.game_recycler)
        //TODO get currentSudoku
        gameAdapter = SudokuViewAdapter(this, currentSudoku)
        gameRecycler.adapter = gameAdapter


        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {

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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.sudoku_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_undo -> {}
            R.id.menu_pause_play -> {}
        }
        return true
    }

    @Suppress("unused_parameter")
    fun resumeGameTimer(view: View? = null) {
        resumeButtonLayout.visibility = View.GONE
        if (!currentSudoku.completed) {
            currentSudoku.startTimer(1500)
            val itemPausePlay: MenuItem = toolbarMenu.findItem(R.id.menu_pause_play)
            itemPausePlay.icon = getDrawable(R.drawable.ic_oui_control_pause)
            itemPausePlay.title = getString(R.string.pause)
            toolbarMenu.findItem(R.id.menu_undo).isEnabled = currentSudoku.history.isNotEmpty()
        }
        playLayout.visibility = View.VISIBLE
    }

    private fun pauseGameTimer() {
        if (currentSudoku.completed) return
        playLayout.visibility = View.GONE
        currentSudoku.stopTimer()
        val itemPausePlay: MenuItem = toolbarMenu.findItem(R.id.menu_pause_play)
        itemPausePlay.icon = getDrawable(R.drawable.ic_oui_control_play)
        itemPausePlay.title = getString(R.string.resume)
        toolbarMenu.findItem(R.id.menu_undo).isEnabled = false
        resumeButtonLayout.visibility = View.VISIBLE
    }

    private fun toggleGameTimer() {
        if (currentSudoku.resumed) pauseGameTimer() else resumeGameTimer()
    }

}