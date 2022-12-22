package de.lemke.sudoku.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.util.SeslMisc
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.picker.app.SeslTimePickerDialog
import androidx.picker.widget.SeslTimePicker
import androidx.preference.*
import androidx.preference.Preference.OnPreferenceClickListener
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivitySettingsBinding
import de.lemke.sudoku.domain.*
import dev.oneuiproject.oneui.preference.HorizontalRadioPreference
import dev.oneuiproject.oneui.preference.internal.PreferenceRelatedCard
import dev.oneuiproject.oneui.utils.PreferenceUtils.createRelatedCard
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        binding.toolbarLayout.setNavigationButtonOnClickListener { finish() }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction().replace(R.id.settings, SettingsFragment()).commit()
    }

    @AndroidEntryPoint
    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
        private lateinit var settingsActivity: SettingsActivity
        private lateinit var pickExportFolderActivityResultLauncher: ActivityResultLauncher<Uri>
        private lateinit var pickImportJsonActivityResultLauncher: ActivityResultLauncher<String>
        private lateinit var darkModePref: HorizontalRadioPreference
        private lateinit var autoDarkModePref: SwitchPreferenceCompat
        private lateinit var confirmExitPref: SwitchPreferenceCompat
        private lateinit var regionalHighlightPref: SwitchPreferenceCompat
        private lateinit var numberHighlightPref: SwitchPreferenceCompat
        private lateinit var animationsPref: SwitchPreferenceCompat
        private lateinit var dailySudokuNotificationPref: SeslSwitchPreferenceScreen
        private lateinit var errorLimitPref: DropDownPreference

        //private var tipCard: TipsCardViewPreference? = null
        //private var tipCardSpacing: PreferenceCategory? = null
        private var relatedCard: PreferenceRelatedCard? = null
        private var lastTimeVersionClicked: Long = 0

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

        override fun onAttach(context: Context) {
            super.onAttach(context)
            if (activity is SettingsActivity) settingsActivity = activity as SettingsActivity
        }

        override fun onCreatePreferences(bundle: Bundle?, str: String?) {
            addPreferencesFromResource(R.xml.preferences)
        }

        override fun onCreate(bundle: Bundle?) {
            super.onCreate(bundle)
            lastTimeVersionClicked = System.currentTimeMillis()
            pickExportFolderActivityResultLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
                if (uri == null) Toast.makeText(settingsActivity, getString(R.string.error_no_folder_selected), Toast.LENGTH_LONG).show()
                else lifecycleScope.launch { exportData(uri) }
            }
            pickImportJsonActivityResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                if (uri == null) Toast.makeText(settingsActivity, getString(R.string.error_no_file_selected), Toast.LENGTH_LONG).show()
                else lifecycleScope.launch { importData(uri) }
            }
            initPreferences()
        }

        @SuppressLint("RestrictedApi", "UnspecifiedImmutableFlag")
        private fun initPreferences() {
            val darkMode = AppCompatDelegate.getDefaultNightMode()
            darkModePref = findPreference("dark_mode_pref")!!
            autoDarkModePref = findPreference("dark_mode_auto")!!
            confirmExitPref = findPreference("confirm_exit_pref")!!
            regionalHighlightPref = findPreference("highlight_regional_pref")!!
            numberHighlightPref = findPreference("highlight_number_pref")!!
            animationsPref = findPreference("animations_pref")!!
            dailySudokuNotificationPref = findPreference("daily_notification_pref")!!
            errorLimitPref = findPreference("error_limit_pref")!!
            confirmExitPref.onPreferenceChangeListener = this
            regionalHighlightPref.onPreferenceChangeListener = this
            numberHighlightPref.onPreferenceChangeListener = this
            animationsPref.onPreferenceChangeListener = this
            dailySudokuNotificationPref.onPreferenceChangeListener = this
            dailySudokuNotificationPref.onPreferenceClickListener = OnPreferenceClickListener {
                lifecycleScope.launch {
                    dailySudokuNotificationPref.isChecked = true
                    val userSettings = getUserSettings()
                    val dialog = SeslTimePickerDialog(
                        settingsActivity,
                        { _: SeslTimePicker?, hourOfDay: Int, minute: Int ->
                            lifecycleScope.launch {
                                updateUserSettings {
                                    it.copy(
                                        dailySudokuNotificationHour = hourOfDay,
                                        dailySudokuNotificationMinute = minute
                                    )
                                }
                                setDailyNotificationPrefTime(hourOfDay, minute)
                                setDailySudokuNotification(true)
                            }
                        },
                        userSettings.dailySudokuNotificationHour,
                        userSettings.dailySudokuNotificationMinute,
                        DateFormat.is24HourFormat(settingsActivity)
                    )
                    dialog.show()
                }
                true
            }
            errorLimitPref.onPreferenceChangeListener = this
            autoDarkModePref.onPreferenceChangeListener = this
            autoDarkModePref.isChecked = darkMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM ||
                    darkMode == AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY ||
                    darkMode == AppCompatDelegate.MODE_NIGHT_UNSPECIFIED

            darkModePref.onPreferenceChangeListener = this
            darkModePref.setDividerEnabled(false)
            darkModePref.setTouchEffectEnabled(false)
            darkModePref.isEnabled = !autoDarkModePref.isChecked
            darkModePref.value = if (SeslMisc.isLightTheme(settingsActivity)) "0" else "1"

            findPreference<PreferenceScreen>("export_data_pref")?.setOnPreferenceClickListener {
                AlertDialog.Builder(settingsActivity)
                    .setTitle(R.string.export_data)
                    .setMessage(R.string.export_data_message)
                    .setNegativeButton(R.string.sesl_cancel, null)
                    .setPositiveButton(R.string.ok) { _: DialogInterface, _: Int ->
                        pickExportFolderActivityResultLauncher.launch(Uri.fromFile(File(Environment.getExternalStorageDirectory().absolutePath)))
                    }
                    .create()
                    .show()
                true
            }
            findPreference<PreferenceScreen>("import_data_pref")?.setOnPreferenceClickListener {
                AlertDialog.Builder(settingsActivity)
                    .setTitle(R.string.import_data)
                    .setMessage(R.string.import_data_message)
                    .setNegativeButton(R.string.sesl_cancel, null)
                    .setPositiveButton(R.string.ok) { _: DialogInterface, _: Int ->
                        pickImportJsonActivityResultLauncher.launch("application/json")
                    }
                    .create()
                    .show()
                true
            }

            findPreference<PreferenceScreen>("privacy_pref")!!.onPreferenceClickListener =
                OnPreferenceClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_website))))
                    true
                }

            findPreference<PreferenceScreen>("tos_pref")!!.onPreferenceClickListener = OnPreferenceClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.tos))
                    .setMessage(getString(R.string.tos_content))
                    .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                    .create()
                    .show()
                true
            }

            AppUpdateManagerFactory.create(requireContext()).appUpdateInfo.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
                findPreference<Preference>("about_app_pref")?.widgetLayoutResource =
                    if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) R.layout.sesl_preference_badge else 0
            }
            lifecycleScope.launch {
                if (!getUserSettings().devModeEnabled) preferenceScreen.removePreference(findPreference("dev_options"))
            }
            findPreference<PreferenceScreen>("delete_app_data_pref")?.setOnPreferenceClickListener {
                AlertDialog.Builder(settingsActivity)
                    .setTitle(R.string.delete_appdata_and_exit)
                    .setMessage(R.string.delete_appdata_and_exit_warning)
                    .setNegativeButton(R.string.sesl_cancel, null)
                    .setPositiveButton(R.string.ok) { _: DialogInterface, _: Int ->
                        (settingsActivity.getSystemService(ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
                    }
                    .create()
                    .show()
                true
            }

            /*
            tipCard = findPreference("tip_card_preference")
            tipCardSpacing = findPreference("spacing_tip_card")
            tipCard?.setTipsCardListener(object : TipsCardViewPreference.TipsCardListener {
                override fun onCancelClicked(view: View) {
                    tipCard!!.isVisible = false
                    tipCardSpacing?.isVisible = false
                    lifecycleScope.launch {
                        val hints: MutableSet<String> = getHints().toMutableSet()
                        hints.remove("tipcard")
                        hintsPref.values = hints
                        setHints(hints)
                    }
                }

                override fun onViewClicked(view: View) {
                    startActivity(Intent(settingsActivity, HelpActivity::class.java))
                }
            })
            */
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            requireView().setBackgroundColor(
                resources.getColor(dev.oneuiproject.oneui.design.R.color.oui_background_color, settingsActivity.theme)
            )
        }

        override fun onStart() {
            super.onStart()
            lifecycleScope.launch {
                val userSettings = getUserSettings()
                confirmExitPref.isChecked = userSettings.confirmExit
                regionalHighlightPref.isChecked = userSettings.highlightRegional
                numberHighlightPref.isChecked = userSettings.highlightNumber
                animationsPref.isChecked = userSettings.animationsEnabled
                dailySudokuNotificationPref.isChecked =
                    userSettings.dailySudokuNotificationEnabled && areNotificationsEnabled(getString(R.string.daily_sudoku_notification_channel_id))
                setDailyNotificationPrefTime(userSettings.dailySudokuNotificationHour, userSettings.dailySudokuNotificationMinute)
                errorLimitPref.summary =
                    if (userSettings.errorLimit == 0) getString(R.string.no_limit) else userSettings.errorLimit.toString()
                //tipCard?.isVisible = showTipCard
                //tipCardSpacing?.isVisible = showTipCard
            }
            setRelatedCardView()
        }

        private fun setDailyNotificationPrefTime(hourOfDay: Int, minute: Int) {
            dailySudokuNotificationPref.summary = getString(
                R.string.daily_sudoku_notification_channel_description_time,
                DateFormat.format(if (DateFormat.is24HourFormat(settingsActivity)) "HH:mm" else "h:mm a", Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                })
            )
        }

        @SuppressLint("WrongConstant", "RestrictedApi")
        @Suppress("UNCHECKED_CAST")
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            when (preference.key) {
                "dark_mode_pref" -> {
                    AppCompatDelegate.setDefaultNightMode(
                        if (newValue == "0") AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
                    )
                    return true
                }
                "dark_mode_auto" -> {
                    if (newValue as Boolean) {
                        darkModePref.isEnabled = false
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    } else {
                        darkModePref.isEnabled = true
                        if (SeslMisc.isLightTheme(settingsActivity)) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    }
                    return true
                }
                "confirm_exit_pref" -> {
                    lifecycleScope.launch { updateUserSettings { it.copy(confirmExit = newValue as Boolean) } }
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
                "animations_pref" -> {
                    lifecycleScope.launch { updateUserSettings { it.copy(animationsEnabled = newValue as Boolean) } }
                    return true
                }
                "daily_notification_pref" -> {
                    if (newValue as Boolean) {
                        when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED -> {
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                dailySudokuNotificationPref.isChecked = false
                            }
                            !areNotificationsEnabled(getString(R.string.daily_sudoku_notification_channel_id)) -> {
                                val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, settingsActivity.packageName)
                                //.putExtra(Settings.EXTRA_CHANNEL_ID, getString(R.string.daily_sudoku_notification_channel_id))
                                startActivity(settingsIntent)
                                dailySudokuNotificationPref.isChecked = false
                            }
                            else -> {
                                setDailySudokuNotification(true)
                            }
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

        private fun setRelatedCardView() {
            if (relatedCard == null) {
                relatedCard = createRelatedCard(settingsActivity)
                relatedCard?.setTitleText(getString(dev.oneuiproject.oneui.design.R.string.oui_relative_description))
                relatedCard?.addButton(getString(R.string.about_me)) {
                    startActivity(
                        Intent(
                            settingsActivity,
                            AboutMeActivity::class.java
                        )
                    )
                }
                    ?.show(this)
            }
        }

        private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
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
            channelId: String? = null,
            notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(requireContext())
        ): Boolean =
            notificationManager.areNotificationsEnabled() && if (channelId != null) {
                notificationManager.getNotificationChannel(channelId)?.importance != NotificationManagerCompat.IMPORTANCE_NONE &&
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) ==
                                    PackageManager.PERMISSION_GRANTED
                        } else true
            } else true

    }
}

