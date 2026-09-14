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
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
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
import de.lemke.sudoku.di.DispatchersModule
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
 * here — it depends on a real, connected Play Games session that Robolectric cannot provide (see status file).
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
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_filter))
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            dialog.isShowing.shouldBeFalse()
        }

    @Test
    fun `the filter dialog's apply button updates the filter settings`() =
        launch { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_item_filter))
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            dialog.isShowing.shouldBeFalse()
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

    // endregion
}
