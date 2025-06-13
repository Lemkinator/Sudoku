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
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import de.lemke.commonutils.AboutActivity
import de.lemke.commonutils.AboutMeActivity
import de.lemke.commonutils.openURL
import de.lemke.commonutils.prepareActivityTransformationFrom
import de.lemke.commonutils.setup
import de.lemke.commonutils.setupCommonActivities
import de.lemke.commonutils.toast
import de.lemke.commonutils.transformToActivity
import de.lemke.sudoku.BuildConfig
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityMainBinding
import de.lemke.sudoku.domain.AppStart
import de.lemke.sudoku.domain.CheckAppStartUseCase
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.ImportSudokuUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.UpdatePlayGamesUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import de.lemke.sudoku.ui.fragments.MainActivityTabHistory
import de.lemke.sudoku.ui.fragments.MainActivityTabStatistics
import de.lemke.sudoku.ui.fragments.MainActivityTabSudoku
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.dialog.ProgressDialog.ProgressStyle.CIRCLE
import dev.oneuiproject.oneui.ktx.onSingleClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private lateinit var binding: ActivityMainBinding
    private val fragmentsInstance: List<Fragment> = listOf(MainActivityTabHistory(), MainActivityTabSudoku(), MainActivityTabStatistics())
    private var selectedPosition = 0
    private var time: Long = 0
    private var isUIReady = false
    private val playGamesActivityResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

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
        @Suppress("DEPRECATION")
        if (SDK_INT < 34) overridePendingTransition(fade_in, fade_out)
        finishAfterTransition()
    }

    private suspend fun checkTOS() {
        if (!getUserSettings().tosAccepted) openOOBE()
        else openMain()
    }

    private fun openMain() {
        setupCommonUtilsActivities()
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
            dialog.setProgressStyle(CIRCLE)
            dialog.setCancelable(false)
            dialog.show()
            val sudoku = importSudoku(intent.data)
            if (sudoku != null) {
                findViewById<AppCompatButton>(R.id.newGameButton).transformToActivity(
                    Intent(this, SudokuActivity::class.java).putExtra(KEY_SUDOKU_ID, sudoku.id.value)
                )
            } else {
                toast(R.string.error_import_failed)
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
        lifecycleScope.launch {
            setupCommonActivities(
                appName = getString(R.string.app_name),
                appVersion = BuildConfig.VERSION_NAME,
                optionalText = optionalText,
                email = getString(R.string.email),
                devModeEnabled = getUserSettings().devModeEnabled,
                onDevModeChanged = { newDevModeEnabled: Boolean -> updateUserSettings { it.copy(devModeEnabled = newDevModeEnabled) } },
                onShareApp = { activity -> PlayGames.getAchievementsClient(activity).unlock(getString(R.string.achievement_share_app)) },
                cantOpenURLMessage = getString(R.string.error_cant_open_url),
                noBrowserInstalledMessage = getString(R.string.no_browser_app_installed),
                noEmailAppInstalledText = getString(R.string.no_email_app_installed),
            )
        }
    }

    private fun initDrawer() {
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
        binding.drawerLayout.setup(
            getString(R.string.about_app),
            mutableListOf(
                findViewById(R.id.drawerItemAchievementsTitle),
                findViewById(R.id.drawerItemLeaderboardsTitle),
                findViewById(R.id.drawerItemAboutAppTitle),
                findViewById(R.id.drawerItemAboutMeTitle),
                findViewById(R.id.drawerItemSettingsTitle),
            ),
            findViewById(R.id.drawerListView)
        )
    }

    private fun signInPlayGames(gamesSignInClient: GamesSignInClient, onSuccess: () -> Unit = {}) {
        gamesSignInClient.signIn().addOnCompleteListener { signInTask: Task<AuthenticationResult> ->
            if (signInTask.isSuccessful && signInTask.result.isAuthenticated) onSuccess()
            else toast(R.string.error_sign_in_failed)
        }
    }

    private fun openLeaderboards() {
        PlayGames.getLeaderboardsClient(this)
            .allLeaderboardsIntent
            .addOnSuccessListener { intent ->
                playGamesActivityResultLauncher.launch(intent, makeCustomAnimation(this, slide_in_left, slide_out_right))
            }
    }

    private fun openAchievements() {
        PlayGames.getAchievementsClient(this)
            .achievementsIntent
            .addOnSuccessListener { intent ->
                playGamesActivityResultLauncher.launch(intent, makeCustomAnimation(this, slide_in_left, slide_out_right))
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
                            val statisticsRecyclerView: RecyclerView = findViewById(R.id.statisticsListRecycler)
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
