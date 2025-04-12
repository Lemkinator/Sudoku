package de.lemke.sudoku.ui

import android.R.anim.fade_in
import android.R.anim.fade_out
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
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
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.prepareActivityTransformationFrom
import de.lemke.commonutils.transformToActivity
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityMainBinding
import de.lemke.sudoku.domain.AppStart
import de.lemke.sudoku.domain.CheckAppStartUseCase
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.ImportSudokuUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.UpdatePlayGamesUseCase
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.fragments.MainActivityTabHistory
import de.lemke.sudoku.ui.fragments.MainActivityTabStatistics
import de.lemke.sudoku.ui.fragments.MainActivityTabSudoku
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.ktx.dpToPx
import dev.oneuiproject.oneui.ktx.onSingleClick
import dev.oneuiproject.oneui.layout.Badge
import dev.oneuiproject.oneui.layout.DrawerLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.sequences.forEach
import dev.oneuiproject.oneui.design.R as designR


@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerListView: LinearLayout
    private val drawerItemTitles: MutableList<TextView> = mutableListOf()
    private val fragmentsInstance: List<Fragment> = listOf(MainActivityTabHistory(), MainActivityTabSudoku(), MainActivityTabStatistics())
    private var selectedPosition = 0
    private var time: Long = 0
    private var isUIReady = false
    private val playGamesActivityResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var importSudoku: ImportSudokuUseCase

    @Inject
    lateinit var checkAppStart: CheckAppStartUseCase

    @Inject
    lateinit var sendDailyNotification: SendDailyNotificationUseCase

    @Inject
    lateinit var updatePlayGames: UpdatePlayGamesUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        time = System.currentTimeMillis()
        prepareActivityTransformationFrom()
        super.onCreate(savedInstanceState)
        if (SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        splashScreen.setKeepOnScreenCondition { !isUIReady }
        /*
        there is a bug in the new splash screen api, when using the onExitAnimationListener -> splash icon flickers
        therefore setting a manual delay in openMain()
        splashScreen.setOnExitAnimationListener { splash ->
            val splashAnimator: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(
                splash.view,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.2f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.2f)
            )
            splashAnimator.interpolator = AccelerateDecelerateInterpolator()
            splashAnimator.duration = 400L
            splashAnimator.doOnEnd { splash.remove() }
            val contentAnimator: ObjectAnimator = ObjectAnimator.ofPropertyValuesHolder(
                binding.root,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.2f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.2f, 1f)
            )
            contentAnimator.interpolator = AccelerateDecelerateInterpolator()
            contentAnimator.duration = 400L

            val remainingDuration = splash.iconAnimationDurationMillis - (System.currentTimeMillis() - splash.iconAnimationStartMillis)
                .coerceAtLeast(0L)
            lifecycleScope.launch {
                delay(remainingDuration)
                splashAnimator.start()
                contentAnimator.start()
            }
        }*/

        lifecycleScope.launch {
            when (checkAppStart()) {
                AppStart.FIRST_TIME -> openOOBE()
                AppStart.NORMAL -> checkTOS()
                AppStart.FIRST_TIME_VERSION -> checkTOS()
            }
        }
    }

    private suspend fun openOOBE() {
        //manually waiting for the animation to finish :/
        delay(800 - (System.currentTimeMillis() - time).coerceAtLeast(0L))
        startActivity(Intent(applicationContext, OOBEActivity::class.java))
        if (SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(fade_in, fade_out)
        }
        finishAfterTransition()
    }

    private suspend fun checkTOS() {
        if (!getUserSettings().tosAccepted) openOOBE()
        else openMain()
    }

    private fun openMain() {
        initDrawer()
        initTabs()
        initFragments()
        NotificationManagerCompat.from(this).cancelAll() // cancel all notifications
        lifecycleScope.launch {
            //manually waiting for the animation to finish :/
            delay(800 - (System.currentTimeMillis() - time).coerceAtLeast(0L))
            isUIReady = true
            checkImportedSudokuOrNotificationClicked()
            sendDailyNotification.setDailySudokuNotification(enable = getUserSettings().dailySudokuNotificationEnabled)
            updatePlayGames(this@MainActivity)
        }
    }

    private suspend fun checkImportedSudokuOrNotificationClicked() {
        if (intent != null && intent.data != null) {
            val dialog = ProgressDialog(this)
            dialog.setProgressStyle(ProgressDialog.STYLE_CIRCLE)
            dialog.setCancelable(false)
            dialog.show()
            val sudoku = importSudoku(intent.data)
            if (sudoku != null) {
                findViewById<AppCompatButton>(R.id.newGameButton).transformToActivity(
                    Intent(this, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
                )
            } else {
                Toast.makeText(this, getString(R.string.error_import_failed), Toast.LENGTH_LONG).show()
            }
            dialog.dismiss()
        }
        if (intent.getBooleanExtra("openDailySudoku", false)) {
            findViewById<AppCompatButton>(R.id.dailyButton).transformToActivity(
                Intent(this, DailySudokuActivity::class.java).putExtra("openDailySudoku", true),
                "DailySudokuActivityTransition" // transitionNames should be unique within the view hierarchy
            )
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

    private fun initDrawer() {
        drawerListView = findViewById(R.id.drawerListView)
        drawerItemTitles.apply {
            clear()
            add(findViewById(R.id.drawerItemAchievementsTitle))
            add(findViewById(R.id.drawerItemLeaderboardsTitle))
            add(findViewById(R.id.drawerItemAboutAppTitle))
            add(findViewById(R.id.drawerItemAboutMeTitle))
            add(findViewById(R.id.drawerItemSettingsTitle))
        }
        val gamesSignInClient = PlayGames.getGamesSignInClient(this)
        findViewById<LinearLayout>(R.id.drawerItemAchievements).onSingleClick {
            gamesSignInClient.isAuthenticated.addOnCompleteListener { isAuthenticatedTask: Task<AuthenticationResult> ->
                if (isAuthenticatedTask.isSuccessful && isAuthenticatedTask.result.isAuthenticated) openAchievements()
                else signInPlayGames(gamesSignInClient) { openAchievements() }
            }
        }
        findViewById<LinearLayout>(R.id.drawerItemLeaderboards).onSingleClick {
            gamesSignInClient.isAuthenticated.addOnCompleteListener { isAuthenticatedTask: Task<AuthenticationResult> ->
                if (isAuthenticatedTask.isSuccessful && isAuthenticatedTask.result.isAuthenticated) openLeaderboards()
                else signInPlayGames(gamesSignInClient) { openLeaderboards() }
            }
        }
        findViewById<LinearLayout>(R.id.drawerItemAboutApp).apply { onSingleClick { transformToActivity(AboutActivity::class.java) } }
        findViewById<LinearLayout>(R.id.drawerItemAboutMe).apply { onSingleClick { transformToActivity(AboutMeActivity::class.java) } }
        findViewById<LinearLayout>(R.id.drawerItemSettings).apply { onSingleClick { transformToActivity(SettingsActivity::class.java) } }
        binding.drawerLayout.apply {
            setHeaderButtonIcon(AppCompatResources.getDrawable(this@MainActivity, dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline))
            setHeaderButtonTooltip(getString(R.string.about_app))
            setHeaderButtonOnClickListener {
                findViewById<ImageButton>(designR.id.drawer_header_button).transformToActivity(AboutActivity::class.java)
            }
            setNavRailContentMinSideMargin(14)
            closeNavRailOnBack = true

            //setupNavRailFadeEffect
            if (isLargeScreenMode) {
                setDrawerStateListener {
                    when (it) {
                        DrawerLayout.DrawerState.OPEN -> offsetUpdaterJob?.cancel().also { updateOffset(1f) }
                        DrawerLayout.DrawerState.CLOSE -> offsetUpdaterJob?.cancel().also { updateOffset(0f) }
                        DrawerLayout.DrawerState.CLOSING, DrawerLayout.DrawerState.OPENING -> startOffsetUpdater()
                    }
                }
                //Set initial offset
                post { updateOffset(binding.drawerLayout.drawerOffset) }
            }
        }
        AppUpdateManagerFactory.create(this).appUpdateInfo.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE)
                binding.drawerLayout.setButtonBadges(Badge.DOT, Badge.DOT)
        }
    }

    private var offsetUpdaterJob: Job? = null
    private fun startOffsetUpdater() {
        //Ensure no duplicate job is running
        if (offsetUpdaterJob?.isActive == true) return
        offsetUpdaterJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updateOffset(binding.drawerLayout.drawerOffset)
                delay(50)
            }
        }
    }

    fun updateOffset(offset: Float) {
        drawerItemTitles.forEach { it.alpha = offset }
        drawerListView.children.forEach {
            it.post {
                if (offset == 0f) {
                    it.updateLayoutParams<MarginLayoutParams> {
                        width = if (it is LinearLayout) 52f.dpToPx(it.context.resources) //drawer item
                        else 25f.dpToPx(it.context.resources) //divider item
                    }
                } else if (it.width != MATCH_PARENT) {
                    it.updateLayoutParams<MarginLayoutParams> { width = MATCH_PARENT }
                }
            }
        }
    }

    private fun signInPlayGames(gamesSignInClient: GamesSignInClient, onSuccess: () -> Unit = {}) {
        gamesSignInClient.signIn().addOnCompleteListener { signInTask: Task<AuthenticationResult> ->
            if (signInTask.isSuccessful && signInTask.result.isAuthenticated) onSuccess()
            else Toast.makeText(this@MainActivity, getString(R.string.error_sign_in_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun openLeaderboards() {
        PlayGames.getLeaderboardsClient(this)
            .allLeaderboardsIntent
            .addOnSuccessListener { intent ->
                playGamesActivityResultLauncher.launch(
                    intent,
                    ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                )
            }
    }

    private fun openAchievements() {
        PlayGames.getAchievementsClient(this)
            .achievementsIntent
            .addOnSuccessListener { intent ->
                playGamesActivityResultLauncher.launch(
                    intent,
                    ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                )
            }
    }

    private fun initTabs() {
        binding.mainTabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
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
                            val statisticsRecyclerView: RecyclerView = findViewById(R.id.statistics_list_recycler)
                            if (binding.drawerLayout.isExpanded) binding.drawerLayout.setExpanded(false, true)
                            else if (statisticsRecyclerView.canScrollVertically(-1)) statisticsRecyclerView.smoothScrollToPosition(0)
                            else showStatisticsFilterDialog()
                        }
                    }
                } catch (_: Exception) { //no required functionality -> ignore errors
                }
            }
        })
    }

    private fun showStatisticsFilterDialog() = FilterBottomSheet().show(supportFragmentManager, "StatisticsFilterDialog")

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
            val newTab = tab ?: binding.mainTabLayout.getTabAt(position)
            if (newTab?.isSelected == false) newTab.select()
        }
        newFragment.onResume()
        invalidateOptionsMenu()
    }
}
