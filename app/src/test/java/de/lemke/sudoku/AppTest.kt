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

import android.app.Activity
import android.content.Context
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.games.AchievementsClient
import com.google.android.gms.games.PlayGames
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [36])
class AppTest {
    private val app: App = ApplicationProvider.getApplicationContext()
    private lateinit var priorAppVersion: String
    private var priorOptionalText: SpannableString? = null
    private lateinit var priorOnShareApp: (Activity) -> Unit

    @Before
    fun saveStaticState() {
        priorAppVersion = CommonUtilsAboutActivity.appVersion
        priorOptionalText = CommonUtilsAboutActivity.optionalText
        priorOnShareApp = CommonUtilsAboutMeActivity.onShareApp
    }

    @After
    fun restoreStaticState() {
        CommonUtilsAboutActivity.appVersion = priorAppVersion
        CommonUtilsAboutActivity.optionalText = priorOptionalText
        CommonUtilsAboutMeActivity.onShareApp = priorOnShareApp
        unmockkStatic(PlayGames::class)
    }

    private fun aboutText(): SpannableString = CommonUtilsAboutActivity.optionalText.shouldNotBeNull()

    private fun SpannableString.links(): List<ClickableSpan> = getSpans(0, length, ClickableSpan::class.java).toList()

    private fun SpannableString.labelOf(span: ClickableSpan): String = substring(getSpanStart(span), getSpanEnd(span))

    @Test
    fun `the application sets the about version without MainActivity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manifestVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName

        manifestVersion.shouldNotBeNull().shouldNotBeEmpty()
        CommonUtilsAboutActivity.appVersion shouldBe manifestVersion
    }

    @Test
    fun `the application sets the about text with the library and license links`() {
        aboutText().toString() shouldBe "A Sudoku app with OneUI-Design.\nUses Sudoku library licensed under LGPL 3."
        val links = aboutText().links()
        links shouldHaveSize 2
        aboutText().labelOf(links[0]) shouldBe "Sudoku library"
        aboutText().labelOf(links[1]) shouldBe "LGPL 3"
    }

    @Test
    fun `clicking the library link opens the library's GitHub page`() {
        aboutText().links()[0].onClick(View(app))

        shadowOf(app).nextStartedActivity.data.toString() shouldBe "https://github.com/sfuhrm/sudoku"
    }

    @Test
    fun `clicking the license link opens the license's GitHub page`() {
        aboutText().links()[1].onClick(View(app))

        shadowOf(app).nextStartedActivity.data.toString() shouldBe "https://github.com/sfuhrm/sudoku/blob/master/LICENSE"
    }

    @Test
    fun `the about text follows a language change without a restart`() {
        RuntimeEnvironment.setQualifiers("+de")
        app.onConfigurationChanged(app.resources.configuration)

        aboutText().toString() shouldBe
            "Eine Sudoku App im schönen OneUI-Design.\nVerwendet Sudoku-Bibliothek, lizenziert unter LGPL 3."
        val links = aboutText().links()
        links shouldHaveSize 2
        aboutText().labelOf(links[0]) shouldBe "Sudoku-Bibliothek"
        aboutText().labelOf(links[1]) shouldBe "LGPL 3"
    }

    @Test
    fun `sharing the app unlocks the share achievement through the given activity`() {
        val achievements = mockk<AchievementsClient>(relaxed = true)
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        mockkStatic(PlayGames::class)
        every { PlayGames.getAchievementsClient(activity) } returns achievements

        CommonUtilsAboutMeActivity.onShareApp(activity)

        verify(exactly = 1) { achievements.unlock("CgkIqfzS8vQZEAIQBA") }
    }
}
