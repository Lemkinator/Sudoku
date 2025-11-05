package de.lemke.sudoku.ui

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.R.anim.slide_in_left
import android.R.anim.slide_out_right
import android.content.Intent
import android.graphics.Typeface
import android.graphics.Typeface.NORMAL
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityOptionsCompat.makeCustomAnimation
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.games.AuthenticationResult
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Task
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.checkAppStartAndHandleOOBE
import de.lemke.commonutils.configureCommonUtilsSplashScreen
import de.lemke.commonutils.onNavigationSingleClick
import de.lemke.commonutils.openURL
import de.lemke.commonutils.prepareActivityTransformationFrom
import de.lemke.commonutils.setupCommonUtilsAboutActivity
import de.lemke.commonutils.setupCommonUtilsAboutMeActivity
import de.lemke.commonutils.setupCommonUtilsOOBEActivity
import de.lemke.commonutils.setupHeaderAndNavRail
import de.lemke.commonutils.toast
import de.lemke.commonutils.transformToActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.sudoku.BuildConfig
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityMainBinding
import de.lemke.sudoku.databinding.DialogStatisticsFilterBinding
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.DIFFICULTY_ALL
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.DIFFICULTY_EASY
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.DIFFICULTY_EXPERT
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.DIFFICULTY_HARD
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.DIFFICULTY_MEDIUM
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.DIFFICULTY_VERY_EASY
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.SIZE_16X16
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.SIZE_4X4
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.SIZE_9X9
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.SIZE_ALL
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.TYPE_ALL
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.TYPE_DAILY
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.TYPE_LEVEL
import de.lemke.sudoku.domain.GetAllSudokusUseCase.Companion.TYPE_NORMAL
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.ImportSudokuUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.UpdatePlayGamesUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.fragments.TabHistory
import de.lemke.sudoku.ui.fragments.TabStatistics
import de.lemke.sudoku.ui.fragments.TabSudoku
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.dialog.ProgressDialog.ProgressStyle.CIRCLE
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.oneuiproject.oneui.design.R as designR


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val fragmentsInstance: List<Fragment> = listOf(TabHistory(), TabSudoku(), TabStatistics())
    private var selectedPosition = 0
    private var isUIReady = false
    private val playGamesActivityResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(StartActivityForResult()) {}

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var importSudoku: ImportSudokuUseCase

    @Inject
    lateinit var sendDailyNotification: SendDailyNotificationUseCase

    @Inject
    lateinit var updatePlayGames: UpdatePlayGamesUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        prepareActivityTransformationFrom()
        super.onCreate(savedInstanceState)
        if (SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureCommonUtilsSplashScreen(splashScreen, binding.root) { !isUIReady }
        setupCommonUtilsOOBEActivity(setAcceptedTosVersion = false, nextActivity = IntroActivity::class.java)
        if (!checkAppStartAndHandleOOBE(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) openMain()
    }

    private fun openMain() {
        setupCommonUtilsActivities()
        initDrawer()
        initTabs()
        initFragments()
        NotificationManagerCompat.from(this).cancelAll()
        lifecycleScope.launch {
            isUIReady = true
            checkImportedSudokuOrNotificationClicked()
            sendDailyNotification.setDailySudokuNotification(enable = getUserSettings().dailySudokuNotificationEnabled)
            updatePlayGames(this@MainActivity)
        }
    }

    private suspend fun checkImportedSudokuOrNotificationClicked() {
        if (intent != null && intent.data != null) {
            val dialog = ProgressDialog(this)
            dialog.setProgressStyle(CIRCLE)
            dialog.setCancelable(false)
            dialog.show()
            val sudoku = importSudoku(intent.data)
            if (sudoku != null) {
                findViewById<AppCompatButton?>(R.id.newGameButton)?.transformToActivity(
                    Intent(this, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
                ) ?: startActivity(Intent(this, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value))
            } else {
                toast(R.string.error_import_failed)
            }
            dialog.dismiss()
        }
        if (intent.getBooleanExtra("openDailySudoku", false)) {
            (findViewById(R.id.dailyButton) ?: findViewById<AppCompatButton?>(R.id.dailyAvailableButton))?.transformToActivity(
                Intent(this, DailySudokuActivity::class.java).putExtra("openDailySudoku", true),
                "DailySudokuActivityTransition" // transitionNames should be unique within the view hierarchy
            ) ?: startActivity(Intent(this, DailySudokuActivity::class.java).putExtra("openDailySudoku", true))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?) = menuInflater.inflate(R.menu.menu_filter, menu).let { true }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.setGroupVisible(R.id.menu_group_filter, selectedPosition == 2)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_item_filter -> showStatisticsFilterDialog().let { true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun setupCommonUtilsActivities() {
        val bib = getString(R.string.sudoku_lib)
        val license = getString(R.string.sudoku_lib_license)
        val text = getString(R.string.app_description) + "\n" + getString(R.string.sudoku_lib_license_text, bib, license)
        val optionalText = SpannableString(text)
        optionalText.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openURL(getString(R.string.sudoku_lib_github_link))
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.typeface = Typeface.create("sans-serif-medium", NORMAL)
                }
            },
            text.indexOf(bib), text.indexOf(bib) + bib.length,
            SPAN_EXCLUSIVE_EXCLUSIVE
        )
        optionalText.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openURL(getString(R.string.sudoku_lib_license_github_link))
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.typeface = Typeface.create("sans-serif-medium", NORMAL)
                }
            },
            text.indexOf(license), text.indexOf(license) + license.length,
            SPAN_EXCLUSIVE_EXCLUSIVE
        )
        setupCommonUtilsAboutActivity(appVersion = BuildConfig.VERSION_NAME, optionalText = optionalText)
        setupCommonUtilsAboutMeActivity(
            onShareApp = { activity -> PlayGames.getAchievementsClient(activity).unlock(getString(R.string.achievement_share_app)) },
        )
    }

    private fun initDrawer() {
        val gamesSignInClient = PlayGames.getGamesSignInClient(this)
        binding.navigationView.onNavigationSingleClick { item ->
            when (item.itemId) {
                R.id.achievements_dest ->
                    gamesSignInClient.isAuthenticated.addOnCompleteListener { isAuthenticatedTask: Task<AuthenticationResult> ->
                        if (isAuthenticatedTask.isSuccessful && isAuthenticatedTask.result.isAuthenticated) openAchievements()
                        else signInPlayGames(gamesSignInClient) { openAchievements() }
                    }

                R.id.leaderboards_dest ->
                    gamesSignInClient.isAuthenticated.addOnCompleteListener { isAuthenticatedTask: Task<AuthenticationResult> ->
                        if (isAuthenticatedTask.isSuccessful && isAuthenticatedTask.result.isAuthenticated) openLeaderboards()
                        else signInPlayGames(gamesSignInClient) { openLeaderboards() }
                    }

                R.id.about_app_dest -> findViewById<View>(R.id.about_app_dest).transformToActivity(CommonUtilsAboutActivity::class.java)
                R.id.about_me_dest -> findViewById<View>(R.id.about_me_dest).transformToActivity(CommonUtilsAboutMeActivity::class.java)
                R.id.settings_dest -> findViewById<View>(R.id.settings_dest).transformToActivity(SettingsActivity::class.java)
                else -> return@onNavigationSingleClick false
            }
            true
        }
        binding.drawerLayout.apply {
            setupHeaderAndNavRail(getString(R.string.about_app))
            //setupNavigation(binding.bottomTab, binding.navigationHost.getFragment())
        }
    }

    private fun signInPlayGames(gamesSignInClient: GamesSignInClient, onSuccess: () -> Unit = {}) {
        gamesSignInClient.signIn().addOnCompleteListener { signInTask: Task<AuthenticationResult> ->
            if (signInTask.isSuccessful && signInTask.result.isAuthenticated) onSuccess()
            else toast(R.string.error_sign_in_failed)
        }
    }

    private fun openLeaderboards() = PlayGames.getLeaderboardsClient(this).allLeaderboardsIntent.addOnSuccessListener { intent ->
        playGamesActivityResultLauncher.launch(intent, makeCustomAnimation(this, slide_in_left, slide_out_right))
    }

    private fun openAchievements() = PlayGames.getAchievementsClient(this).achievementsIntent.addOnSuccessListener { intent ->
        playGamesActivityResultLauncher.launch(intent, makeCustomAnimation(this, slide_in_left, slide_out_right))
    }

    private fun initTabs() {
        binding.bottomTab.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = onTabItemSelected(tab.position, tab)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                try {
                    when (tab.position) {
                        0 -> {
                            val historyRecyclerView: RecyclerView = findViewById(R.id.sudokuHistoryList)
                            if (historyRecyclerView.canScrollVertically(-1)) historyRecyclerView.smoothScrollToPosition(0)
                            else binding.drawerLayout.setExpanded(!binding.drawerLayout.isExpanded, true)
                        }

                        1 -> binding.drawerLayout.setExpanded(!binding.drawerLayout.isExpanded, true)

                        2 -> {
                            val statisticsRecyclerView: RecyclerView = findViewById(R.id.statisticsListRecycler)
                            if (binding.drawerLayout.isExpanded) binding.drawerLayout.setExpanded(expanded = false, animate = true)
                            else if (statisticsRecyclerView.canScrollVertically(-1)) statisticsRecyclerView.smoothScrollToPosition(0)
                            else showStatisticsFilterDialog()
                        }
                    }
                } catch (_: Exception) { //no required functionality -> ignore errors
                }
            }
        })
    }

    private fun showStatisticsFilterDialog() {
        lifecycleScope.launch {
            val dialogBinding = DialogStatisticsFilterBinding.inflate(layoutInflater).apply { initDialog() }
            AlertDialog.Builder(this@MainActivity).apply {
                setTitle(getString(R.string.statistics_filter))
                setView(dialogBinding.root)
                setNegativeButton(getString(designR.string.oui_des_common_cancel)) { d, _ -> d.dismiss() }
                setPositiveButton(getString(designR.string.oui_des_common_apply)) { _, _ -> updateFilterSettings(dialogBinding) }
                show()
            }
        }
    }

    private suspend fun DialogStatisticsFilterBinding.initDialog() {
        getUserSettings().let {
            filterNormal.isChecked = it.filterFlags and TYPE_NORMAL != 0 || it.filterFlags and TYPE_ALL != 0
            filterDaily.isChecked = it.filterFlags and TYPE_DAILY != 0 || it.filterFlags and TYPE_ALL != 0
            filterLevel.isChecked = it.filterFlags and TYPE_LEVEL != 0 || it.filterFlags and TYPE_ALL != 0
            filterSize4.isChecked = it.filterFlags and SIZE_4X4 != 0 || it.filterFlags and SIZE_ALL != 0
            filterSize9.isChecked = it.filterFlags and SIZE_9X9 != 0 || it.filterFlags and SIZE_ALL != 0
            filterSize16.isChecked = it.filterFlags and SIZE_16X16 != 0 || it.filterFlags and SIZE_ALL != 0
            filterDifficultyVeryEasy.isChecked =
                it.filterFlags and DIFFICULTY_VERY_EASY != 0 || it.filterFlags and DIFFICULTY_ALL != 0
            filterDifficultyEasy.isChecked = it.filterFlags and DIFFICULTY_EASY != 0 || it.filterFlags and DIFFICULTY_ALL != 0
            filterDifficultyMedium.isChecked = it.filterFlags and DIFFICULTY_MEDIUM != 0 || it.filterFlags and DIFFICULTY_ALL != 0
            filterDifficultyHard.isChecked = it.filterFlags and DIFFICULTY_HARD != 0 || it.filterFlags and DIFFICULTY_ALL != 0
            filterDifficultyExpert.isChecked = it.filterFlags and DIFFICULTY_EXPERT != 0 || it.filterFlags and DIFFICULTY_ALL != 0
        }
    }

    private fun updateFilterSettings(dialogBinding: DialogStatisticsFilterBinding) {
        var flags = 0
        if (dialogBinding.filterNormal.isChecked) flags = flags or TYPE_NORMAL
        if (dialogBinding.filterDaily.isChecked) flags = flags or TYPE_DAILY
        if (dialogBinding.filterLevel.isChecked) flags = flags or TYPE_LEVEL
        if (flags and TYPE_NORMAL != 0 && flags and TYPE_DAILY != 0 && flags and TYPE_LEVEL != 0) flags = flags or TYPE_ALL
        if (dialogBinding.filterSize4.isChecked) flags = flags or SIZE_4X4
        if (dialogBinding.filterSize9.isChecked) flags = flags or SIZE_9X9
        if (dialogBinding.filterSize16.isChecked) flags = flags or SIZE_16X16
        if (flags and SIZE_4X4 != 0 && flags and SIZE_9X9 != 0 && flags and SIZE_16X16 != 0) flags = flags or SIZE_ALL
        if (dialogBinding.filterDifficultyVeryEasy.isChecked) flags = flags or DIFFICULTY_VERY_EASY
        if (dialogBinding.filterDifficultyEasy.isChecked) flags = flags or DIFFICULTY_EASY
        if (dialogBinding.filterDifficultyMedium.isChecked) flags = flags or DIFFICULTY_MEDIUM
        if (dialogBinding.filterDifficultyHard.isChecked) flags = flags or DIFFICULTY_HARD
        if (dialogBinding.filterDifficultyExpert.isChecked) flags = flags or DIFFICULTY_EXPERT
        if (flags and DIFFICULTY_VERY_EASY != 0 && flags and DIFFICULTY_EASY != 0 && flags and DIFFICULTY_MEDIUM != 0
            && flags and DIFFICULTY_HARD != 0 && flags and DIFFICULTY_EXPERT != 0
        ) {
            flags = flags or DIFFICULTY_ALL
        }
        lifecycleScope.launch { updateUserSettings { it.copy(filterFlags = flags) } }
    }

    private fun initFragments() {
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        for (fragment in fragmentsInstance) transaction.add(R.id.fragmentContainer, fragment)
        transaction.commitNowAllowingStateLoss()
        onTabItemSelected(1)
    }

    fun onTabItemSelected(position: Int, tab: TabLayout.Tab? = null) {
        val newFragment: Fragment = fragmentsInstance[position]
        if (selectedPosition != position) {
            selectedPosition = position
            val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
            for (fragment in supportFragmentManager.fragments) transaction.hide(fragment)
            transaction.show(newFragment).commitNowAllowingStateLoss()
            val newTab = tab ?: binding.bottomTab.getTabAt(position)
            if (newTab?.isSelected == false) newTab.select()
        }
        newFragment.onResume()
        invalidateOptionsMenu()
    }
}
