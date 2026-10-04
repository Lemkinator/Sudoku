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
import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import android.content.Intent.ACTION_CREATE_DOCUMENT
import android.content.Intent.CATEGORY_OPENABLE
import android.content.Intent.EXTRA_TITLE
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Build
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
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.picker.app.SeslTimePickerDialog
import androidx.picker.widget.SeslTimePicker
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SeslSwitchPreferenceScreen
import com.google.android.gms.games.PlayGames
import dagger.hilt.android.AndroidEntryPoint
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
import de.lemke.commonutils.ui.utils.toSafeFileName
import de.lemke.commonutils.ui.utils.toast
import de.lemke.sudoku.R
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.databinding.ActivitySettingsBinding
import de.lemke.sudoku.domain.model.DataExportResult
import de.lemke.sudoku.domain.model.DataImportResult
import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.dialog.ProgressDialog.ProgressStyle.HORIZONTAL
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
        private lateinit var exportActivityResultLauncher: ActivityResultLauncher<Intent>
        private lateinit var importActivityResultLauncher: ActivityResultLauncher<String>

        private val viewModel: SettingsViewModel by viewModels()

        @Inject
        lateinit var userSettings: UserSettings

        private var deleteInvalidSudokusDialog: AlertDialog? = null
        private var dataTransferProgressDialog: ProgressDialog? = null

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
            addPreferencesFromResource(commonutilsR.xml.preferences_more_info)
        }

        override fun onCreate(bundle: Bundle?) {
            super.onCreate(bundle)
            exportActivityResultLauncher =
                registerForSingleLaunchResult(StartActivityForResult()) { result ->
                    val uri = result.data?.data
                    if (result.resultCode == RESULT_OK && uri != null) viewModel.onExportDestinationPicked(uri)
                }
            importActivityResultLauncher =
                registerForSingleLaunchResult(GetContent()) { uri: Uri? ->
                    if (uri == null) {
                        toast(R.string.error_no_file_selected)
                    } else {
                        viewModel.onImportFilePicked(uri)
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
            collectState(viewModel.dataTransfer) { renderDataTransferProgress(it) }
            collectState(viewModel.dataTransfer, minActiveState = RESUMED) { if (it is DataTransfer.Result) onDataTransferResult(it) }
            collectState(viewModel.invalidSudokuDeletion, minActiveState = RESUMED) {
                if (it is InvalidSudokuDeletion.Result) onInvalidSudokuDeletionResult(it)
            }
        }

        override fun onDestroyView() {
            dismissDataTransferProgress()
            super.onDestroyView()
        }

        private fun initPreferences() {
            initErrorLimitPreference()
            initDailyNotificationPreference()
            initIntroPreference()
            initExportDataPreference()
            initImportDataPreference()
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

        internal fun initExportDataPreference() {
            findPreference<PreferenceScreen>("exportData")?.onSingleLaunchClick {
                exportActivityResultLauncher.launch(
                    Intent(ACTION_CREATE_DOCUMENT).apply {
                        addCategory(CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(EXTRA_TITLE, "sudoku_export".toSafeFileName(".json"))
                    },
                )
            }
        }

        internal fun initImportDataPreference() {
            findPreference<PreferenceScreen>("importData")?.onSingleLaunchClick {
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(R.string.import_data)
                    .setMessage(R.string.import_data_message)
                    .setNegativeButton(designR.string.oui_des_common_cancel, null)
                    .setPositiveButton(commonutilsR.string.commonutils_ok) { _: DialogInterface, _: Int ->
                        importActivityResultLauncher.launch("application/json")
                    }.showOnce(IMPORT_DATA_DIALOG_TAG)
            }
        }

        private fun renderDataTransferProgress(transfer: DataTransfer) {
            when (transfer) {
                is DataTransfer.Running -> showDataTransferProgress(transfer.toProgressUi())
                DataTransfer.Idle, is DataTransfer.Result -> dismissDataTransferProgress()
            }
        }

        private fun showDataTransferProgress(ui: DataTransferProgressUi) {
            val dialog =
                dataTransferProgressDialog ?: ProgressDialog(requireContext()).also {
                    it.setCancelable(false)
                    it.setProgressStyle(HORIZONTAL)
                    dataTransferProgressDialog = it
                }
            dialog.setTitle(ui.title)
            dialog.setMessage(getString(ui.message))
            when (val indicator = ui.indicator) {
                ProgressIndicator.Indeterminate -> {
                    dialog.isIndeterminate = true
                    dialog.max = 1
                    dialog.progress = 0
                }

                is ProgressIndicator.Determinate -> {
                    dialog.isIndeterminate = false
                    dialog.max = indicator.total
                    dialog.progress = indicator.done
                }
            }
            dialog.showOnce(DATA_TRANSFER_PROGRESS_DIALOG_TAG)
        }

        private fun dismissDataTransferProgress() {
            dataTransferProgressDialog?.dismiss()
            dataTransferProgressDialog = null
        }

        private fun onDataTransferResult(result: DataTransfer.Result) {
            when (result) {
                is DataTransfer.Exported -> showExportResult(result.result)
                is DataTransfer.Imported -> showImportResult(result.result)
            }
            viewModel.onDataTransferHandled(result)
        }

        private fun showExportResult(result: DataExportResult) {
            AlertDialog
                .Builder(requireContext())
                .setTitle(R.string.export_data)
                .setMessage(exportResultMessage(result))
                .setPositiveButton(commonutilsR.string.commonutils_ok, null)
                .showOnce(EXPORT_RESULT_DIALOG_TAG)
        }

        private fun exportResultMessage(result: DataExportResult): String =
            when (result) {
                DataExportResult.Exported -> getString(R.string.export_data_success)
                DataExportResult.WriteFailed -> getString(commonutilsR.string.commonutils_error_creating_file)
            }

        private fun showImportResult(result: DataImportResult) {
            AlertDialog
                .Builder(requireContext())
                .setTitle(R.string.import_data)
                .setPositiveButton(commonutilsR.string.commonutils_ok, null)
                .setMessage(importResultMessage(result))
                .showOnce(IMPORT_RESULT_DIALOG_TAG)
        }

        private fun importResultMessage(result: DataImportResult): String =
            when (result) {
                is DataImportResult.Imported if result.skippedCount > 0 -> {
                    resources.getQuantityString(R.plurals.import_data_success_skipped, result.skippedCount, result.skippedCount)
                }

                is DataImportResult.Imported -> {
                    getString(R.string.import_data_success)
                }

                DataImportResult.InvalidJson -> {
                    getString(R.string.import_data_error_no_valid_json)
                }

                DataImportResult.InvalidFile -> {
                    getString(R.string.import_data_error_no_valid_file)
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
                        // .putExtra(Settings.EXTRA_CHANNEL_ID, getString(R.string.daily_sudoku_notification_channel_id))
                        requireContext().singleLaunchActivity(settingsIntent)
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
            const val IMPORT_DATA_DIALOG_TAG = "importData"
            const val IMPORT_RESULT_DIALOG_TAG = "importResult"
            const val EXPORT_RESULT_DIALOG_TAG = "exportResult"
            const val DATA_TRANSFER_PROGRESS_DIALOG_TAG = "dataTransferProgress"
            const val DELETE_INVALID_SUDOKUS_DIALOG_TAG = "deleteInvalidSudokus"
        }
    }
}
