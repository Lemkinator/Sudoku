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
import android.content.Intent
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.descendants
import androidx.picker.app.SeslTimePickerDialog
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SeslSwitchPreferenceScreen
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.AchievementsClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.R
import de.lemke.sudoku.TestDispatchers
import de.lemke.sudoku.data.UserSettings
import dev.oneuiproject.oneui.ktx.getRelativeLinksCard
import dev.oneuiproject.oneui.widget.RelativeLinksCard
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SettingsFragmentTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var testDispatchers: TestDispatchers

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var userSettings: UserSettings

    @Before
    fun setup() {
        hiltRule.inject()
        testDispatchers.io.redirectTo(UnconfinedTestDispatcher())
        settings.bypassOobe()
        PlayGamesSdk.initialize(ApplicationProvider.getApplicationContext())
    }

    @After
    fun resetDispatchers() {
        testDispatchers.reset()
    }

    @After
    fun unmockPlayGames() {
        unmockkStatic(PlayGames::class)
    }

    private fun launch(block: (SettingsActivity.SettingsFragment) -> Unit) {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            idleMainLooper()
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

    @Test
    fun `errorLimit shows the no-limit summary at launch when already set to 0`() {
        userSettings.errorLimit = 0
        launch { fragment ->
            fragment.pref<DropDownPreference>("errorLimit").summary.toString() shouldBe
                fragment.getString(R.string.no_limit)
        }
    }

    @Test
    fun `errorLimit shows the entered text when it cannot be parsed as a number`() =
        launch { fragment ->
            val pref = fragment.pref<DropDownPreference>("errorLimit")
            pref.onPreferenceChangeListener?.onPreferenceChange(pref, "abc")
            pref.summary.toString() shouldBe "abc"
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

    @Test
    fun `tapping the intro preference twice opens one IntroActivity`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("intro")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val activity = shadowOf(fragment.requireActivity())
            activity.nextStartedActivity?.component?.className shouldBe IntroActivity::class.java.name
            activity.nextStartedActivity shouldBe null
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
            idleMainLooper()
            val dialog = ShadowDialog.getLatestDialog()
            dialog.shouldNotBeNull()
        }

    @Test
    fun `the time picker's done button applies the selected time and updates the summary`() {
        userSettings.dailySudokuNotificationHour = 14
        userSettings.dailySudokuNotificationMinute = 37
        launch { fragment ->
            val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
            shadowOf(context).grantPermissions(POST_NOTIFICATIONS)
            val pref = fragment.pref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            idleMainLooper()
            val dialog = ShadowDialog.getLatestDialog() as SeslTimePickerDialog
            dialog.updateTime(9, 5)
            // SeslTimePickerDialog ignores BUTTON_POSITIVE clicks until its spinners' show animation ends.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idleMainLooper()
            dialog.isShowing.shouldBeFalse()
            userSettings.dailySudokuNotificationHour shouldBe 9
            userSettings.dailySudokuNotificationMinute shouldBe 5
            pref.summary.toString() shouldContain "9:05"
        }
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
    fun `the notification permission launcher's callback re-syncs the toggle after a grant`() =
        launch { fragment ->
            val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
            shadowOf(context).denyPermissions(POST_NOTIFICATIONS)
            val pref = fragment.pref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)

            val request = shadowOf(fragment.requireActivity()).lastRequestedPermission.shouldNotBeNull()
            shadowOf(context).grantPermissions(POST_NOTIFICATIONS)
            fragment.requireActivity().onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                intArrayOf(android.content.pm.PackageManager.PERMISSION_GRANTED),
            )
            idleMainLooper()

            pref.isChecked.shouldBeTrue()
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
            ShadowToast.shownToastCount() shouldBe 0
        }

    @Test
    fun `daily notification toggle shows the error toast when the device has no notification settings screen`() =
        launch { fragment ->
            val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
            shadowOf(context).grantPermissions(POST_NOTIFICATIONS)
            shadowOf(context).checkActivities(true)
            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            shadowOf(notificationManager).setNotificationsEnabled(false)
            val pref = fragment.pref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            pref.isChecked.shouldBeFalse()
            shadowOf(fragment.requireActivity()).nextStartedActivity.shouldBeNull()
            ShadowToast.getTextOfLatestToast() shouldBe "Error"
        }

    @Test
    fun `daily notification toggle off disables it without further checks`() {
        userSettings.dailySudokuNotificationEnabled = true
        launch { fragment ->
            val pref = fragment.pref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            pref.onPreferenceChangeListener?.onPreferenceChange(pref, false)
            idleMainLooper()
            userSettings.dailySudokuNotificationEnabled.shouldBeFalse()
        }
    }

    @Test
    fun `daily notification summary uses a 24-hour time when the system uses 24-hour format`() {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        android.provider.Settings.System
            .putString(context.contentResolver, android.provider.Settings.System.TIME_12_24, "24")
        launch { fragment ->
            val pref = fragment.pref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            pref.summary.toString() shouldNotContain "AM"
            pref.summary.toString() shouldNotContain "PM"
        }
    }

    // endregion

    // region relative links

    @Test
    fun `the share app link unlocks the share achievement and opens the share sheet`() {
        val achievements = mockk<AchievementsClient>(relaxed = true)
        launch { fragment ->
            val activity = fragment.requireActivity()
            mockkStatic(PlayGames::class)
            every { PlayGames.getAchievementsClient(activity) } returns achievements

            fragment
                .getRelativeLinksCard()
                .shouldNotBeNull()
                .link("Share app")
                .performClick()

            verify(exactly = 1) { achievements.unlock("CgkIqfzS8vQZEAIQBA") }
            shadowOf(activity).nextStartedActivity.action shouldBe Intent.ACTION_CHOOSER
        }
    }

    private fun RelativeLinksCard.link(title: String): TextView =
        descendants.filterIsInstance<TextView>().first {
            it.text.toString() ==
                title
        }

    // endregion

    // region recreation

    @Test
    fun `recreating the settings screen keeps its single settings fragment`() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.recreate()
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.supportFragmentManager.fragments.filterIsInstance<SettingsActivity.SettingsFragment>() shouldHaveSize 1
            }
        }
    }

    // endregion

    // region preference not found

    private fun <T : Preference> SettingsActivity.SettingsFragment.removePref(key: String) {
        val pref = pref<T>(key)
        pref.parent?.removePreference(pref)
    }

    @Test
    fun `errorLimit init logs and no-ops when the preference is missing`() =
        launch { fragment ->
            fragment.removePref<DropDownPreference>("errorLimit")
            fragment.initErrorLimitPreference()
            fragment.findPreference<DropDownPreference>("errorLimit").shouldBeNull()
        }

    @Test
    fun `dailyNotification init logs and no-ops when the preference is missing`() =
        launch { fragment ->
            fragment.removePref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            fragment.initDailyNotificationPreference()
            fragment.findPreference<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled").shouldBeNull()
        }

    @Test
    fun `intro init logs and no-ops when the preference is missing`() =
        launch { fragment ->
            fragment.removePref<PreferenceScreen>("intro")
            fragment.initIntroPreference()
            fragment.findPreference<PreferenceScreen>("intro").shouldBeNull()
        }

    @Test
    fun `the notification permission launcher's callback still updates settings when the preference is missing`() =
        launch { fragment ->
            val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
            shadowOf(context).denyPermissions(POST_NOTIFICATIONS)
            val pref = fragment.pref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val request = shadowOf(fragment.requireActivity()).lastRequestedPermission.shouldNotBeNull()

            fragment.removePref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            shadowOf(context).grantPermissions(POST_NOTIFICATIONS)
            fragment.requireActivity().onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                intArrayOf(android.content.pm.PackageManager.PERMISSION_GRANTED),
            )
            idleMainLooper()

            fragment.findPreference<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled").shouldBeNull()
            userSettings.dailySudokuNotificationEnabled.shouldBeTrue()
        }

    // endregion
}
