/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.sudoku.ui

import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.di.MainDispatcher
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.sudokuToExport
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuFilterFlags
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import dev.oneuiproject.oneui.navigation.widget.DrawerNavigationView
import io.kjson.stringifyJSON
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog

/**
 * Covers [MainActivity]'s menu (`onCreateOptionsMenu`/`onPrepareOptionsMenu`/`onOptionsItemSelected`,
 * `showStatisticsFilterDialog`), the drawer navigation clicks that route to another activity, and
 * `checkImportedSudoku`'s failure path. Play Games sign-in (`achievements_dest`/`leaderboards_dest`) is not driven
 * here — it depends on a real, connected Play Games session that Robolectric cannot provide (see
 * [MainActivityGamesSignInTest], which fakes that boundary instead).
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class MainActivityMenuTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @DefaultDispatcher
    @JvmField
    val testDefaultDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()

    @BindValue
    @IoDispatcher
    @JvmField
    val testIoDispatcher: CoroutineDispatcher = Dispatchers.IO

    @BindValue
    @MainDispatcher
    @JvmField
    val testMainDispatcher: CoroutineDispatcher = Dispatchers.Main

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var userSettings: UserSettings

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        // MainActivity talks to PlayGames on launch; the SDK is normally auto-initialized by its
        // manifest-merged ContentProvider, which Robolectric does not run under HiltTestApplication.
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun launch(
        intent: Intent? = null,
        block: (MainActivity) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val launchIntent = intent ?: Intent(context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity -> block(activity) }
        }
    }

    // region menu

    private fun menuFor(activity: MainActivity) = android.widget.PopupMenu(activity, activity.binding.root).menu

    @Test
    fun `onCreateOptionsMenu inflates the filter menu`() =
        launch { activity ->
            val menu = menuFor(activity)
            activity.onCreateOptionsMenu(menu).shouldBeTrue()
            menu.findItem(R.id.menu_item_filter).shouldNotBeNull()
        }

    @Test
    fun `onPrepareOptionsMenu hides the filter group outside the statistics tab`() =
        launch { activity ->
            activity.onTabItemSelected(1)
            val menu = menuFor(activity)
            activity.onCreateOptionsMenu(menu)
            activity.onPrepareOptionsMenu(menu)
            menu.findItem(R.id.menu_item_filter).isVisible.shouldBeFalse()
        }

    @Test
    fun `onPrepareOptionsMenu shows the filter group on the statistics tab`() =
        launch { activity ->
            activity.onTabItemSelected(2)
            val menu = menuFor(activity)
            activity.onCreateOptionsMenu(menu)
            activity.onPrepareOptionsMenu(menu)
            menu.findItem(R.id.menu_item_filter).isVisible.shouldBeTrue()
        }

    @Test
    @Config(application = HiltTestApplication::class, sdk = [33])
    fun `onCreate skips the activity transition override below UPSIDE_DOWN_CAKE`() =
        launch { activity -> activity.isFinishing.shouldBeFalse() }

    @Test
    fun `onPrepareOptionsMenu is a no-op for a null menu`() =
        launch { activity ->
            activity.onPrepareOptionsMenu(null).shouldBeTrue()
        }

    @Test
    fun `menu_item_filter shows the statistics filter dialog`() =
        launch { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_filter)).shouldBeTrue()
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog()
            dialog.shouldNotBeNull()
            dialog.isShowing.shouldBeTrue()
        }

    @Test
    fun `the filter dialog's cancel button dismisses without changing settings`() =
        launch { activity ->
            val originalFilterFlags = userSettings.filterFlags
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_filter))
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.findViewById<AppCompatCheckBox>(R.id.filterSize4)!!.isChecked = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            dialog.isShowing.shouldBeFalse()
            userSettings.filterFlags shouldBe originalFilterFlags
        }

    @Test
    fun `the filter dialog's apply button updates the filter settings`() =
        launch { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_filter))
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.findViewById<AppCompatCheckBox>(R.id.filterSize4)!!.isChecked = false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            dialog.isShowing.shouldBeFalse()
            (userSettings.filterFlags and SudokuFilterFlags.SIZE_4X4) shouldBe 0
            (userSettings.filterFlags and SudokuFilterFlags.SIZE_ALL) shouldBe 0
        }

    @Test
    fun `an unknown menu item falls through to the default behavior`() =
        launch { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(-12345)).shouldBeFalse()
        }

    // endregion

    // region drawer navigation

    private fun clickNavItem(
        activity: MainActivity,
        id: Int,
    ) {
        SystemClock.sleep(601L)
        val item = activity.binding.navigationView.findMenuItem(id) as androidx.appcompat.view.menu.MenuItemImpl
        item.invoke()
    }

    @Test
    fun `about_app_dest opens the about activity`() =
        launch { activity ->
            clickNavItem(activity, R.id.about_app_dest)
            val started = shadowOf(activity).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe CommonUtilsAboutActivity::class.java.name
        }

    @Test
    fun `about_me_dest opens the about-me activity`() =
        launch { activity ->
            clickNavItem(activity, R.id.about_me_dest)
            val started = shadowOf(activity).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe CommonUtilsAboutMeActivity::class.java.name
        }

    @Test
    fun `settings_dest opens the settings activity`() =
        launch { activity ->
            clickNavItem(activity, R.id.settings_dest)
            val started = shadowOf(activity).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe SettingsActivity::class.java.name
        }

    @Test
    fun `leaks_dest opens the memory leak screen`() =
        launch { activity ->
            clickNavItem(activity, R.id.leaks_dest)
            shadowOf(activity).nextStartedActivity.shouldNotBeNull()
        }

    /**
     * `DrawerNavigationView`'s fixed `menu_navigation.xml` resource has no item id left unhandled by
     * `initDrawer`'s `when`, so the `else` fallthrough can't be reached through a real click on an
     * actual menu item. Reflectively retrieves the real, already-registered
     * `NavigationView.OnNavigationItemSelectedListener` (same technique as
     * `TabHistoryActionModeTest.actionModeListenerOf`) and invokes it with a real but unrecognized id —
     * every side effect from there on is real production code.
     */
    private fun navigationListenerOf(navigationView: DrawerNavigationView): NavigationView.OnNavigationItemSelectedListener {
        val field = DrawerNavigationView::class.java.getDeclaredField("navigationItemSelectedListener").apply { isAccessible = true }
        return field.get(navigationView) as NavigationView.OnNavigationItemSelectedListener
    }

    @Test
    fun `an unrecognized navigation item is ignored`() =
        launch { activity ->
            SystemClock.sleep(601L)
            val listener = navigationListenerOf(activity.binding.navigationView)
            listener.onNavigationItemSelected(RoboMenuItem(-54321)).shouldBeFalse()
        }

    // endregion

    // region about links (ClickableSpan)

    /**
     * `setupCommonUtilsActivities()` stores the real `SpannableString` on
     * [CommonUtilsAboutActivity]'s companion object as soon as `onCreate` runs — no need to actually
     * navigate to the about screen. The two `ClickableSpan`s are found on that real, rendered text and
     * invoked directly, exercising the exact anonymous `onClick` production code.
     */
    private fun clickableSpans(): List<android.text.style.ClickableSpan> {
        val text = CommonUtilsAboutActivity.optionalText.shouldNotBeNull()
        return text.getSpans(0, text.length, android.text.style.ClickableSpan::class.java).toList()
    }

    @Test
    fun `the about text has exactly the library and license links`() = launch { clickableSpans() shouldHaveSize 2 }

    @Test
    fun `clicking the library link span opens the library's GitHub page`() =
        launch { activity ->
            val spans = clickableSpans()
            spans[0].onClick(activity.binding.root)
            val started = shadowOf(activity).nextStartedActivity
            started.shouldNotBeNull()
            started.data shouldBe Uri.parse(activity.getString(R.string.sudoku_lib_github_link))
        }

    @Test
    fun `clicking the license link span opens the license's GitHub page`() =
        launch { activity ->
            val spans = clickableSpans()
            spans[1].onClick(activity.binding.root)
            val started = shadowOf(activity).nextStartedActivity
            started.shouldNotBeNull()
            started.data shouldBe Uri.parse(activity.getString(R.string.sudoku_lib_license_github_link))
        }

    // endregion

    // region checkImportedSudoku

    @Test
    fun `launching with an unresolvable import uri shows the import-failed toast`() {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent =
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("content://de.lemke.sudoku.nonexistent.provider/import"))
        launch(intent) {
            shadowOf(Looper.getMainLooper()).idle()
            org.robolectric.shadows.ShadowToast
                .getTextOfLatestToast() shouldBe context.getString(R.string.error_import_failed)
        }
    }

    @Test
    fun `launching with a resolvable import uri opens the imported sudoku`() {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val size = 4
        val sudoku =
            Sudoku.create(
                size = size,
                difficulty = Difficulty.VERY_EASY,
                modeLevel = Sudoku.MODE_NORMAL,
                fields =
                    MutableList(size * size) { i ->
                        Field(position = Position.create(i, size), solution = (i % size) + 1, value = (i % size) + 1, given = true)
                    },
            )
        val file = File.createTempFile("main-activity-import", ".json")
        file.writeText(sudokuToExport(sudoku).stringifyJSON())
        try {
            val intent = Intent(context, MainActivity::class.java).setData(Uri.fromFile(file))
            launch(intent) { activity ->
                shadowOf(Looper.getMainLooper()).idle()
                val started = shadowOf(activity).nextStartedActivity
                started.shouldNotBeNull()
                started.component?.className shouldBe SudokuActivity::class.java.name
                started.getStringExtra(KEY_SUDOKU_ID) shouldBe sudoku.id.value
            }
        } finally {
            file.delete()
        }
    }

    // endregion
}
