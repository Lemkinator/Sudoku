package de.lemke.sudoku.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.databinding.ActivityOobeBinding
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.SendDailyNotificationUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import de.lemke.sudoku.ui.utils.TipsItemView
import dev.oneuiproject.oneui.utils.DialogUtils
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class OOBEActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOobeBinding
    private lateinit var toSDialog: AlertDialog
    private var time: Long = 0

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var sendDailyNotification: SendDailyNotificationUseCase


    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOobeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initOnBackPressed()
        initTipsItems()
        initToSView()
        initFooterButton()
    }

    private fun initOnBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                if (System.currentTimeMillis() - time < 3000) finishAffinity()
                else {
                    Toast.makeText(this@OOBEActivity, resources.getString(R.string.press_again_to_exit), Toast.LENGTH_SHORT).show()
                    time = System.currentTimeMillis()
                }
            }
        else onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (System.currentTimeMillis() - time < 3000) finishAffinity()
                else {
                    Toast.makeText(this@OOBEActivity, resources.getString(R.string.press_again_to_exit), Toast.LENGTH_SHORT).show()
                    time = System.currentTimeMillis()
                }
            }
        })
    }

    private fun initTipsItems() {
        val defaultLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val titles = arrayOf(R.string.oobe_onboard_msg1_title, R.string.oobe_onboard_msg2_title, R.string.oobe_onboard_msg3_title)
        val summaries = arrayOf(R.string.oobe_onboard_msg1_summary, R.string.oobe_onboard_msg2_summary, R.string.oobe_onboard_msg3_summary)
        val icons = arrayOf(
            dev.oneuiproject.oneui.R.drawable.ic_oui_palette,
            dev.oneuiproject.oneui.R.drawable.ic_oui_credit_card_outline,
            dev.oneuiproject.oneui.R.drawable.ic_oui_decline
        )
        for (i in titles.indices) {
            val item = TipsItemView(this)
            item.setIcon(icons[i])
            item.setTitleText(getString(titles[i]))
            item.setSummaryText(getString(summaries[i]))
            binding.oobeIntroTipsContainer.addView(item, defaultLp)
        }
    }

    private fun initToSView() {
        val tos = getString(R.string.tos)
        val tosText = getString(R.string.oobe_tos_text, tos)
        val tosLink = SpannableString(tosText)
        tosLink.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    toSDialog.show()
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
            },
            tosText.indexOf(tos), tosText.length - if (Locale.getDefault().language == "de") 4 else 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.oobeIntroFooterTosText.text = tosLink
        binding.oobeIntroFooterTosText.movementMethod = LinkMovementMethod.getInstance()
        binding.oobeIntroFooterTosText.highlightColor = Color.TRANSPARENT
        initToSDialog()
    }

    private fun initToSDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.tos))
        builder.setMessage(getString(R.string.tos_content))
        builder.setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        toSDialog = builder.create()
    }

    private fun initFooterButton() {
        if (resources.configuration.screenWidthDp < 360) {
            binding.oobeIntroFooterButton.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.oobeIntroFooterButton.setOnClickListener {
            binding.oobeIntroFooterTosText.isEnabled = false
            binding.oobeIntroFooterButton.visibility = View.GONE
            binding.oobeIntroFooterButtonProgress.visibility = View.VISIBLE
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
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
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
            .setPositiveButton(R.string.ok) { _: DialogInterface, _: Int ->
                lifecycleScope.launch {
                    //Enable Notifications when < Android 13 or permission is granted, else ask for permission
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(
                            this@OOBEActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        updateUserSettings { it.copy(dailySudokuNotificationEnabled = true) }
                        openIntroActivity()
                    } else requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setCancelable(false)
            .create()
        dialog.show()
        DialogUtils.setDialogButtonTextColor(
            dialog,
            DialogInterface.BUTTON_NEGATIVE,
            getColor(dev.oneuiproject.oneui.design.R.color.oui_functional_red_color)
        )
    }


    private suspend fun openIntroActivity() {
        sendDailyNotification.setDailySudokuNotification(enable = getUserSettings().dailySudokuNotificationEnabled)
        startActivity(Intent(applicationContext, IntroActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}