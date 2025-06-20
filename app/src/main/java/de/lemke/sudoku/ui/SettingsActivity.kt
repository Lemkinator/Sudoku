package de.lemke.sudoku.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.os.Environment.getExternalStorageDirectory
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import android.provider.Settings.EXTRA_APP_PACKAGE
import android.text.format.DateFormat
import android.text.format.DateFormat.is24HourFormat
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_NONE
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.lifecycleScope
import androidx.picker.app.SeslTimePickerDialog
import androidx.picker.widget.SeslTimePicker
import androidx.preference.DropDownPreference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SeslSwitchPreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import com.google.android.gms.games.PlayGames
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.initCommonUtilsPreferences
import de.lemke.commonutils.onPrefChange
import de.lemke.commonutils.openApp
import de.lemke.commonutils.prepareActivityTransformationTo
import de.lemke.commonutils.setCustomBackAnimation
import de.lemke.commonutils.shareApp
import de.lemke.commonutils.toast
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivitySettingsBinding
import de.lemke.sudoku.domain.DeleteInvalidSudokusUseCase
import de.lemke.sudoku.domain.ExportDataUseCase
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.ImportDataUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.ktx.addRelativeLinksCard
import dev.oneuiproject.oneui.ktx.setOnClickListenerWithProgress
import dev.oneuiproject.oneui.widget.RelativeLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
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
        private lateinit var pickExportFolderActivityResultLauncher: ActivityResultLauncher<Uri?>
        private lateinit var pickImportJsonActivityResultLauncher: ActivityResultLauncher<String>

        @Inject
        lateinit var getUserSettings: GetUserSettingsUseCase

        @Inject
        lateinit var updateUserSettings: UpdateUserSettingsUseCase

        @Inject
        lateinit var sendDailyNotification: SendDailyNotificationUseCase

        @Inject
        lateinit var exportData: ExportDataUseCase

        @Inject
        lateinit var importData: ImportDataUseCase

        @Inject
        lateinit var deleteInvalidSudokus: DeleteInvalidSudokusUseCase

        override fun onCreatePreferences(bundle: Bundle?, str: String?) {
            addPreferencesFromResource(commonutilsR.xml.preferences_design)
            addPreferencesFromResource(R.xml.preferences)
            addPreferencesFromResource(commonutilsR.xml.preferences_more_info)
        }

        override fun onCreate(bundle: Bundle?) {
            super.onCreate(bundle)
            pickExportFolderActivityResultLauncher = registerForActivityResult(OpenDocumentTree()) { uri: Uri? ->
                if (uri == null) toast(R.string.error_no_folder_selected)
                else lifecycleScope.launch { exportData(uri) }
            }
            pickImportJsonActivityResultLauncher = registerForActivityResult(GetContent()) { uri: Uri? ->
                if (uri == null) toast(R.string.error_no_file_selected)
                else lifecycleScope.launch { importData(uri) }
            }
            initCommonUtilsPreferences()
            initPreferences()
        }

        private fun initPreferences() {
            lifecycleScope.launch {
                val userSettings = getUserSettings()

                findPreference<DropDownPreference>("error_limit_pref")?.apply {
                    summary = if (userSettings.errorLimit == 0) getString(R.string.no_limit) else userSettings.errorLimit.toString()
                    onPrefChange { newValue: String ->
                        lifecycleScope.launch { updateUserSettings { it.copy(errorLimit = newValue.toIntOrNull() ?: 0) } }
                        summary = if (newValue.toIntOrNull() == 0) getString(R.string.no_limit) else newValue
                    }
                } ?: Log.e(TAG, "error limit Preference not found")

                findPreference<SwitchPreferenceCompat>("keep_screen_on_pref")?.apply {
                    isChecked = userSettings.keepScreenOn
                    onPrefChange { v: Boolean -> lifecycleScope.launch { updateUserSettings { it.copy(keepScreenOn = v) } } }
                } ?: Log.e(TAG, "keep screen on Preference not found")

                findPreference<SwitchPreferenceCompat>("highlight_regional_pref")?.apply {
                    isChecked = userSettings.highlightRegional
                    onPrefChange { v: Boolean -> lifecycleScope.launch { updateUserSettings { it.copy(highlightRegional = v) } } }
                } ?: Log.e(TAG, "regional highlight Preference not found")

                findPreference<SwitchPreferenceCompat>("highlight_number_pref")?.apply {
                    isChecked = userSettings.highlightNumber
                    onPrefChange { v: Boolean -> lifecycleScope.launch { updateUserSettings { it.copy(highlightNumber = v) } } }
                } ?: Log.e(TAG, "number highlight Preference not found")

                findPreference<SwitchPreferenceCompat>("animations_pref")?.apply {
                    isChecked = userSettings.animationsEnabled
                    onPrefChange { v: Boolean -> lifecycleScope.launch { updateUserSettings { it.copy(animationsEnabled = v) } } }
                } ?: Log.e(TAG, "animations Preference not found")

                findPreference<SeslSwitchPreferenceScreen>("daily_notification_pref")?.apply {
                    isChecked = userSettings.dailySudokuNotificationEnabled &&
                            areNotificationsEnabled(getString(R.string.daily_sudoku_notification_channel_id))
                    setDailyNotificationPrefTime(userSettings.dailySudokuNotificationHour, userSettings.dailySudokuNotificationMinute)
                    onPrefChange { newValue: Boolean ->
                        when {
                            !newValue -> setDailySudokuNotification(false)
                            SDK_INT >= TIRAMISU &&
                                    checkSelfPermission(requireContext(), POST_NOTIFICATIONS) != PERMISSION_GRANTED -> {
                                requestPermissionLauncher.launch(POST_NOTIFICATIONS)
                                isChecked = false
                            }

                            !areNotificationsEnabled(getString(R.string.daily_sudoku_notification_channel_id)) -> {
                                val settingsIntent = Intent(ACTION_APP_NOTIFICATION_SETTINGS)
                                    .addFlags(FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra(EXTRA_APP_PACKAGE, requireContext().packageName)
                                //.putExtra(Settings.EXTRA_CHANNEL_ID, getString(R.string.daily_sudoku_notification_channel_id))
                                startActivity(settingsIntent)
                                isChecked = false
                            }

                            else -> setDailySudokuNotification(true)
                        }
                    }
                    onPreferenceClickListener = OnPreferenceClickListener {
                        lifecycleScope.launch {
                            isChecked = true
                            onPreferenceChangeListener?.onPreferenceChange(this@apply, true)
                            val userSettings = getUserSettings()
                            val dialog = SeslTimePickerDialog(
                                requireContext(),
                                { _: SeslTimePicker?, hourOfDay: Int, minute: Int ->
                                    lifecycleScope.launch {
                                        updateUserSettings {
                                            it.copy(dailySudokuNotificationHour = hourOfDay, dailySudokuNotificationMinute = minute)
                                        }
                                        setDailyNotificationPrefTime(hourOfDay, minute)
                                        setDailySudokuNotification(true)
                                    }
                                },
                                userSettings.dailySudokuNotificationHour,
                                userSettings.dailySudokuNotificationMinute,
                                is24HourFormat(requireContext())
                            )
                            dialog.show()
                        }
                        true
                    }
                } ?: Log.e(TAG, "daily notification Preference not found")

                findPreference<PreferenceScreen>("intro_pref")?.setOnPreferenceClickListener {
                    startActivity(Intent(requireContext(), IntroActivity::class.java).putExtra("openedFromSettings", true))
                    true
                }

                findPreference<PreferenceScreen>("export_data_pref")?.setOnPreferenceClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.export_data)
                        .setMessage(R.string.export_data_message)
                        .setNegativeButton(designR.string.oui_des_common_cancel, null)
                        .setPositiveButton(commonutilsR.string.commonutils_ok) { _: DialogInterface, _: Int ->
                            pickExportFolderActivityResultLauncher.launch(Uri.fromFile(File(getExternalStorageDirectory().absolutePath)))
                        }
                        .show()
                    true
                }

                findPreference<PreferenceScreen>("import_data_pref")?.setOnPreferenceClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.import_data)
                        .setMessage(R.string.import_data_message)
                        .setNegativeButton(designR.string.oui_des_common_cancel, null)
                        .setPositiveButton(commonutilsR.string.commonutils_ok) { _: DialogInterface, _: Int ->
                            pickImportJsonActivityResultLauncher.launch("application/json")
                        }
                        .show()
                    true
                }

                findPreference<PreferenceScreen>("delete_invalid_sudokus_pref")?.setOnPreferenceClickListener {
                    val dialog = AlertDialog.Builder(requireContext())
                        .setTitle(R.string.delete_invalid_sudokus)
                        .setMessage(R.string.delete_invalid_sudokus_summary)
                        .setNegativeButton(designR.string.oui_des_common_cancel, null)
                        .setPositiveButton(R.string.commonutils_delete, null)
                        .create()
                    dialog.show()
                    dialog.getButton(BUTTON_POSITIVE).apply {
                        setTextColor(requireContext().getColor(designR.color.oui_des_functional_red_color))
                        setOnClickListenerWithProgress { button, progressBar ->
                            lifecycleScope.launch {
                                deleteInvalidSudokus()
                                delay(500)
                                dialog.dismiss()
                            }
                        }
                    }
                    true
                }
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            addRelativeLinksCard(
                RelativeLink(getString(R.string.commonutils_share_app)) {
                    PlayGames.getAchievementsClient(requireActivity()).unlock(getString(R.string.achievement_share_app))
                    shareApp()
                },
                RelativeLink(getString(R.string.commonutils_rate_app)) { openApp(requireContext().packageName, false) }
            )
        }

        private fun SeslSwitchPreferenceScreen.setDailyNotificationPrefTime(hourOfDay: Int, minute: Int) {
            summary = getString(
                R.string.daily_sudoku_notification_channel_description_time,
                DateFormat.format(if (is24HourFormat(requireContext())) "HH:mm" else "h:mm a", Calendar.getInstance().apply {
                    set(HOUR_OF_DAY, hourOfDay)
                    set(MINUTE, minute)
                })
            )
        }

        private val requestPermissionLauncher =
            registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your app.
                    setDailySudokuNotification(true)
                } else {
                    // Explain to the user that the feature is unavailable because the features requires a permission that the user has denied.
                    // At the same time, respect the user's decision. Don't link to system settings in an effort to convince the user
                    // to change their decision.
                    setDailySudokuNotification(false)
                }
            }

        private fun setDailySudokuNotification(enabled: Boolean) {
            lifecycleScope.launch {
                updateUserSettings { it.copy(dailySudokuNotificationEnabled = enabled) }
                sendDailyNotification.setDailySudokuNotification(enable = enabled)
            }
        }

        private fun areNotificationsEnabled(
            channelId: String,
            notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(requireContext()),
        ) = when {
            !notificationManager.areNotificationsEnabled() -> false
            notificationManager.getNotificationChannel(channelId)?.importance == IMPORTANCE_NONE -> false
            SDK_INT >= TIRAMISU -> checkSelfPermission(requireContext(), POST_NOTIFICATIONS) == PERMISSION_GRANTED
            else -> true
        }
    }
}

