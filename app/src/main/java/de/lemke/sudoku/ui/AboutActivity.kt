package de.lemke.sudoku.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.commonutils.openApp
import de.lemke.commonutils.prepareActivityTransformationTo
import de.lemke.commonutils.setCustomBackPressAnimation
import de.lemke.sudoku.BuildConfig
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityAboutBinding
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import de.lemke.sudoku.domain.openURL
import dev.oneuiproject.oneui.layout.AppInfoLayout.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AboutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutBinding
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var appUpdateInfo: AppUpdateInfo
    private lateinit var appUpdateInfoTask: Task<AppUpdateInfo>
    private lateinit var activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var clicks = 0

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareActivityTransformationTo()
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCustomBackPressAnimation(binding.root)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        binding.appInfoLayout.updateStatus = Status.Loading
        setExtraText()
        setVersionText()
        binding.appInfoLayout.setMainButtonClickListener(object : OnClickListener {
            override fun onUpdateClicked(v: View) {
                startUpdateFlow()
            }

            override fun onRetryClicked(v: View) {
                binding.appInfoLayout.updateStatus = Status.Loading
                checkUpdate()
            }
        })
        binding.aboutButtonOpenInStore.setOnClickListener { openApp(packageName, false) }
        binding.aboutButtonOpenSourceLicenses.setOnClickListener { startActivity(Intent(this, OssLicensesMenuActivity::class.java)) }
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            when (result.resultCode) {
                // For immediate updates, you might not receive RESULT_OK because
                // the update should already be finished by the time control is given back to your app.
                RESULT_OK -> Log.d("InAppUpdate", "Update successful")
                RESULT_CANCELED -> Log.w("InAppUpdate", "Update canceled")
                ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> Log.e("InAppUpdate", "Update failed")
            }
        }
        checkUpdate()
    }

    private fun setVersionText() {
        val version: TextView = binding.appInfoLayout.findViewById(dev.oneuiproject.oneui.design.R.id.app_info_version)
        lifecycleScope.launch { setVersionTextView(version, getUserSettings().devModeEnabled) }
        version.setOnClickListener {
            clicks++
            if (clicks > 5) {
                clicks = 0
                lifecycleScope.launch {
                    val newDevModeEnabled = !getUserSettings().devModeEnabled
                    updateUserSettings { it.copy(devModeEnabled = newDevModeEnabled) }
                    setVersionTextView(version, newDevModeEnabled)
                }
            }
        }
    }

    private fun setVersionTextView(textView: TextView, devModeEnabled: Boolean) {
        lifecycleScope.launch {
            textView.text = getString(
                dev.oneuiproject.oneui.design.R.string.version_info, BuildConfig.VERSION_NAME + if (devModeEnabled) " (dev)" else ""
            )
        }
    }

    private fun setExtraText() {
        val bib = getString(R.string.sudoku_lib)
        val license = getString(R.string.sudoku_lib_license)
        val text = getString(R.string.about_page_optional_text) + "\n" +
                getString(R.string.sudoku_lib_license_text, bib, license)
        val textLink = SpannableString(text)
        textLink.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openURL(getString(R.string.sudoku_lib_github_link))
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
            },
            text.indexOf(bib), text.indexOf(bib) + bib.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        textLink.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openURL(getString(R.string.sudoku_lib_license_github_link))
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
            },
            text.indexOf(license), text.indexOf(license) + license.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val optionalTextView = binding.appInfoLayout.addOptionalText("")
        optionalTextView.text = textLink
        optionalTextView.movementMethod = LinkMovementMethod.getInstance()
        optionalTextView.highlightColor = Color.TRANSPARENT
        optionalTextView.setLinkTextColor(getColor(R.color.primary_color_themed))
    }

    // Checks that the update is not stalled during 'onResume()'.
    // However, you should execute this check at all entry points into the app.
    override fun onResume() {
        super.onResume()
        appUpdateManager
            .appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    // If an in-app update is already running, resume the update.
                    startUpdateFlow()
                }
            }
    }

    private fun checkUpdate() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            binding.appInfoLayout.updateStatus = Status.NoConnection
            return
        }

        // Returns an intent object that you use to check for an update.
        appUpdateInfoTask = appUpdateManager.appUpdateInfo
        // Checks that the platform will allow the specified type of update.
        appUpdateInfoTask
            .addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
                this.appUpdateInfo = appUpdateInfo
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    binding.appInfoLayout.updateStatus = Status.UpdateAvailable
                }
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_NOT_AVAILABLE) {
                    binding.appInfoLayout.updateStatus = Status.NoUpdate
                }
            }
            .addOnFailureListener { appUpdateInfo: Exception ->
                binding.appInfoLayout.updateStatus = Status.NotUpdatable
                Log.w("AboutActivity", appUpdateInfo.message.toString())
            }
    }

    private fun startUpdateFlow() {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activityResultLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
            )
        } catch (e: Exception) {
            binding.appInfoLayout.updateStatus = Status.NotUpdatable
            e.printStackTrace()
        }
    }
}