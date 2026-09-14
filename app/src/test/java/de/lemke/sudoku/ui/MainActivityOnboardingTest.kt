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

import android.os.Looper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.ui.activity.CommonUtilsOOBEActivity
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Covers [MainActivity.onCreate]'s `onboardIfNeeded(...) ?: return` early-return path — every other
 * `MainActivity` test calls `settings.bypassOobe()` in its own setup, which always keeps this on the
 * non-null side. A fresh, un-bypassed settings store here is first-time by construction, so
 * `onboardIfNeeded` redirects to the real OOBE activity and returns `null` for real.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class MainActivityOnboardingTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun `onCreate redirects to onboarding and returns early on first launch`() {
        hiltRule.inject()
        // ActivityScenario refuses onActivity() once isFinishing triggers a real destroy, so build the
        // activity directly to keep a live reference to inspect after onCreate() redirects to OOBE.
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivity
        started.shouldNotBeNull()
        started.component?.className shouldBe CommonUtilsOOBEActivity::class.java.name
        activity.isFinishing.shouldBeTrue()
    }
}
