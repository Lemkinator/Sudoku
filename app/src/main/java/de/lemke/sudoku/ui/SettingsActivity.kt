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
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import android.content.Intent.ACTION_CREATE_DOCUMENT
import android.content.Intent.CATEGORY_OPENABLE
import android.content.Intent.EXTRA_TITLE
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Bundle
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import android.provider.Settings.EXTRA_APP_PACKAGE
import android.text.format.DateFormat
import android.text.format.DateFormat.is24HourFormat
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.picker.app.SeslTimePickerDialog
import androidx.picker.widget.SeslTimePicker
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SeslSwitchPreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import com.google.android.gms.games.PlayGames
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.initCommonUtilsPreferences
import de.lemke.commonutils.ui.utils.openApp
import de.lemke.commonutils.ui.utils.prepareActivityTransformationTo
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.shareApp
import de.lemke.commonutils.ui.utils.toSafeFileName
import de.lemke.commonutils.ui.utils.toast
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.databinding.ActivitySettingsBinding
import de.lemke.sudoku.domain.ExportDataUseCase
import de.lemke.sudoku.domain.ImportDataUseCase
import dev.oneuiproject.oneui.ktx.addRelativeLinksCard
import dev.oneuiproject.oneui.ktx.onClick
import dev.oneuiproject.oneui.ktx.onNewValue
import dev.oneuiproject.oneui.ktx.setOnClickListenerWithProgress
import dev.oneuiproject.oneui.widget.RelativeLink
import java.util.Calendar
import java.util.Calendar.HOUR_OF_DAY
import java.util.Calendar.MINUTE
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import de.lemke.commonutils.R as commonutilsR
import dev.oneuiproject.oneui.design.R as designR

