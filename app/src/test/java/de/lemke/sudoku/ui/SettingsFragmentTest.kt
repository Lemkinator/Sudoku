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
import android.app.Activity
import android.app.NotificationManager
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.DropDownPreference
import androidx.preference.Preference
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
import de.lemke.sudoku.TestPersistenceModule
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.data.database.SudokuDao
import de.lemke.sudoku.data.database.SudokuExport
import de.lemke.sudoku.data.database.SudokuWithFields
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.data.database.sudokuToExport
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.FakeDocumentProvider
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuId
import de.lemke.sudoku.domain.model.SudokuSize
import dev.oneuiproject.oneui.dialog.ProgressDialog
import io.kjson.stringifyJSON
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
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
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import dev.oneuiproject.oneui.design.R as designR

/** sdk = 36: Robolectric's max supported SDK. */
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
    val testIoDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()

    @BindValue
    @MainDispatcher
    @JvmField
    val testMainDispatcher: CoroutineDispatcher = Dispatchers.Main

    private var getAllGate = CompletableDeferred(Unit)
    private var getAllCalls = 0
    private val sudokuDao = TestPersistenceModule.provideTestAppDatabase(ApplicationProvider.getApplicationContext()).sudokuDao()

    @BindValue
    @JvmField
    val sudokusRepository: SudokusRepository =
        SudokusRepository(
            object : SudokuDao by sudokuDao {
                override suspend fun getAll(): List<SudokuWithFields> {
                    getAllCalls++
                    getAllGate.await()
                    return sudokuDao.getAll()
                }
            },
        )

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var userSettings: UserSettings

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

    // region exportData / importData

    @Test
    fun `tapping the importData preference twice shows one confirmation dialog`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("importData")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            ShadowDialog.getShownDialogs().count { it.isShowing } shouldBe 1
        }

    @Test
    fun `tapping the import confirmation's ok button twice launches one document picker`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("importData")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val okButton = (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
            okButton.performClick()
            okButton.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val activity = shadowOf(fragment.requireActivity())
            activity.nextStartedActivity?.action shouldBe android.content.Intent.ACTION_GET_CONTENT
            activity.nextStartedActivity shouldBe null
        }

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

    @Test
    fun `exportData's result callback ignores a cancelled picker`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("exportData")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val shadowActivity = shadowOf(fragment.requireActivity())
            val started = shadowActivity.peekNextStartedActivityForResult()!!
            shadowActivity.receiveResult(started.intent, Activity.RESULT_CANCELED, null)
            shadowOf(Looper.getMainLooper()).idle()
            ShadowDialog.getLatestDialog().shouldBeNull()
        }

    @Test
    fun `exportData's result callback ignores an OK result without a destination`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("exportData")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val shadowActivity = shadowOf(fragment.requireActivity())
            val started = shadowActivity.peekNextStartedActivityForResult()!!
            shadowActivity.receiveResult(started.intent, Activity.RESULT_OK, Intent())
            shadowOf(Looper.getMainLooper()).idle()
            ShadowDialog.getLatestDialog().shouldBeNull()
        }

    @Test
    fun `exportData's result callback ignores an OK result with a null data intent`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("exportData")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            val shadowActivity = shadowOf(fragment.requireActivity())
            val started = shadowActivity.peekNextStartedActivityForResult()!!
            shadowActivity.receiveResult(started.intent, Activity.RESULT_OK, null)
            shadowOf(Looper.getMainLooper()).idle()
            ShadowDialog.getLatestDialog().shouldBeNull()
        }

    @Test
    fun `exportData's result callback exports to the picked destination on RESULT_OK`() =
        launch { fragment ->
            val destinationFile = File.createTempFile("settings-fragment-export", ".json")
            try {
                val pref = fragment.pref<PreferenceScreen>("exportData")
                pref.onPreferenceClickListener?.onPreferenceClick(pref)
                val shadowActivity = shadowOf(fragment.requireActivity())
                val started = shadowActivity.peekNextStartedActivityForResult()!!
                shadowActivity.receiveResult(
                    started.intent,
                    Activity.RESULT_OK,
                    Intent().apply { data = Uri.fromFile(destinationFile) },
                )
                shadowOf(Looper.getMainLooper()).idle()
                destinationFile.readText() shouldBe "[]"
            } finally {
                destinationFile.delete()
            }
        }

    private fun pickExportDestination(
        fragment: SettingsActivity.SettingsFragment,
        destination: Uri,
    ) {
        val pref = fragment.pref<PreferenceScreen>("exportData")
        pref.onPreferenceClickListener?.onPreferenceClick(pref)
        val shadowActivity = shadowOf(fragment.requireActivity())
        val started = shadowActivity.peekNextStartedActivityForResult()!!
        shadowActivity.receiveResult(started.intent, Activity.RESULT_OK, Intent().apply { data = destination })
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `exportData shows the progress dialog while the export runs and the success dialog once it finished`() =
        launch { fragment ->
            val destinationFile = File.createTempFile("settings-fragment-export", ".json")
            try {
                getAllGate = CompletableDeferred()

                pickExportDestination(fragment, Uri.fromFile(destinationFile))

                val progressDialog = ShadowDialog.getLatestDialog().shouldBeInstanceOf<ProgressDialog>()
                progressDialog.isShowing.shouldBeTrue()
                progressDialog.findViewById<TextView>(designR.id.message)?.text?.toString() shouldBe
                    fragment.getString(R.string.export_data_ongoing)

                getAllGate.complete(Unit)
                shadowOf(Looper.getMainLooper()).idle()

                progressDialog.isShowing.shouldBeFalse()
                val successDialog = ShadowDialog.getLatestDialog() as AlertDialog
                successDialog.isShowing.shouldBeTrue()
                successDialog.findViewById<TextView>(android.R.id.message)?.text?.toString() shouldBe
                    fragment.getString(R.string.export_data_success)
            } finally {
                destinationFile.delete()
            }
        }

    @Test
    fun `exportData shows the file error dialog instead of crashing when the provider fails to open the destination`() =
        launch { fragment ->
            val authority = "settings.export.denied"
            val provider =
                FakeDocumentProvider(
                    File.createTempFile("settings-fragment-export", ".json").apply { deleteOnExit() },
                    "application/json",
                    exists = true,
                    openFailure = SecurityException("Permission Denial"),
                )
            provider.attachInfo(ApplicationProvider.getApplicationContext(), ProviderInfo().apply { this.authority = authority })
            ShadowContentResolver.registerProviderInternal(authority, provider)

            pickExportDestination(fragment, Uri.parse("content://$authority/document/export"))

            val resultDialog = ShadowDialog.getLatestDialog() as AlertDialog
            resultDialog.isShowing.shouldBeTrue()
            resultDialog.findViewById<TextView>(android.R.id.message)?.text?.toString() shouldBe "Error creating file"
        }

    @Test
    fun `importData's result callback shows an error toast when no file was selected`() =
        launch { fragment ->
            val pref = fragment.pref<PreferenceScreen>("importData")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val shadowActivity = shadowOf(fragment.requireActivity())
            val started = shadowActivity.peekNextStartedActivityForResult()!!
            shadowActivity.receiveResult(started.intent, Activity.RESULT_CANCELED, null)
            shadowOf(Looper.getMainLooper()).idle()
            ShadowToast.getTextOfLatestToast() shouldBe fragment.getString(R.string.error_no_file_selected)
        }

    @Test
    fun `importData's result callback imports the picked file on RESULT_OK`() =
        launch { fragment ->
            val sourceFile = File.createTempFile("settings-fragment-import", ".json")
            sourceFile.writeText("[]")
            try {
                val pref = fragment.pref<PreferenceScreen>("importData")
                pref.onPreferenceClickListener?.onPreferenceClick(pref)
                val confirmDialog = ShadowDialog.getLatestDialog() as AlertDialog
                confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                shadowOf(Looper.getMainLooper()).idle()
                val shadowActivity = shadowOf(fragment.requireActivity())
                val started = shadowActivity.peekNextStartedActivityForResult()!!
                shadowActivity.receiveResult(
                    started.intent,
                    Activity.RESULT_OK,
                    Intent().apply { data = Uri.fromFile(sourceFile) },
                )
                shadowOf(Looper.getMainLooper()).idle()
                val resultDialog = ShadowDialog.getLatestDialog().shouldNotBeNull()
                resultDialog shouldNotBe confirmDialog
                resultDialog.isShowing.shouldBeTrue()
            } finally {
                sourceFile.delete()
            }
        }

    private fun registerImportDocument(
        authority: String,
        exports: List<SudokuExport>,
    ): Uri = registerImportDocument(authority, exports.stringifyJSON(), "application/json")

    private fun registerImportDocument(
        authority: String,
        content: String,
        mimeType: String,
    ): Uri {
        val file = File.createTempFile("settings-fragment-import", ".json").apply { deleteOnExit() }
        file.writeText(content)
        val provider = FakeDocumentProvider(file, mimeType, exists = true, openFailure = null)
        provider.attachInfo(ApplicationProvider.getApplicationContext(), ProviderInfo().apply { this.authority = authority })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        return Uri.parse("content://$authority/document/import")
    }

    private fun exportOfSize(size: Int): SudokuExport =
        sudokuToExport(
            Sudoku.create(
                sudokuId = SudokuId.generate(),
                size = SudokuSize.FOUR,
                difficulty = Difficulty.VERY_EASY,
                modeLevel = Sudoku.MODE_NORMAL,
                fields = MutableList(SudokuSize.FOUR.cellCount) { Field(position = Position.create(it, SudokuSize.FOUR), solution = 1) },
            ),
        ).copy(size = size)

    private fun importResultMessage(
        fragment: SettingsActivity.SettingsFragment,
        uri: Uri,
    ): String? {
        val pref = fragment.pref<PreferenceScreen>("importData")
        pref.onPreferenceClickListener?.onPreferenceClick(pref)
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val shadowActivity = shadowOf(fragment.requireActivity())
        val started = shadowActivity.peekNextStartedActivityForResult()!!
        shadowActivity.receiveResult(started.intent, Activity.RESULT_OK, Intent().apply { data = uri })
        shadowOf(Looper.getMainLooper()).idle()
        val resultDialog = ShadowDialog.getLatestDialog() as AlertDialog
        return resultDialog.findViewById<TextView>(android.R.id.message)?.text?.toString()
    }

    @Test
    fun `importData's result dialog keeps the plain success message when nothing is skipped`() =
        launch { fragment ->
            val uri = registerImportDocument("settings.import.none", listOf(exportOfSize(4)))

            importResultMessage(fragment, uri) shouldBe "Data imported successfully."
        }

    @Test
    fun `importData's result dialog names one skipped sudoku`() =
        launch { fragment ->
            val uri = registerImportDocument("settings.import.one", listOf(exportOfSize(4), exportOfSize(5)))

            importResultMessage(fragment, uri) shouldBe "Data imported successfully. 1 invalid Sudoku was skipped."
        }

    @Test
    fun `importData's result dialog counts two skipped sudokus`() =
        launch { fragment ->
            val uri = registerImportDocument("settings.import.two", listOf(exportOfSize(5), exportOfSize(4), exportOfSize(0)))

            importResultMessage(fragment, uri) shouldBe "Data imported successfully. 2 invalid Sudokus were skipped."
        }

    @Test
    fun `importData's result dialog shows the invalid-file error for a document with the wrong mime type`() =
        launch { fragment ->
            val uri = registerImportDocument("settings.import.wrongmime", listOf(exportOfSize(4)).stringifyJSON(), "text/plain")

            importResultMessage(fragment, uri) shouldBe "Error: No valid file."
        }

    @Test
    fun `importData's result dialog shows the invalid-JSON error for malformed JSON`() =
        launch { fragment ->
            val uri = registerImportDocument("settings.import.malformed", "[{not json", "application/json")

            importResultMessage(fragment, uri) shouldBe "Error: No valid JSON file."
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
            shadowOf(Looper.getMainLooper()).idle()

            getAllCalls shouldBe 1
            dialog.isShowing.shouldBeTrue()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
            shadowOf(Looper.getMainLooper()).idle()
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
    fun `the time picker's done button applies the selected time and updates the summary`() {
        userSettings.dailySudokuNotificationHour = 14
        userSettings.dailySudokuNotificationMinute = 37
        launch { fragment ->
            val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
            shadowOf(context).grantPermissions(POST_NOTIFICATIONS)
            val pref = fragment.pref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            pref.onPreferenceClickListener?.onPreferenceClick(pref)
            shadowOf(Looper.getMainLooper()).idle()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            // SeslTimePickerDialog ignores BUTTON_POSITIVE clicks during its 283ms show animation.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
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
            shadowOf(Looper.getMainLooper()).idle()

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
        }

    @Test
    fun `daily notification toggle off disables it without further checks`() {
        userSettings.dailySudokuNotificationEnabled = true
        launch { fragment ->
            val pref = fragment.pref<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")
            pref.onPreferenceChangeListener?.onPreferenceChange(pref, false)
            shadowOf(Looper.getMainLooper()).idle()
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
    fun `exportData init logs and no-ops when the preference is missing`() =
        launch { fragment ->
            fragment.removePref<PreferenceScreen>("exportData")
            fragment.initExportDataPreference()
            fragment.findPreference<PreferenceScreen>("exportData").shouldBeNull()
        }

    @Test
    fun `importData init logs and no-ops when the preference is missing`() =
        launch { fragment ->
            fragment.removePref<PreferenceScreen>("importData")
            fragment.initImportDataPreference()
            fragment.findPreference<PreferenceScreen>("importData").shouldBeNull()
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
            shadowOf(Looper.getMainLooper()).idle()

            fragment.findPreference<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled").shouldBeNull()
            userSettings.dailySudokuNotificationEnabled.shouldBeTrue()
        }

    // endregion
}
