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

import android.content.Intent
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.commonutils.di.IoDispatcher
import de.lemke.commonutils.di.MainDispatcher
import de.lemke.sudoku.di.DispatchersModule
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Drives the real `while (introStepNow() == X) { delay(...); ... }` loops in `IntroActivityStepAnimations.kt` to
 * completion by advancing Robolectric's paused main-looper clock past their `delay()` calls with
 * `shadowOf(Looper.getMainLooper()).idleFor(Duration)`, rather than driving the state machine alone (see
 * [IntroActivityFlowTest]) — that only reaches the `when` dispatch in `startAnimation`, never the loop bodies
 * themselves. Each loop is entered at least once, including its real `View.animate()` calls, before advancing to the
 * next step (which cancels the animation via `stopAnimation`).
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class IntroActivityAnimationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @DefaultDispatcher
    @JvmField
    val testDefaultDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()

    @BindValue
    @IoDispatcher
    @JvmField
    val testIoDispatcher: CoroutineDispatcher = Dispatchers.IO

    @BindValue
    @MainDispatcher
    @JvmField
    val testMainDispatcher: CoroutineDispatcher = Dispatchers.Main

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    private fun launch(block: (IntroActivity) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent = Intent(context, IntroActivity::class.java)
        ActivityScenario.launch<IntroActivity>(intent).use { scenario ->
            scenario.onActivity { activity -> block(activity) }
        }
    }

    private fun idle(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    /**
     * Advancing the paused main looper's clock in several smaller steps (instead of one big
     * `idleFor`) is what actually drains a chain of sequential `delay()` calls inside a single loop
     * iteration (`delay(); work; delay(); work; ...`) — a single large `idleFor` call only ever
     * resolves the *first* pending delay, leaving the rest of that iteration's body never entered,
     * even though the total duration comfortably covers all of them.
     */
    private fun idleRepeated(
        times: Int,
        millisEach: Long,
    ) = repeat(times) { idle(millisEach) }

    @Test
    fun `step 0's row-column-block loop runs a full block-row-column highlight cycle`() =
        launch { activity ->
            idleRepeated(5, 1000)
            activity.introStep shouldBe 0
            activity.nextIntroStep()
            activity.introStep shouldBe 1
        }

    @Test
    fun `step 2's field-highlight loop runs at least one animation cycle`() =
        launch { activity ->
            activity.nextIntroStep()
            activity.nextIntroStep()
            activity.introStep shouldBe 2
            idleRepeated(5, 800)
            activity.select(DEMO_CELL_INDEX_4)
            activity.introStep shouldBe 3
        }

    @Test
    fun `step 5's field-highlight loop runs at least one animation cycle`() =
        launch { activity ->
            activity.nextIntroStep()
            activity.nextIntroStep()
            activity.select(DEMO_CELL_INDEX_4)
            activity.select(activity.sudoku.itemCount + DEMO_NUMBER_BUTTON_INDEX_4)
            activity.select(activity.sudoku.itemCount + 1)
            activity.introStep shouldBe 5
            idleRepeated(5, 800)
            activity.select(DEMO_CELL_INDEX_49)
            activity.introStep shouldBe 6
        }

    @Test
    fun `step 6's block-highlight loop runs at least one animation cycle`() =
        launch { activity ->
            activity.nextIntroStep()
            activity.nextIntroStep()
            activity.select(DEMO_CELL_INDEX_4)
            activity.select(activity.sudoku.itemCount + DEMO_NUMBER_BUTTON_INDEX_4)
            activity.select(activity.sudoku.itemCount + 1)
            activity.select(DEMO_CELL_INDEX_49)
            activity.introStep shouldBe 6
            idleRepeated(5, 800)
            activity.select(DEMO_CELL_INDEX_24)
            activity.introStep shouldBe 7
        }

    @Test
    fun `animate with animateSudoku drives the whole-board branch of matchesAnimation`() =
        launch { activity ->
            val position =
                de.lemke.sudoku.domain.model.Position
                    .create(0, activity.sudoku.size)
            val job = animate(position, activity.gameAdapter, activity.sudoku, activity.lifecycleScope, animateSudoku = true)
            job.shouldNotBeNull()
            idle(2000)
        }

    @Test
    fun `animate with every flag false is a no-op`() =
        launch { activity ->
            val position =
                de.lemke.sudoku.domain.model.Position
                    .create(0, activity.sudoku.size)
            val job = animate(position, activity.gameAdapter, activity.sudoku, activity.lifecycleScope)
            job.shouldBeNull()
        }

    @Test
    fun `step 8's number-entry loop runs at least one full demo cycle`() =
        launch { activity ->
            activity.nextIntroStep()
            activity.nextIntroStep()
            activity.select(DEMO_CELL_INDEX_4)
            activity.select(activity.sudoku.itemCount + DEMO_NUMBER_BUTTON_INDEX_4)
            activity.select(activity.sudoku.itemCount + 1)
            activity.select(DEMO_CELL_INDEX_49)
            activity.select(DEMO_CELL_INDEX_24)
            activity.nextIntroStep()
            activity.introStep shouldBe 8
            idleRepeated(8, 1200)
            activity.nextIntroStep()
            activity.introStep shouldBe 9
        }
}
