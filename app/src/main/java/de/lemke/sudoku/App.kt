/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.sudoku

import android.app.Application
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.Typeface.NORMAL
import android.text.SpannableString
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import com.google.android.gms.games.PlayGames
import dagger.hilt.android.HiltAndroidApp
import de.lemke.commonutils.data.applyDarkMode
import de.lemke.commonutils.ui.utils.openURL
import de.lemke.commonutils.ui.utils.setupCommonUtilsAboutActivity
import de.lemke.commonutils.ui.utils.setupCommonUtilsAboutMeActivity
import de.lemke.commonutils.ui.utils.setupOnboarding
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.ui.IntroActivity
import javax.inject.Inject

/**
 * Main entry point into the application process.
 * Registered in the AndroidManifest.xml file.
 */
@HiltAndroidApp
class App : Application() {
    @Inject
    lateinit var settings: UserSettings

    override fun onCreate() {
        super.onCreate()
        settings.applyDarkMode()
        setupOnboarding(listOf(IntroActivity::class.java))
        setupAboutScreens()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupAboutScreens()
    }

    private fun setupAboutScreens() {
        setupCommonUtilsAboutActivity(appVersion = BuildConfig.VERSION_NAME, optionalText = aboutText())
        setupCommonUtilsAboutMeActivity(
            onShareApp = { activity ->
                PlayGames.getAchievementsClient(activity).unlock(activity.getString(R.string.achievement_share_app))
            },
        )
    }

    private fun aboutText(): SpannableString {
        val library = getString(R.string.sudoku_lib)
        val license = getString(R.string.sudoku_lib_license)
        val text = getString(R.string.app_description) + "\n" + getString(R.string.sudoku_lib_license_text, library, license)
        return SpannableString(text).apply {
            linkSpan(text, library, getString(R.string.sudoku_lib_github_link))
            linkSpan(text, license, getString(R.string.sudoku_lib_license_github_link))
        }
    }

    private fun SpannableString.linkSpan(
        text: String,
        label: String,
        url: String,
    ) {
        val start = text.indexOf(label)
        setSpan(LinkSpan(url), start, start + label.length, SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private class LinkSpan(
        private val url: String,
    ) : ClickableSpan() {
        override fun onClick(widget: View) {
            widget.context.openURL(url)
        }

        override fun updateDrawState(ds: TextPaint) {
            super.updateDrawState(ds)
            ds.typeface = Typeface.create("sans-serif-medium", NORMAL)
        }
    }
}
