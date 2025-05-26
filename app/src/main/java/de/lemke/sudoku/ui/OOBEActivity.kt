package de.lemke.sudoku.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.R.anim.fade_in
import android.R.anim.fade_out
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Color
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout.LayoutParams
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityOobeBinding
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.widget.OnboardingTipsItemView
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import dev.oneuiproject.oneui.R as oneuiR
import dev.oneuiproject.oneui.design.R as designR

@AndroidEntryPoint
class OOBEActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOobeBinding

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var sendDailyNotification: SendDailyNotificationUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, fade_in, fade_out)
        binding = ActivityOobeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initTipsItems()
        initToSView()
        initFooterButton()
    }

    private fun initTipsItems() {
        val tipsData = listOf(
            Triple(R.string.oobe_onboard_msg1_title, R.string.oobe_onboard_msg1_summary, oneuiR.drawable.ic_oui_palette),
            Triple(R.string.oobe_onboard_msg2_title, R.string.oobe_onboard_msg2_summary, oneuiR.drawable.ic_oui_credit_card_outline),
            Triple(R.string.oobe_onboard_msg3_title, R.string.oobe_onboard_msg3_summary, oneuiR.drawable.ic_oui_decline)
        )
        tipsData.forEach { (titleRes, summaryRes, iconRes) ->
            OnboardingTipsItemView(this).apply {
                setIcon(iconRes)
                title = getString(titleRes)
                summary = getString(summaryRes)
                binding.oobeIntroTipsContainer.addView(this, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
        }
    }

    private fun initToSView() {
        val tos = getString(R.string.tos)
        val tosText = getString(R.string.oobe_tos_text, tos)
        val tosLink = SpannableString(tosText)
        tosLink.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    AlertDialog.Builder(this@OOBEActivity)
                        .setTitle(getString(R.string.tos))
                        .setMessage(getString(R.string.tos_content))
                        .setPositiveButton(de.lemke.commonutils.R.string.ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                        .show()
                }
            },
            tosText.indexOf(tos), tosText.length - if (Locale.getDefault().language == "de") 4 else 1,
            SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.oobeIntroFooterTosText.text = tosLink
        binding.oobeIntroFooterTosText.movementMethod = LinkMovementMethod.getInstance()
        binding.oobeIntroFooterTosText.highlightColor = Color.TRANSPARENT
    }

    private fun initFooterButton() {
        if (resources.configuration.screenWidthDp < 360) binding.oobeIntroFooterButton.layoutParams.width = MATCH_PARENT
        binding.oobeIntroFooterButton.setOnClickListener {
            binding.oobeIntroFooterTosText.isEnabled = false
            binding.oobeIntroFooterButton.isVisible = false
            binding.oobeIntroFooterButtonProgress.isVisible = true
            lifecycleScope.launch {
                //updateUserSettings { it.copy(tosAccepted = true) }
                //set in IntroActivity: in case user exits before completing intro, oobe and intro will be shown again
                notificationsDialog()
            }
        }
    }

    // Register the permissions callback, which handles the user's response to the system permissions dialog. Save the return value,
    // an instance of ActivityResultLauncher. You can use either a val, as shown in this snippet,
    // or a lateinit var in your onAttach() or onCreate() method.
    private val requestPermissionLauncher = registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
        lifecycleScope.launch {
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your app.
                updateUserSettings { it.copy(dailySudokuNotificationEnabled = true) }
            } else {
                // Explain to the user that the feature is unavailable because the features requires a permission that the user has denied.
                // At the same time, respect the user's decision. Don't link to system settings in an effort to convince the user
                // to change their decision.
                updateUserSettings { it.copy(dailySudokuNotificationEnabled = false) }
            }
            openIntroActivity()
        }
    }

    @SuppressLint("InlinedApi")
    private fun notificationsDialog() {
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.notifications_title))
            .setMessage(getString(R.string.daily_sudoku_notification_channel_description))
            .setNegativeButton(R.string.decline_notifications) { _: DialogInterface, _: Int ->
                lifecycleScope.launch {
                    updateUserSettings { it.copy(dailySudokuNotificationEnabled = false) }
                    openIntroActivity()
                }
            }
            .setPositiveButton(de.lemke.commonutils.R.string.ok) { _: DialogInterface, _: Int ->
                lifecycleScope.launch {
                    //Enable Notifications when < Android 13 or permission is granted, else ask for permission
                    if (SDK_INT < TIRAMISU || checkSelfPermission(this@OOBEActivity, POST_NOTIFICATIONS) == PERMISSION_GRANTED) {
                        updateUserSettings { it.copy(dailySudokuNotificationEnabled = true) }
                        openIntroActivity()
                    } else requestPermissionLauncher.launch(POST_NOTIFICATIONS)
                }
            }
            .setCancelable(false)
            .create()
        dialog.show()
        dialog.getButton(BUTTON_NEGATIVE).setTextColor(getColor(designR.color.oui_des_functional_red_color))
    }


    private suspend fun openIntroActivity() {
        sendDailyNotification.setDailySudokuNotification(enable = getUserSettings().dailySudokuNotificationEnabled)
        startActivity(Intent(applicationContext, IntroActivity::class.java))
        @Suppress("DEPRECATION") if (SDK_INT < 34) overridePendingTransition(fade_in, fade_out)
        finishAfterTransition()
    }
}