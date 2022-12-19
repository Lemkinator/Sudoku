package de.lemke.sudoku.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityMainBinding
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.ImportSudokuUseCase
import de.lemke.sudoku.ui.dialog.StatisticsFilterDialog
import de.lemke.sudoku.ui.fragments.MainActivityTabHistory
import de.lemke.sudoku.ui.fragments.MainActivityTabStatistics
import de.lemke.sudoku.ui.fragments.MainActivityTabSudoku
import dev.oneuiproject.oneui.dialog.ProgressDialog
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main) {
    companion object {
        var refreshView = false
    }

    private lateinit var binding: ActivityMainBinding
    private val fragmentsInstance: List<Fragment> = listOf(MainActivityTabHistory(), MainActivityTabSudoku(), MainActivityTabStatistics())
    private var selectedPosition = 0
    private var time: Long = 0

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var importSudoku: ImportSudokuUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        time = System.currentTimeMillis()
        initTabs()
        initFragments()
        initOnBackPressed()
        lifecycleScope.launch { checkImportedSudoku() }
    }

    override fun onResume() {
        super.onResume()
        if (refreshView) {
            refreshView = false
            recreate()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        MenuCompat.setGroupDividerEnabled(menu, true)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuitem_app_info -> {
                startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                return true
            }
            R.id.menuitem_settings -> {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private suspend fun checkImportedSudoku() {
        val intent = intent
        if (intent != null && intent.data != null) {
            val dialog = ProgressDialog(this)
            dialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
            dialog.setCancelable(false)
            dialog.show()
            val sudoku = importSudoku(intent.data)
            if (sudoku != null) {
                startActivity(Intent(this, SudokuActivity::class.java).putExtra("sudokuId", sudoku.id.value))
            } else {
                Toast.makeText(this@MainActivity, "Sudoku konnte nicht importiert werden", Toast.LENGTH_LONG).show()
            }
            dialog.dismiss()
        }
    }

    private fun initTabs() {
        binding.mainMarginsTabLayout.tabMode = TabLayout.SESL_MODE_FIXED_AUTO
        binding.mainMarginsTabLayout.addTab(binding.mainMarginsTabLayout.newTab().setText(getString(R.string.history)))
        binding.mainMarginsTabLayout.addTab(binding.mainMarginsTabLayout.newTab().setText(getString(R.string.app_name)))
        binding.mainMarginsTabLayout.addTab(binding.mainMarginsTabLayout.newTab().setText(getString(R.string.statistics)))
        binding.mainMarginsTabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                onTabItemSelected(tab.position, tab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                try {
                    when (tab.text) {
                        getString(R.string.app_name) -> binding.mainToolbarlayout.setExpanded(!binding.mainToolbarlayout.isExpanded, true)
                        getString(R.string.history) -> {
                            val historyRecyclerView: RecyclerView = findViewById(R.id.sudokuHistoryList)
                            if (historyRecyclerView.canScrollVertically(-1)) historyRecyclerView.smoothScrollToPosition(0)
                            else binding.mainToolbarlayout.setExpanded(!binding.mainToolbarlayout.isExpanded, true)
                        }
                        getString(R.string.statistics) -> {
                            val statisticsRecyclerView: RecyclerView = findViewById(R.id.statistics_list_recycler)
                            if (binding.mainToolbarlayout.isExpanded) binding.mainToolbarlayout.setExpanded(false, true)
                            else if (statisticsRecyclerView.canScrollVertically(-1)) statisticsRecyclerView.smoothScrollToPosition(0)
                            else StatisticsFilterDialog { lifecycleScope.launch { fragmentsInstance[2].onResume() } }.show(
                                supportFragmentManager,
                                "StatisticsFilterDialog"
                            )
                        }
                    }
                } catch (_: Exception) { //no required functionality -> ignore errors
                }
            }
        })
    }

    private fun initFragments() {
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        for (fragment in fragmentsInstance) transaction.add(R.id.fragment_container, fragment)
        transaction.commit()
        supportFragmentManager.executePendingTransactions()
        onTabItemSelected(1)
    }

    fun onTabItemSelected(position: Int, tab: TabLayout.Tab? = null) {
        val newFragment: Fragment = fragmentsInstance[position]
        if (selectedPosition != position) {
            selectedPosition = position
            val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
            for (fragment in supportFragmentManager.fragments) {
                transaction.hide(fragment)
            }
            transaction.show(newFragment).commit()
            supportFragmentManager.executePendingTransactions()
            val newTab = tab ?: binding.mainMarginsTabLayout.getTabAt(position)
            if (newTab?.isSelected == false) newTab.select()
        }
        newFragment.onResume()
    }

    private fun initOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                lifecycleScope.launch {
                    if (selectedPosition != 1) onTabItemSelected(1)
                    else {
                        if (getUserSettings().confirmExit) {
                            if (System.currentTimeMillis() - time < 3000) finishAffinity()
                            else {
                                Toast.makeText(this@MainActivity, resources.getString(R.string.press_again_to_exit), Toast.LENGTH_SHORT)
                                    .show()
                                time = System.currentTimeMillis()
                            }
                        } else finishAffinity()
                    }
                }
            }
        })
    }
}
