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
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import android.provider.Settings.EXTRA_APP_PACKAGE
import android.text.format.DateFormat
import android.text.format.DateFormat.is24HourFormat
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.picker.app.SeslTimePickerDialog
import androidx.picker.widget.SeslTimePicker
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SeslSwitchPreferenceScreen
import com.google.android.gms.games.PlayGames
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.ui.utils.bindBackupPreferences
import de.lemke.commonutils.ui.utils.collectState
import de.lemke.commonutils.ui.utils.initCommonUtilsPreferences
import de.lemke.commonutils.ui.utils.onSingleLaunchClick
import de.lemke.commonutils.ui.utils.openApp
import de.lemke.commonutils.ui.utils.prepareActivityTransformationTo
import de.lemke.commonutils.ui.utils.registerForSingleLaunchResult
import de.lemke.commonutils.ui.utils.setCustomBackAnimation
import de.lemke.commonutils.ui.utils.shareApp
import de.lemke.commonutils.ui.utils.showOnce
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import de.lemke.commonutils.ui.utils.startActivityForOutcome
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.databinding.ActivitySettingsBinding
import dev.oneuiproject.oneui.ktx.addRelativeLinksCard
import dev.oneuiproject.oneui.ktx.onNewValue
import dev.oneuiproject.oneui.ktx.setOnClickListenerWithProgress
import dev.oneuiproject.oneui.widget.RelativeLink
import java.util.Calendar
import java.util.Calendar.HOUR_OF_DAY
import java.util.Calendar.MINUTE
import javax.inject.Inject
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
        private val viewModel: SettingsViewModel by viewModels()

        @Inject
        lateinit var userSettings: UserSettings

        private var deleteInvalidSudokusDialog: AlertDialog? = null

        private val requestPermissionLauncher =
            registerForSingleLaunchResult(RequestPermission()) { isGranted: Boolean ->
                viewModel.onNotificationPermissionResult(isGranted)
                findPreference<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")?.isChecked =
                    viewModel.isDailyNotificationChecked
            }

        override fun onCreatePreferences(
            bundle: Bundle?,
            str: String?,
        ) {
            addPreferencesFromResource(commonutilsR.xml.preferences_design)
            addPreferencesFromResource(R.xml.preferences)
            addPreferencesFromResource(commonutilsR.xml.preferences_backup)
            addPreferencesFromResource(commonutilsR.xml.preferences_more_info)
        }

        override fun onCreate(bundle: Bundle?) {
            super.onCreate(bundle)
            initCommonUtilsPreferences(userSettings)
            bindBackupPreferences(viewModel.sectionLabels) { viewModel.backup }
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
            collectState(viewModel.invalidSudokuDeletion, minActiveState = RESUMED) {
                if (it is InvalidSudokuDeletion.Result) onInvalidSudokuDeletionResult(it)
            }
        }

        private fun initPreferences() {
            initErrorLimitPreference()
            initDailyNotificationPreference()
            initIntroPreference()
            initDeleteInvalidSudokusPreference()
        }

        internal fun initErrorLimitPreference() {
            findPreference<DropDownPreference>("errorLimit")?.apply {
                summary = if (userSettings.errorLimit == 0) getString(R.string.no_limit) else userSettings.errorLimit.toString()
                onNewValue { newValue: String ->
                    summary = if (newValue.toIntOrNull() == 0) getString(R.string.no_limit) else newValue
                }
            } ?: Log.e(TAG, "error limit Preference not found")
        }

        internal fun initDailyNotificationPreference() {
            findPreference<SeslSwitchPreferenceScreen>("dailySudokuNotificationEnabled")?.apply {
                isChecked = viewModel.isDailyNotificationChecked
                setDailyNotificationPrefTime(viewModel.dailySudokuNotificationHour, viewModel.dailySudokuNotificationMinute)
                onNewValue { applyDailyNotificationToggle(it) }
                onSingleLaunchClick {
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
                        dialog.showOnce(NOTIFICATION_TIME_PICKER_TAG)
                    }
                }
            } ?: Log.e(TAG, "daily notification Preference not found")
        }

        internal fun initIntroPreference() {
            findPreference<PreferenceScreen>("intro")?.onSingleLaunchClick {
                requireContext().singleLaunchActivity(
                    Intent(requireContext(), IntroActivity::class.java).putExtra(IntroActivity.KEY_OPENED_FROM_SETTINGS, true),
                )
            }
        }

        internal fun initDeleteInvalidSudokusPreference() {
            findPreference<PreferenceScreen>("deleteInvalidSudokus")?.onSingleLaunchClick {
                val dialog =
                    AlertDialog
                        .Builder(requireContext())
                        .setTitle(R.string.delete_invalid_sudokus)
                        .setMessage(R.string.delete_invalid_sudokus_summary)
                        .setNegativeButton(designR.string.oui_des_common_cancel, null)
                        .setPositiveButton(R.string.commonutils_delete, null)
                        .showOnce(DELETE_INVALID_SUDOKUS_DIALOG_TAG) ?: return@onSingleLaunchClick
                deleteInvalidSudokusDialog = dialog
                dialog.getButton(BUTTON_POSITIVE).apply {
                    setTextColor(requireContext().getColor(designR.color.oui_des_functional_red_color))
                    setOnClickListenerWithProgress { _, _ -> viewModel.onDeleteInvalidSudokusConfirmed() }
                }
            }
        }

        private fun onInvalidSudokuDeletionResult(result: InvalidSudokuDeletion.Result) {
            when (result) {
                InvalidSudokuDeletion.Finished -> deleteInvalidSudokusDialog?.dismiss()
            }
            deleteInvalidSudokusDialog = null
            viewModel.onInvalidSudokuDeletionHandled(result)
        }

        private fun SeslSwitchPreferenceScreen.applyDailyNotificationToggle(enabled: Boolean): DailyNotificationToggleResult =
            viewModel.onDailyNotificationToggleRequested(enabled).also { result ->
                when (result) {
                    DailyNotificationToggleResult.Applied -> {}

                    DailyNotificationToggleResult.NeedsPermission -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissionLauncher.launch(POST_NOTIFICATIONS)
                        }
                        isChecked = false
                    }

                    DailyNotificationToggleResult.SystemNotificationsDisabled -> {
                        val settingsIntent =
                            Intent(ACTION_APP_NOTIFICATION_SETTINGS)
                                .addFlags(FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(EXTRA_APP_PACKAGE, requireContext().packageName)
                        startActivityForOutcome(settingsIntent)
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

        private companion object {
            const val NOTIFICATION_TIME_PICKER_TAG = "notificationTimePicker"
            const val DELETE_INVALID_SUDOKUS_DIALOG_TAG = "deleteInvalidSudokus"
        }
    }
}
