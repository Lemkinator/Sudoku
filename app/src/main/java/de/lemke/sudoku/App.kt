package de.lemke.sudoku

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.lemke.commonutils.data.initCommonUtilsSettingsAndSetDarkMode
import de.lemke.commonutils.setupOnboarding
import de.lemke.sudoku.ui.IntroActivity

/**
 * Main entry point into the application process.
 * Registered in the AndroidManifest.xml file.
 */
@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        initCommonUtilsSettingsAndSetDarkMode()
        setupOnboarding(listOf(IntroActivity::class.java))
    }
}