private const val TAG = "SettingsActivity"

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackAnimation(binding.root)
        if (savedInstanceState == null) supportFragmentManager.beginTransaction().replace(R.id.settings, SettingsFragment()).commit()
    }

    @AndroidEntryPoint
    class SettingsFragment : PreferenceFragmentCompat() {
        private lateinit var exportActivityResultLauncher: ActivityResultLauncher<Intent>
        private lateinit var importActivityResultLauncher: ActivityResultLauncher<String>

        private val viewModel: SettingsViewModel by viewModels()

        @Inject
        lateinit var userSettings: UserSettings

        @Inject
        lateinit var exportData: ExportDataUseCase

        @Inject
        lateinit var importData: ImportDataUseCase

        private val requestPermissionLauncher =
            registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
                viewModel.onNotificationPermissionResult(isGranted)
                findPreference<SeslSwitchPreferenceScreen>("daily_notification_pref")?.isChecked = viewModel.isDailyNotificationChecked
            }

        override fun onCreatePreferences(
            bundle: Bundle?,
            str: String?,
        ) {
            addPreferencesFromResource(commonutilsR.xml.preferences_design)
            addPreferencesFromResource(R.xml.preferences)
            addPreferencesFromResource(commonutilsR.xml.preferences_more_info)
        }

        override fun onCreate(bundle: Bundle?) {
            super.onCreate(bundle)
            exportActivityResultLauncher =
                registerForActivityResult(StartActivityForResult()) { result ->
                    if (result.resultCode == RESULT_OK && result.data?.data != null) {
                        lifecycleScope.launch { exportData(result.data!!.data!!) }
                    }
                }
            importActivityResultLauncher =
                registerForActivityResult(GetContent()) { uri: Uri? ->
                    if (uri == null) {
                        toast(R.string.error_no_file_selected)
                    } else {
                        lifecycleScope.launch { importData(uri) }
                    }
                }
            initCommonUtilsPreferences(userSettings)
            initPreferences()
        }

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)
            addRelativeLinksCard(
                RelativeLink(getString(R.string.commonutils_share_app)) {
                    PlayGames.getAchievementsClient(requireActivity()).unlock(getString(R.string.achievement_share_app))
                    shareApp()
                },
                RelativeLink(getString(R.string.commonutils_rate_app)) { openApp(requireContext().packageName, false) },
            )
        }

        @SuppressLint("InlinedApi")
        @Suppress("CyclomaticComplexMethod", "LongMethod")
        private fun initPreferences() {
            findPreference<DropDownPreference>("error_limit_pref")?.apply {
                summary = if (viewModel.errorLimit == 0) getString(R.string.no_limit) else viewModel.errorLimit.toString()
                onNewValue { newValue: String ->
                    viewModel.errorLimit = newValue.toIntOrNull() ?: 0
                    summary = if (newValue.toIntOrNull() == 0) getString(R.string.no_limit) else newValue
                }
            } ?: Log.e(TAG, "error limit Preference not found")

            findPreference<SwitchPreferenceCompat>("keep_screen_on_pref")?.apply {
                isChecked = viewModel.keepScreenOn
                onNewValue { v: Boolean -> viewModel.keepScreenOn = v }
            } ?: Log.e(TAG, "keep screen on Preference not found")

            findPreference<SwitchPreferenceCompat>("highlight_regional_pref")?.apply {
                isChecked = viewModel.highlightRegional
                onNewValue { v: Boolean -> viewModel.highlightRegional = v }
            } ?: Log.e(TAG, "regional highlight Preference not found")

            findPreference<SwitchPreferenceCompat>("highlight_number_pref")?.apply {
                isChecked = viewModel.highlightNumber
                onNewValue { v: Boolean -> viewModel.highlightNumber = v }
            } ?: Log.e(TAG, "number highlight Preference not found")

            findPreference<SwitchPreferenceCompat>("animations_pref")?.apply {
                isChecked = viewModel.animationsEnabled
                onNewValue { v: Boolean -> viewModel.animationsEnabled = v }
            } ?: Log.e(TAG, "animations Preference not found")

            findPreference<SeslSwitchPreferenceScreen>("daily_notification_pref")?.apply {
                isChecked = viewModel.isDailyNotificationChecked
                setDailyNotificationPrefTime(viewModel.dailySudokuNotificationHour, viewModel.dailySudokuNotificationMinute)
                onNewValue { applyDailyNotificationToggle(it) }
                onClick {
                    isChecked = true
                    if (applyDailyNotificationToggle(true) == DailyNotificationToggleResult.Applied) {
                        val dialog =
                            SeslTimePickerDialog(
                                requireContext(),
                                { _: SeslTimePicker?, hourOfDay: Int, minute: Int ->
                                    viewModel.onDailyNotificationTimeSelected(hourOfDay, minute)
                                    setDailyNotificationPrefTime(hourOfDay, minute)
                                },
                                viewModel.dailySudokuNotificationHour,
                                viewModel.dailySudokuNotificationMinute,
                                is24HourFormat(requireContext()),
                            )
                        dialog.show()
                    }
                }
            } ?: Log.e(TAG, "daily notification Preference not found")

            findPreference<PreferenceScreen>("intro_pref")?.onClick {
                startActivity(
                    Intent(requireContext(), IntroActivity::class.java).putExtra(IntroActivity.KEY_OPENED_FROM_SETTINGS, true),
                )
            }

            findPreference<PreferenceScreen>("export_data_pref")?.onClick {
                exportActivityResultLauncher.launch(
                    Intent(ACTION_CREATE_DOCUMENT).apply {
                        addCategory(CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(EXTRA_TITLE, "sudoku_export".toSafeFileName(".json"))
                    },
                )
            }

            findPreference<PreferenceScreen>("import_data_pref")?.onClick {
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(R.string.import_data)
                    .setMessage(R.string.import_data_message)
                    .setNegativeButton(designR.string.oui_des_common_cancel, null)
                    .setPositiveButton(commonutilsR.string.commonutils_ok) { _: DialogInterface, _: Int ->
                        importActivityResultLauncher.launch("application/json")
                    }.show()
            }

            findPreference<PreferenceScreen>("delete_invalid_sudokus_pref")?.onClick {
                val dialog =
                    AlertDialog
                        .Builder(requireContext())
                        .setTitle(R.string.delete_invalid_sudokus)
                        .setMessage(R.string.delete_invalid_sudokus_summary)
                        .setNegativeButton(designR.string.oui_des_common_cancel, null)
                        .setPositiveButton(R.string.commonutils_delete, null)
                        .create()
                dialog.show()
                dialog.getButton(BUTTON_POSITIVE).apply {
                    setTextColor(requireContext().getColor(designR.color.oui_des_functional_red_color))
                    setOnClickListenerWithProgress { _, _ ->
                        lifecycleScope.launch {
                            viewModel.onDeleteInvalidSudokusConfirmed()
                            delay(500.milliseconds)
                            dialog.dismiss()
                        }
                    }
                }
            }
        }

        private fun SeslSwitchPreferenceScreen.applyDailyNotificationToggle(enabled: Boolean): DailyNotificationToggleResult =
            viewModel.onDailyNotificationToggleRequested(enabled).also { result ->
                when (result) {
                    DailyNotificationToggleResult.Applied -> {}

                    DailyNotificationToggleResult.NeedsPermission -> {
                        requestPermissionLauncher.launch(POST_NOTIFICATIONS)
                        isChecked = false
                    }

                    DailyNotificationToggleResult.SystemNotificationsDisabled -> {
                        val settingsIntent =
                            Intent(ACTION_APP_NOTIFICATION_SETTINGS)
                                .addFlags(FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(EXTRA_APP_PACKAGE, requireContext().packageName)
                        // .putExtra(Settings.EXTRA_CHANNEL_ID, getString(R.string.daily_sudoku_notification_channel_id))
                        startActivity(settingsIntent)
                        isChecked = false
                    }
                }
            }

        private fun SeslSwitchPreferenceScreen.setDailyNotificationPrefTime(
            hourOfDay: Int,
            minute: Int,
        ) {
            summary =
                getString(
                    R.string.daily_sudoku_notification_channel_description_time,
                    DateFormat.format(
                        if (is24HourFormat(requireContext())) "HH:mm" else "h:mm a",
                        Calendar.getInstance().apply {
                            set(HOUR_OF_DAY, hourOfDay)
                            set(MINUTE, minute)
                        },
                    ),
                )
        }
    }
}
