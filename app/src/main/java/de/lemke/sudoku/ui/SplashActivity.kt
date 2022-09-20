package de.lemke.sudoku.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.animation.Animation
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.layout.SplashLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private lateinit var splashView: SplashLayout
    private var launchCanceled = false

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        splashView = findViewById(R.id.splash)
        lifecycleScope.launch {
            //TODO splashView.text = getString(R.string.app_name)
            if (getUserSettings().devModeEnabled) {
                val devText: Spannable = SpannableString(" Dev")
                devText.setSpan(
                    ForegroundColorSpan(getColor(dev.oneuiproject.oneui.R.color.oui_functional_orange_color)),
                    0,
                    devText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                splashView.findViewById<TextView>(dev.oneuiproject.oneui.R.id.oui_splash_text).append(devText)
            }
        }
        splashView.setSplashAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationRepeat(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                if (!launchCanceled) launchApp()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        launchCanceled = true
    }

    override fun onResume() {
        super.onResume()
        launchCanceled = false
        lifecycleScope.launch {
            delay(400)
            splashView.startSplashAnimation()
        }
    }

    private fun launchApp() {
        startActivity(Intent().setClass(applicationContext, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}