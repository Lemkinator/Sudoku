package de.lemke.sudoku.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.content.Context
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
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_NONE
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.picker.app.SeslTimePickerDialog
import androidx.picker.widget.SeslTimePicker
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SeslSwitchPreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import com.google.android.gms.games.PlayGames
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.deleteAppDataAndExit
import de.lemke.commonutils.openApp
import de.lemke.commonutils.openAppLocaleSettings
import de.lemke.commonutils.openURL
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
import de.lemke.sudoku.domain.ObserveUserSettingsUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.ktx.addRelativeLinksCard
import dev.oneuiproject.oneui.ktx.setOnClickListenerWithProgress
import dev.oneuiproject.oneui.preference.HorizontalRadioPreference
import dev.oneuiproject.oneui.widget.RelativeLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import dev.oneuiproject.oneui.design.R as designR


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
    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
        private lateinit var settingsActivity: SettingsActivity
        private lateinit var pickExportFolderActivityResultLauncher: ActivityResultLauncher<Uri?>
        private lateinit var pickImportJsonActivityResultLauncher: ActivityResultLauncher<String>
        private lateinit var darkModePref: HorizontalRadioPreference
        private lateinit var autoDarkModePref: SwitchPreferenceCompat
        private lateinit var keepScreenOnPref: SwitchPreferenceCompat
        private lateinit var regionalHighlightPref: SwitchPreferenceCompat
        private lateinit var numberHighlightPref: SwitchPreferenceCompat
        private lateinit var animationsPref: SwitchPreferenceCompat
        private lateinit var dailySudokuNotificationPref: SeslSwitchPreferenceScreen
        private lateinit var errorLimitPref: DropDownPreference

        @Inject
        lateinit var observeUserSettings: ObserveUserSettingsUseCase

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

        override fun onAttach(context: Context) {
            super.onAttach(context)
            if (activity is SettingsActivity) settingsActivity = activity as SettingsActivity
        }

        override fun onCreatePreferences(bundle: Bundle?, str: String?) {
            addPreferencesFromResource(R.xml.preferences)
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
            initPreferences()
        }

        private fun initPreferences() {
            darkModePref = findPreference("dark_mode_pref")!!
            autoDarkModePref = findPreference("dark_mode_auto")!!
            keepScreenOnPref = findPreference("keep_screen_on_pref")!!
            regionalHighlightPref = findPreference("highlight_regional_pref")!!
            numberHighlightPref = findPreference("highlight_number_pref")!!
            animationsPref = findPreference("animations_pref")!!
            dailySudokuNotificationPref = findPreference("daily_notification_pref")!!
            errorLimitPref = findPreference("error_limit_pref")!!
            errorLimitPref.onPreferenceChangeListener = this
            autoDarkModePref.onPreferenceChangeListener = this
            darkModePref.onPreferenceChangeListener = this
            darkModePref.setDividerEnabled(false)
            darkModePref.setTouchEffectEnabled(false)
            keepScreenOnPref.onPreferenceChangeListener = this
            regionalHighlightPref.onPreferenceChangeListener = this
            numberHighlightPref.onPreferenceChangeListener = this
            animationsPref.onPreferenceChangeListener = this
            dailySudokuNotificationPref.onPreferenceChangeListener = this
            dailySudokuNotificationPref.onPreferenceClickListener = OnPreferenceClickListener {
                lifecycleScope.launch {
                    dailySudokuNotificationPref.isChecked = true
                    dailySudokuNotificationPref.onPreferenceChangeListener?.onPreferenceChange(dailySudokuNotificationPref, true)
                    val userSettings = getUserSettings()
                    val dialog = SeslTimePickerDialog(
                        settingsActivity,
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
                        is24HourFormat(settingsActivity)
                    )
                    dialog.show()
                }
                true
            }

            lifecycleScope.launch {
                observeUserSettings().flowWithLifecycle(lifecycle).collectLatest { userSettings ->
                    autoDarkModePref.isChecked = userSettings.autoDarkMode
                    darkModePref.isEnabled = !autoDarkModePref.isChecked
                    darkModePref.value = if (userSettings.darkMode) "1" else "0"
                    findPreference<PreferenceCategory>("dev_options")?.isVisible = userSettings.devModeEnabled
                    keepScreenOnPref.isChecked = userSettings.keepScreenOn
                    regionalHighlightPref.isChecked = userSettings.highlightRegional
                    numberHighlightPref.isChecked = userSettings.highlightNumber
                    animationsPref.isChecked = userSettings.animationsEnabled
                    dailySudokuNotificationPref.isChecked =
                        userSettings.dailySudokuNotificationEnabled && areNotificationsEnabled(getString(R.string.daily_sudoku_notification_channel_id))
                    setDailyNotificationPrefTime(userSettings.dailySudokuNotificationHour, userSettings.dailySudokuNotificationMinute)
                    errorLimitPref.summary =
                        if (userSettings.errorLimit == 0) getString(R.string.no_limit) else userSettings.errorLimit.toString()

                }
            }

            if (SDK_INT >= TIRAMISU) {
                findPreference<PreferenceScreen>("language_pref")!!.isVisible = true
                findPreference<PreferenceScreen>("language_pref")!!.onPreferenceClickListener = OnPreferenceClickListener {
                    openAppLocaleSettings()
                }
            }

            findPreference<PreferenceScreen>("intro_pref")?.setOnPreferenceClickListener {
                startActivity(Intent(settingsActivity, IntroActivity::class.java).putExtra("openedFromSettings", true))
                true
            }
            findPreference<PreferenceScreen>("export_data_pref")?.setOnPreferenceClickListener {
                AlertDialog.Builder(settingsActivity)
                    .setTitle(R.string.export_data)
                    .setMessage(R.string.export_data_message)
                    .setNegativeButton(designR.string.oui_des_common_cancel, null)
                    .setPositiveButton(de.lemke.commonutils.R.string.ok) { _: DialogInterface, _: Int ->
                        pickExportFolderActivityResultLauncher.launch(Uri.fromFile(File(getExternalStorageDirectory().absolutePath)))
                    }
                    .show()
                true
            }
            findPreference<PreferenceScreen>("import_data_pref")?.setOnPreferenceClickListener {
                AlertDialog.Builder(settingsActivity)
                    .setTitle(R.string.import_data)
                    .setMessage(R.string.import_data_message)
                    .setNegativeButton(designR.string.oui_des_common_cancel, null)
                    .setPositiveButton(de.lemke.commonutils.R.string.ok) { _: DialogInterface, _: Int ->
                        pickImportJsonActivityResultLauncher.launch("application/json")
                    }
                    .show()
                true
            }
            findPreference<PreferenceScreen>("delete_invalid_sudokus_pref")?.setOnPreferenceClickListener {
                val dialog = AlertDialog.Builder(settingsActivity)
                    .setTitle(R.string.delete_invalid_sudokus)
                    .setMessage(R.string.delete_invalid_sudokus_summary)
                    .setNegativeButton(designR.string.oui_des_common_cancel, null)
                    .setPositiveButton(R.string.delete, null)
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
            findPreference<PreferenceScreen>("delete_app_data_pref")?.setOnPreferenceClickListener { deleteAppDataAndExit() }

            findPreference<PreferenceScreen>("privacy_pref")!!.onPreferenceClickListener =
                OnPreferenceClickListener {
                    openURL(getString(R.string.privacy_website))
                    true
                }

            findPreference<PreferenceScreen>("tos_pref")!!.onPreferenceClickListener = OnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.tos))
                    .setMessage(getString(R.string.tos_content))
                    .setPositiveButton(de.lemke.commonutils.R.string.ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                    .show()
                true
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            addRelativeLinksCard(
                RelativeLink(getString(de.lemke.commonutils.R.string.share_app)) {
                    PlayGames.getAchievementsClient(settingsActivity).unlock(getString(R.string.achievement_share_app))
                    shareApp()
                },
                RelativeLink(getString(de.lemke.commonutils.R.string.rate_app)) { openApp(settingsActivity.packageName, false) }
            )
        }

        private fun setDailyNotificationPrefTime(hourOfDay: Int, minute: Int) {
            dailySudokuNotificationPref.summary = getString(
                R.string.daily_sudoku_notification_channel_description_time,
                DateFormat.format(if (is24HourFormat(settingsActivity)) "HH:mm" else "h:mm a", Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                })
            )
        }

        @SuppressLint("WrongConstant", "RestrictedApi")
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            when (preference.key) {
                "dark_mode_pref" -> {
                    val darkMode = newValue as String == "1"
                    setDefaultNightMode(if (darkMode) MODE_NIGHT_YES else MODE_NIGHT_NO)
                    lifecycleScope.launch { updateUserSettings { it.copy(darkMode = darkMode) } }
                    return true
                }

                "dark_mode_auto" -> {
                    val autoDarkMode = newValue as Boolean
                    darkModePref.isEnabled = !autoDarkMode
                    lifecycleScope.launch {
                        if (autoDarkMode) setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)
                        else {
                            if (getUserSettings().darkMode) setDefaultNightMode(MODE_NIGHT_YES)
                            else setDefaultNightMode(MODE_NIGHT_NO)
                        }
                        updateUserSettings { it.copy(autoDarkMode = newValue) }
                    }
                    return true
                }

                "highlight_regional_pref" -> {
                    lifecycleScope.launch { updateUserSettings { it.copy(highlightRegional = newValue as Boolean) } }
                    return true
                }

                "highlight_number_pref" -> {
                    lifecycleScope.launch { updateUserSettings { it.copy(highlightNumber = newValue as Boolean) } }
                    return true
                }

                "keep_screen_on_pref" -> {
                    lifecycleScope.launch { updateUserSettings { it.copy(keepScreenOn = newValue as Boolean) } }
                    return true
                }

                "animations_pref" -> {
                    lifecycleScope.launch { updateUserSettings { it.copy(animationsEnabled = newValue as Boolean) } }
                    return true
                }

                "daily_notification_pref" -> {
                    if (newValue as Boolean) {
                        when {
                            SDK_INT >= TIRAMISU && checkSelfPermission(requireContext(), POST_NOTIFICATIONS) != PERMISSION_GRANTED -> {
                                requestPermissionLauncher.launch(POST_NOTIFICATIONS)
                                dailySudokuNotificationPref.isChecked = false
                            }

                            !areNotificationsEnabled(getString(R.string.daily_sudoku_notification_channel_id)) -> {
                                val settingsIntent = Intent(ACTION_APP_NOTIFICATION_SETTINGS)
                                    .addFlags(FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra(EXTRA_APP_PACKAGE, settingsActivity.packageName)
                                //.putExtra(Settings.EXTRA_CHANNEL_ID, getString(R.string.daily_sudoku_notification_channel_id))
                                startActivity(settingsIntent)
                                dailySudokuNotificationPref.isChecked = false
                            }

                            else -> setDailySudokuNotification(true)
                        }
                    } else {
                        setDailySudokuNotification(false)
                    }
                    return true
                }

                "error_limit_pref" -> {
                    val errorLimit = (newValue as String).toIntOrNull() ?: 0
                    lifecycleScope.launch { updateUserSettings { it.copy(errorLimit = errorLimit) } }
                    errorLimitPref.summary = if (errorLimit == 0) getString(R.string.no_limit) else errorLimit.toString()
                    return true
                }
            }
            return false
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

