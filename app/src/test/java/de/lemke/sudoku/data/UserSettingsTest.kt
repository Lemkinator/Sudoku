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

package de.lemke.sudoku.data

import android.app.Application
import android.content.SharedPreferences
import de.lemke.commonutils.data.assertDelegatedKeys
import de.lemke.commonutils.freshTestPreferences
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.SudokuFilterFlags.DIFFICULTY_ALL
import de.lemke.sudoku.domain.model.SudokuFilterFlags.SIZE_ALL
import de.lemke.sudoku.domain.model.SudokuFilterFlags.TYPE_ALL
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pins Sudoku's own settings invariants (defaults, clamping, sanitizing) against the real [UserSettings]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class UserSettingsTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var settings: UserSettings

    private fun reload() = UserSettings(prefs, CoroutineScope(UnconfinedTestDispatcher()))

    @Before
    fun setUp() {
        prefs = freshTestPreferences()
        settings = reload()
    }

    @Test
    fun `defaults on fresh store`() {
        settings.difficultySliderValue shouldBe 2
        settings.sizeSliderValue shouldBe 1
        settings.keepScreenOn.shouldBeTrue()
        settings.animationsEnabled.shouldBeTrue()
        settings.highlightRegional.shouldBeTrue()
        settings.highlightNumber.shouldBeTrue()
        settings.errorLimit shouldBe 3
        settings.filterFlags shouldBe (TYPE_ALL or SIZE_ALL or DIFFICULTY_ALL)
        settings.dailyShowUncompleted.shouldBeTrue()
        settings.dailySudokuNotificationEnabled.shouldBeTrue()
        settings.dailySudokuNotificationHour shouldBe 9
        settings.dailySudokuNotificationMinute shouldBe 0
        settings.currentLevelTab shouldBe 1
    }

    @Test
    fun `difficultySliderValue round-trips a value within range`() {
        settings.difficultySliderValue = Difficulty.max
        reload().difficultySliderValue shouldBe Difficulty.max
    }

    @Test
    fun `difficultySliderValue clamps to 0 when written below range`() {
        settings.difficultySliderValue = -1
        settings.difficultySliderValue shouldBe 0
    }

    @Test
    fun `difficultySliderValue clamps to Difficulty max when written above range`() {
        settings.difficultySliderValue = Difficulty.max + 1
        settings.difficultySliderValue shouldBe Difficulty.max
    }

    @Test
    fun `difficultySliderValue persists the clamped value rather than the raw write`() {
        settings.difficultySliderValue = Difficulty.max + 500
        prefs.getInt("difficultySliderValue", -1) shouldBe Difficulty.max
    }

    @Test
    fun `keepScreenOn round-trips false`() {
        settings.keepScreenOn = false
        reload().keepScreenOn.shouldBeFalse()
    }

    @Test
    fun `errorLimit round-trips a value`() {
        settings.errorLimit = 7
        reload().errorLimit shouldBe 7
    }

    @Test
    fun `errorLimit clamps to 0 when written below range`() {
        settings.errorLimit = -1
        settings.errorLimit shouldBe 0
    }

    @Test
    fun `errorLimit persists as its string wire format`() {
        settings.errorLimit = 5
        prefs.getString("errorLimit", null) shouldBe "5"
    }

    @Test
    fun `errorLimit falls back to 0 on malformed stored string`() {
        prefs.edit().putString("errorLimit", "not-a-number").apply()
        reload().errorLimit shouldBe 0
    }

    @Test
    fun `filterFlags round-trips`() {
        settings.filterFlags = DIFFICULTY_ALL
        reload().filterFlags shouldBe DIFFICULTY_ALL
    }

    @Test
    fun `dailySudokuNotificationHour round-trips`() {
        settings.dailySudokuNotificationHour = 21
        reload().dailySudokuNotificationHour shouldBe 21
    }

    @Test
    fun `dailyShowUncompletedFlow reflects current value`() {
        settings.dailyShowUncompleted = false
        settings.dailyShowUncompletedFlow.value.shouldBeFalse()
    }

    @Test
    fun `filterFlagsFlow reflects current value`() {
        settings.filterFlags = TYPE_ALL
        settings.filterFlagsFlow.value shouldBe TYPE_ALL
    }

    @Test
    fun `errorLimitFlow reflects current value`() {
        settings.errorLimit = 9
        settings.errorLimitFlow.value shouldBe 9
    }

    @Test
    fun `delegated keys are pinned`() {
        assertDelegatedKeys(
            UserSettings::class.java,
            setOf(
                "difficultySliderValue",
                "sizeSliderValue",
                "keepScreenOn",
                "animationsEnabled",
                "highlightRegional",
                "highlightNumber",
                "errorLimit",
                "filterFlags",
                "dailyShowUncompleted",
                "dailySudokuNotificationEnabled",
                "dailySudokuNotificationHour",
                "dailySudokuNotificationMinute",
                "currentLevelTab",
            ),
        )
    }
}
