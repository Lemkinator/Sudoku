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
import de.lemke.sudoku.databinding.ActivitySplashBinding
import de.lemke.sudoku.domain.AppStart
import de.lemke.sudoku.domain.CheckAppStartUseCase
import de.lemke.sudoku.domain.GetUserSettingsUseCase
import de.lemke.sudoku.domain.UpdateUserSettingsUseCase
import dev.oneuiproject.oneui.layout.SplashLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private var launchCanceled = false

    @Inject
    lateinit var updateUserSettings: UpdateUserSettingsUseCase

    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    @Inject
    lateinit var checkAppStart: CheckAppStartUseCase

    lateinit var appStart: AppStart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lifecycleScope.launch {
            appStart = checkAppStart()
            if (getUserSettings().devModeEnabled) {
                val devText: Spannable = SpannableString(" Dev")
                devText.setSpan(
                    ForegroundColorSpan(getColor(dev.oneuiproject.oneui.R.color.oui_functional_orange_color)),
                    0,
                    devText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.splashLayout.findViewById<TextView>(dev.oneuiproject.oneui.R.id.oui_splash_text).append(devText)
            }
        }
        binding.splashLayout.setSplashAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationRepeat(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                if (!launchCanceled) lifecycleScope.launch { launchApp() }
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
            binding.splashLayout.startSplashAnimation()
        }
    }

    private suspend fun launchApp() {
        if (!getUserSettings().tosAccepted) {
            startActivity(Intent(applicationContext, OOBEActivity::class.java))
        }
        else {
            when (appStart) {
                AppStart.FIRST_TIME, AppStart.NEW_TERMS_OF_USE -> {
                    startActivity(Intent(applicationContext, OOBEActivity::class.java))
                }
                AppStart.NORMAL, AppStart.FIRST_TIME_VERSION -> {
                    startActivity(Intent(applicationContext, MainActivity::class.java))
                }
            }
        }
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}