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

import android.app.AlarmManager
import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.ui.utils.setupOnboarding
import de.lemke.commonutils.ui.utils.singleLaunchActivity
import de.lemke.sudoku.HiltTestRule
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.util.Calendar
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** Runs the intro as the onboarding step after OOBE, so `advanceOnboarding` starts the main activity through the launch latch. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class IntroActivityLaunchOutcomeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    private val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()

    @Before
    fun setup() {
        hiltRule.inject()
        setupOnboarding(listOf(IntroActivity::class.java))
    }

    @After
    fun tearDown() = setupOnboarding()

    /** The intent OOBE received from the main activity's first launch, retargeted at the intro step. */
    private fun onboardingIntroIntent(): Intent {
        val main = Robolectric.buildActivity(MainActivity::class.java).create()
        idle()
        val oobe = shadowOf(main.get()).nextStartedActivity.shouldNotBeNull()
        main.destroy()
        return Intent(oobe).setClass(context, IntroActivity::class.java)
    }

    private fun launchIntro(block: (ActivityController<IntroActivity>) -> Unit) {
        val controller = Robolectric.buildActivity(IntroActivity::class.java, onboardingIntroIntent()).setup()
        idle()
        try {
            block(controller)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Starts another activity without pausing the intro, so the launch latch drops launches until the intro resumes again. */
    private fun IntroActivity.closeLaunchLatch() {
        singleLaunchActivity(Intent(this, SettingsActivity::class.java)).shouldBeTrue()
        shadowOf(this).nextStartedActivity.shouldNotBeNull()
    }

    private fun IntroActivity.declineNotifications() {
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(BUTTON_NEGATIVE).performClick()
        idle()
    }

    private fun IntroActivity.shouldHaveStartedMainActivity() {
        shadowOf(this)
            .nextStartedActivity
            .shouldNotBeNull()
            .component
            ?.className shouldBe "de.lemke.sudoku.ui.MainActivity"
        isFinishing.shouldBeTrue()
        viewModel.notificationChoice.value shouldBe NotificationChoice.Pending
    }

    @Test
    fun `a started advance marks the saved choice handled`() =
        launchIntro { controller ->
            val activity = controller.get()
            activity.binding.introContinueButton.performClick()
            activity.declineNotifications()

            activity.shouldHaveStartedMainActivity()
        }

    @Test
    fun `a dropped advance keeps the saved choice pending and retries it on the next resume`() =
        launchIntro { controller ->
            val activity = controller.get()
            activity.binding.introContinueButton.performClick()
            activity.closeLaunchLatch()
            activity.declineNotifications()

            shadowOf(activity).nextStartedActivity.shouldBeNull()
            activity.isFinishing.shouldBeFalse()
            activity.viewModel.notificationChoice.value shouldBe NotificationChoice.Saved

            controller.pause().resume()
            idle()

            activity.shouldHaveStartedMainActivity()
        }

    @Test
    fun `tapping continue again while a dropped advance is pending advances without asking again`() =
        launchIntro { controller ->
            val activity = controller.get()
            activity.binding.introContinueButton.performClick()
            val question = ShadowDialog.getLatestDialog()
            activity.closeLaunchLatch()
            activity.declineNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

            activity.binding.introContinueButton.performClick()
            idle()

            ShadowDialog.getLatestDialog() shouldBe question
            activity.shouldHaveStartedMainActivity()
        }

    @Test
    fun `a dropped permission request stays pending, retries on the next resume, and its grant then advances`() =
        launchIntro { controller ->
            val activity = controller.get()
            shadowOf(context).denyPermissions("android.permission.POST_NOTIFICATIONS")
            activity.binding.introContinueButton.performClick()
            activity.closeLaunchLatch()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(BUTTON_POSITIVE).performClick()
            idle()

            shadowOf(activity).lastRequestedPermission.shouldBeNull()
            activity.viewModel.notificationChoice.value shouldBe NotificationChoice.RequestPermission

            controller.pause().resume()
            idle()

            val request = shadowOf(activity).lastRequestedPermission.shouldNotBeNull()
            request.requestedPermissions.toList() shouldBe listOf("android.permission.POST_NOTIFICATIONS")
            activity.viewModel.notificationChoice.value shouldBe NotificationChoice.Pending

            val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
            alarms.peekNextScheduledAlarm().shouldBeNull()

            activity.activityResultRegistry.dispatchResult(request.requestCode, true)
            idle()

            val alarm = alarms.peekNextScheduledAlarm().shouldNotBeNull()
            val trigger = Calendar.getInstance().apply { timeInMillis = alarm.triggerAtMs }
            trigger.get(Calendar.HOUR_OF_DAY) shouldBe 9
            trigger.get(Calendar.MINUTE) shouldBe 0
            activity.shouldHaveStartedMainActivity()
        }
}
