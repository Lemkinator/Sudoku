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
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.sudoku.HiltTestRule
import de.lemke.sudoku.data.DailyNotifications
import de.lemke.sudoku.data.FakeDailyNotifications
import de.lemke.sudoku.di.DailyNotificationsModule
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** API 32 has no runtime notification permission, so the intro has nothing to request. */
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

    @Before
    fun setup() = hiltRule.inject()

    @Test
    fun `a permission request below API 33 launches nothing and is handled`() {
        val controller = Robolectric.buildActivity(IntroActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.binding.introContinueButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            shadowOf(activity).lastRequestedPermission.shouldBeNull()
            activity.viewModel.notificationChoice.value shouldBe NotificationChoice.Pending
            fakeDailyNotifications.scheduled.shouldBeNull()
            activity.isFinishing.shouldBeFalse()
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
