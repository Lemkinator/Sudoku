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
import androidx.preference.PreferenceScreen
import androidx.preference.SeslSwitchPreferenceScreen
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.PlayGamesSdk
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.R
import de.lemke.sudoku.TestDispatchers
import de.lemke.sudoku.TestPersistenceModule
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.DefaultSudokusRepository
import de.lemke.sudoku.data.database.SudokuDao
import de.lemke.sudoku.data.database.SudokuWithFields
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.di.SudokusRepositoryModule
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
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
@UninstallModules(SudokusRepositoryModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SettingsFragmentTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    private var getAllGate = CompletableDeferred(Unit)
    private var getAllCalls = 0
    private val sudokuDao = TestPersistenceModule.provideTestAppDatabase(ApplicationProvider.getApplicationContext()).sudokuDao()

    @BindValue
    @JvmField
    val sudokusRepository: SudokusRepository =
        DefaultSudokusRepository(
            object : SudokuDao by sudokuDao {
                override suspend fun getAll(): List<SudokuWithFields> {
                    getAllCalls++
                    getAllGate.await()
                    return sudokuDao.getAll()
                }
            },
        )

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
    fun `tapping the deleteInvalidSudokus delete button twice deletes once and dismisses once the deletion finished`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("deleteInvalidSudokus")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val deleteButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            deleteButton.performClick()
            deleteButton.performClick()
            idleMainLooper()

            getAllCalls shouldBe 1
            dialog.isShowing.shouldBeTrue()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            idleMainLooper()
            dialog.isShowing.shouldBeFalse()
            getAllCalls shouldBe 1
        }

    @Test
    fun `deleteInvalidSudokus preference's delete button confirms and dismisses`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("deleteInvalidSudokus")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            idleMainLooper()
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
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            // SeslTimePickerDialog ignores BUTTON_POSITIVE clicks during its 283ms show animation.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idleMainLooper()
            dialog.isShowing.shouldBeFalse()
            userSettings.dailySudokuNotificationHour shouldBe 14
            userSettings.dailySudokuNotificationMinute shouldBe 37
            pref.summary.toString() shouldContain "2:37"
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
    fun `deleteInvalidSudokus init logs and no-ops when the preference is missing`() =
        launch { fragment ->
            fragment.removePref<PreferenceScreen>("deleteInvalidSudokus")
            fragment.initDeleteInvalidSudokusPreference()
            fragment.findPreference<PreferenceScreen>("deleteInvalidSudokus").shouldBeNull()
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
