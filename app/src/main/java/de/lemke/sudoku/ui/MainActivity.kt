package de.lemke.sudoku.ui

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.games.AuthenticationResult
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.tasks.Task
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
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
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main) {
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
        PlayGamesSdk.initialize(applicationContext)
        initDrawer()
        initTabs()
        initFragments()
        initOnBackPressed()
        lifecycleScope.launch { checkImportedSudoku() }
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
                Toast.makeText(this@MainActivity, getString(R.string.error_import_failed), Toast.LENGTH_LONG).show()
            }
            dialog.dismiss()
        }
    }

    private fun initDrawer() {
        val achievementsOption = findViewById<LinearLayout>(R.id.draweritem_achievements)
        val leaderboardsOption = findViewById<LinearLayout>(R.id.draweritem_leaderboards)
        val aboutAppOption = findViewById<LinearLayout>(R.id.draweritem_about_app)
        val aboutMeOption = findViewById<LinearLayout>(R.id.draweritem_about_me)
        val settingsOption = findViewById<LinearLayout>(R.id.draweritem_settings)
        val gamesSignInClient = PlayGames.getGamesSignInClient(this)
        achievementsOption.setOnClickListener {
            gamesSignInClient.isAuthenticated.addOnCompleteListener { isAuthenticatedTask: Task<AuthenticationResult> ->
                val isAuthenticated = isAuthenticatedTask.isSuccessful && isAuthenticatedTask.result.isAuthenticated
                if (isAuthenticated) {
                    PlayGames.getAchievementsClient(this)
                        .achievementsIntent
                        .addOnSuccessListener { intent -> startActivityForResult(intent, 9003) }
                } else gamesSignInClient.signIn()
            }
        }
        leaderboardsOption.setOnClickListener {
            gamesSignInClient.isAuthenticated.addOnCompleteListener { isAuthenticatedTask: Task<AuthenticationResult> ->
                val isAuthenticated = isAuthenticatedTask.isSuccessful && isAuthenticatedTask.result.isAuthenticated
                if (isAuthenticated) {
                    PlayGames.getLeaderboardsClient(this)
                        .allLeaderboardsIntent
                        .addOnSuccessListener { intent -> startActivityForResult(intent, 9004) }
                } else gamesSignInClient.signIn()
            }
        }
        aboutAppOption.setOnClickListener {
            startActivity(Intent(this@MainActivity, AboutActivity::class.java))
            binding.drawerLayoutMain.setDrawerOpen(false, true)
        }
        aboutMeOption.setOnClickListener {
            startActivity(Intent(this@MainActivity, AboutMeActivity::class.java))
            binding.drawerLayoutMain.setDrawerOpen(false, true)
        }
        settingsOption.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            binding.drawerLayoutMain.setDrawerOpen(false, true)
        }
        binding.drawerLayoutMain.setDrawerButtonIcon(getDrawable(dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline))
        binding.drawerLayoutMain.setDrawerButtonOnClickListener { startActivity(Intent().setClass(this@MainActivity, AboutActivity::class.java)) }
        binding.drawerLayoutMain.setDrawerButtonTooltip(getText(R.string.about_app))
        AppUpdateManagerFactory.create(this).appUpdateInfo.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE)
                binding.drawerLayoutMain.setButtonBadges(ToolbarLayout.N_BADGE, DrawerLayout.N_BADGE)
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
                        getString(R.string.app_name) -> binding.drawerLayoutMain.setExpanded(!binding.drawerLayoutMain.isExpanded, true)
                        getString(R.string.history) -> {
                            val historyRecyclerView: RecyclerView = findViewById(R.id.sudokuHistoryList)
                            if (historyRecyclerView.canScrollVertically(-1)) historyRecyclerView.smoothScrollToPosition(0)
                            else binding.drawerLayoutMain.setExpanded(!binding.drawerLayoutMain.isExpanded, true)
                        }
                        getString(R.string.statistics) -> {
                            val statisticsRecyclerView: RecyclerView = findViewById(R.id.statistics_list_recycler)
                            if (binding.drawerLayoutMain.isExpanded) binding.drawerLayoutMain.setExpanded(false, true)
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
                    when {
                        binding.drawerLayoutMain.findViewById<androidx.drawerlayout.widget.DrawerLayout>(dev.oneuiproject.oneui.design.R.id.drawerlayout_drawer)
                            .isDrawerOpen(
                                binding.drawerLayoutMain.findViewById<LinearLayout>(dev.oneuiproject.oneui.design.R.id.drawerlayout_drawer_content)
                            ) -> {
                            binding.drawerLayoutMain.setDrawerOpen(false, true)
                        }
                        selectedPosition != 1 -> onTabItemSelected(1)
                        else -> {
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
            }
        })
    }
}
