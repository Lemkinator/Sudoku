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

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.NotificationManager
import android.content.Context.NOTIFICATION_SERVICE
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SeslSwitchPreferenceScreen
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
import de.lemke.sudoku.R
import de.lemke.sudoku.di.DispatchersModule
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Duration
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
import org.robolectric.shadows.ShadowDialog

/**
 * Covers [SettingsActivity.SettingsFragment]'s preference wiring by finding each preference through the real
 * [PreferenceFragmentCompat] and invoking its `onPreferenceClickListener`/`onPreferenceChangeListener` directly —
 * the same real listeners `.onClick`/`.onNewValue` install, without needing to render or tap actual preference rows.
 * `exportData`/`importData`'s `registerForActivityResult` callbacks are not driven here: simulating a real Activity
 * Result requires either Espresso-Intents or `ShadowActivity.receiveResult`, neither established in this fleet yet.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SettingsFragmentTest {
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
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun launch(block: (SettingsActivity.SettingsFragment) -> Unit) {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.findFragmentById(R.id.settings) as SettingsActivity.SettingsFragment
                block(fragment)
            }
        }
    }

    private fun <T : Preference> SettingsActivity.SettingsFragment.pref(key: String): T = findPreference<T>(key).shouldNotBeNull()

    // region errorLimit

    @Test
    fun `errorLimit shows the no-limit summary when set to 0`() =
        launch { fragment ->
            val pref = fragment.pref<DropDownPreference>("errorLimit")
            pref.onPreferenceChangeListener?.onPreferenceChange(pref, "0")
            pref.summary.toString() shouldBe fragment.getString(R.string.no_limit)
        }

    @Test
    fun `errorLimit shows the numeric summary for a non-zero limit`() =
        launch { fragment ->
            val pref = fragment.pref<DropDownPreference>("errorLimit")
            pref.onPreferenceChangeListener?.onPreferenceChange(pref, "5")
            pref.summary.toString() shouldBe "5"
        }

    // endregion

    // region intro

    @Test
    fun `intro preference opens IntroActivity opened from settings`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("intro")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.component?.className shouldBe IntroActivity::class.java.name
            started.getBooleanExtra(IntroActivity.KEY_OPENED_FROM_SETTINGS, false).shouldBeTrue()
        }

    // endregion

    // region exportData / importData (click only, see class doc)

    @Test
    fun `exportData preference launches a create-document picker`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("exportData")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.action shouldBe android.content.Intent.ACTION_CREATE_DOCUMENT
        }

    @Test
    fun `importData preference shows a confirmation dialog`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("importData")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val dialog = ShadowDialog.getLatestDialog()
            dialog.shouldNotBeNull()
            dialog.isShowing.shouldBeTrue()
        }

    @Test
    fun `importData preference's ok button launches a document picker`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("importData")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            dialog.isShowing.shouldBeFalse()
        }

    // endregion

    // region deleteInvalidSudokus

    @Test
    fun `deleteInvalidSudokus preference shows a confirmation dialog`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("deleteInvalidSudokus")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val dialog = ShadowDialog.getLatestDialog()
            dialog.shouldNotBeNull()
            dialog.isShowing.shouldBeTrue()
        }

    @Test
    fun `deleteInvalidSudokus preference's delete button confirms and dismisses`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("deleteInvalidSudokus")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            shadowOf(Looper.getMainLooper()).idle()
            dialog.isShowing.shouldBeFalse()
        }

    // endregion

    // region dailySudokuNotificationEnabled

    @Test
    fun `daily notification toggle shows the time picker when applied`() =
        launch { fragment ->
            val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
            shadowOf(context).grantPermissions(POST_NOTIFICATIONS)
            val pref = fragment.pref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog()
            dialog.shouldNotBeNull()
        }

    @Test
    fun `daily notification toggle requests permission when not yet granted`() =
        launch { fragment ->
            val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
            shadowOf(context).denyPermissions(POST_NOTIFICATIONS)
            val pref = fragment.pref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            pref.isChecked.shouldBeFalse()
        }

    @Test
    fun `daily notification toggle routes to system settings when notifications are disabled`() =
        launch { fragment ->
            val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
            shadowOf(context).grantPermissions(POST_NOTIFICATIONS)
            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            shadowOf(notificationManager).setNotificationsEnabled(false)
            val pref = fragment.pref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            pref.isChecked.shouldBeFalse()
            val started = shadowOf(fragment.requireActivity()).nextStartedActivity
            started.shouldNotBeNull()
            started.action shouldBe android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
        }

    @Test
    fun `daily notification toggle off disables it without further checks`() =
        launch { fragment ->
            val pref = fragment.pref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            pref.onPreferenceChangeListener?.onPreferenceChange(pref, false)
        }

    // endregion
}
