package de.lemke.sudoku.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.AppStart
import de.lemke.sudoku.domain.CheckAppStartUseCase
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.widget.MarginsTabLayout
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main) {
    companion object {
        var refreshView = false
    }

    private var currentTab = 0
    private var previousTab = 0
    private var currentFragment: Fragment? = null
    private var time: Long = 0
    private lateinit var fragmentManager: FragmentManager
    private lateinit var tabLayout: MarginsTabLayout

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var checkAppStart: CheckAppStartUseCase

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        time = System.currentTimeMillis()
        tabLayout = findViewById(R.id.main_tabs)
        fragmentManager = supportFragmentManager

        lifecycleScope.launch {
            when (checkAppStart()) {
                AppStart.FIRST_TIME -> setCurrentItem()
                AppStart.NORMAL -> setCurrentItem()
                AppStart.FIRST_TIME_VERSION -> setCurrentItem()
            }
        }

        // TabLayout
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.app_name)))
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.history)))
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.statistics)))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                previousTab = currentTab
                currentTab = tab.position
                setCurrentItem()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                lifecycleScope.launch {
                    if (currentTab != 0) {
                        currentTab = 0
                        setCurrentItem()
                    } else {
                        if (getUserSettings().confirmExit) {
                            if (System.currentTimeMillis() - time < 3000) finishAffinity()
                            else {
                                Toast.makeText(this@MainActivity, resources.getString(R.string.press_again_to_exit), Toast.LENGTH_SHORT).show()
                                time = System.currentTimeMillis()
                            }
                        } else finishAffinity()
                    }
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

    fun setCurrentItem() {
        if (tabLayout.isEnabled && tabLayout.selectedTabPosition != currentTab) tabLayout.getTabAt(currentTab)?.select()
        setFragment(currentTab)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuitem_app_info -> startActivity(Intent(this@MainActivity, AboutActivity::class.java))
            R.id.menuitem_settings -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }
        return true
    }

    private fun setFragment(tabPosition: Int) {
        if (fragmentManager.isDestroyed) return
        val mTabsTagName: Array<String> = resources.getStringArray(R.array.mainactivity_tab_tag)
        val mTabsClassName: Array<String> = resources.getStringArray(R.array.mainactivity_tab_class)
        val tabName = mTabsTagName[tabPosition]
        val fragment = fragmentManager.findFragmentByTag(tabName)
        try {
            currentFragment?.let { fragmentManager.beginTransaction().detach(it).commit() }
            //Fatal Exception: java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
            //https://stackoverflow.com/questions/7469082/getting-exception-illegalstateexception-can-not-perform-this-action-after-onsa/10261438#10261438
            //https://www.androiddesignpatterns.com/2013/08/fragment-transaction-commit-state-loss.html
        } catch (e: IllegalStateException) {
            currentFragment?.let { fragmentManager.beginTransaction().detach(it).commitAllowingStateLoss() }
        }
        if (fragment != null) {
            fragmentManager.executePendingTransactions()
            currentFragment = fragment
            try {
                fragmentManager.beginTransaction().attach(fragment).commit()
                //see comment above
            } catch (e: IllegalStateException) {
                fragmentManager.beginTransaction().attach(fragment).commitAllowingStateLoss()
            }
        } else {
            try {
                currentFragment = Class.forName(mTabsClassName[tabPosition]).newInstance() as Fragment
            } catch (e: Exception) {
                Toast.makeText(applicationContext, e.toString(), Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
            fragmentManager.beginTransaction().add(R.id.fragment_container, currentFragment!!, tabName).commit()
        }
    }
}