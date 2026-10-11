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

package de.lemke.sudoku.ui

import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.ui.utils.setupOnboarding
import de.lemke.sudoku.HiltTestRule
import de.lemke.sudoku.data.DailyNotifications
import de.lemke.sudoku.data.FakeDailyNotifications
import de.lemke.sudoku.data.FakeDailyNotifications.AlarmTime
import de.lemke.sudoku.di.DailyNotificationsModule
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** API 32 has no runtime notification permission, so it counts as granted. */
@UninstallModules(DailyNotificationsModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [32])
class IntroActivityLegacyPermissionTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    private val fakeDailyNotifications = FakeDailyNotifications(permissionGranted = false)

    @BindValue
    @JvmField
    val dailyNotifications: DailyNotifications = fakeDailyNotifications

    private val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()

    @Before
    fun setup() {
        hiltRule.inject()
        setupOnboarding(listOf(IntroActivity::class.java))
    }

    @After
    fun tearDown() = setupOnboarding()

    @Test
    fun `accepting notifications below API 33 saves them as granted and advances without a permission request`() {
        val controller = Robolectric.buildActivity(IntroActivity::class.java, onboardingIntroIntent(context)).setup()
        try {
            val activity = controller.get()
            activity.binding.introContinueButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            shadowOf(activity).lastRequestedPermission.shouldBeNull()
            fakeDailyNotifications.scheduled shouldBe AlarmTime(hour = 9, minute = 0)
            shadowOf(activity)
                .nextStartedActivity
                .shouldNotBeNull()
                .component
                ?.className shouldBe "de.lemke.sudoku.ui.MainActivity"
            activity.isFinishing.shouldBeTrue()
            activity.viewModel.notificationChoice.value shouldBe NotificationChoice.Pending
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
