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
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.util.SeslMisc
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import androidx.preference.Preference.OnPreferenceClickListener
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivitySettingsBinding
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.preference.HorizontalRadioPreference
import dev.oneuiproject.oneui.preference.internal.PreferenceRelatedCard
import dev.oneuiproject.oneui.utils.PreferenceUtils.createRelatedCard
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_navigate_up))
        binding.toolbarLayout.setNavigationButtonIcon(getDrawable(dev.oneuiproject.oneui.R.drawable.ic_oui_back))
        binding.toolbarLayout.setNavigationButtonOnClickListener { finish() }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction().replace(R.id.settings, SettingsFragment()).commit()
    }

    @AndroidEntryPoint
    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
        private lateinit var settingsActivity: SettingsActivity
        private lateinit var darkModePref: HorizontalRadioPreference
        private lateinit var autoDarkModePref: SwitchPreferenceCompat
        private lateinit var confirmExitPref: SwitchPreferenceCompat
        private lateinit var regionalHighlightPref: SwitchPreferenceCompat
        private lateinit var numberHighlightPref: SwitchPreferenceCompat
        private lateinit var animationsPref: SwitchPreferenceCompat
        private lateinit var dailySudokuNotificationPref: SwitchPreferenceCompat
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
            initPreferences()
        }

        @SuppressLint("RestrictedApi", "UnspecifiedImmutableFlag")
        private fun initPreferences() {
            val darkMode = AppCompatDelegate.getDefaultNightMode()
            darkModePref = findPreference("dark_mode_pref")!!
            autoDarkModePref = findPreference("dark_mode_auto")!!
            confirmExitPref = findPreference("confirmExit")!!
            regionalHighlightPref = findPreference("highlightRegionalSwitch")!!
            numberHighlightPref = findPreference("highlightNumberSwitch")!!
            animationsPref = findPreference("animationsSwitch")!!
            dailySudokuNotificationPref = findPreference("dailyNotificationSwitch")!!
            errorLimitPref = findPreference("errorLimitDropDown")!!
            confirmExitPref.onPreferenceChangeListener = this
            regionalHighlightPref.onPreferenceChangeListener = this
            numberHighlightPref.onPreferenceChangeListener = this
            animationsPref.onPreferenceChangeListener = this
            dailySudokuNotificationPref.onPreferenceChangeListener = this
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
            findPreference<PreferenceScreen>("privacy")!!.onPreferenceClickListener =
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
                findPreference<Preference>("about_app")?.widgetLayoutResource =
                    if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) R.layout.sesl_preference_badge else 0
            }
            lifecycleScope.launch {
                if (!getUserSettings().devModeEnabled) preferenceScreen.removePreference(findPreference("dev_options"))
            }
            findPreference<PreferenceScreen>("delete_app_data")?.setOnPreferenceClickListener {
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
                errorLimitPref.summary =
                    if (userSettings.errorLimit == 0) getString(R.string.no_limit) else userSettings.errorLimit.toString()
                //tipCard?.isVisible = showTipCard
                //tipCardSpacing?.isVisible = showTipCard
            }
            setRelatedCardView()
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
                "confirmExit" -> {
                    lifecycleScope.launch { updateUserSettings { it.copy(confirmExit = newValue as Boolean) } }
                    return true
                }
                "highlightRegionalSwitch" -> {
                    lifecycleScope.launch { updateUserSettings { it.copy(highlightRegional = newValue as Boolean) } }
                    return true
                }
                "highlightNumberSwitch" -> {
                    lifecycleScope.launch { updateUserSettings { it.copy(highlightNumber = newValue as Boolean) } }
                    return true
                }
                "animationsSwitch" -> {
                    lifecycleScope.launch { updateUserSettings { it.copy(animationsEnabled = newValue as Boolean) } }
                    return true
                }
                "dailyNotificationSwitch" -> {
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
                "errorLimitDropDown" -> {
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

        private fun setDailySudokuNotification(enabled:Boolean) {
            lifecycleScope.launch { updateUserSettings { it.copy(dailySudokuNotificationEnabled = enabled) } }
            sendDailyNotification.setDailySudokuNotification(enable = enabled)
        }

        private fun areNotificationsEnabled(
            channelId: String? = null,
            notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(requireContext())
        ): Boolean =
            notificationManager.areNotificationsEnabled() &&
                    if (channelId != null) {
                        notificationManager.getNotificationChannel(channelId)?.importance != NotificationManagerCompat.IMPORTANCE_NONE &&
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    ContextCompat.checkSelfPermission(
                                        requireContext(),
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                } else true
                    } else true

    }
}

