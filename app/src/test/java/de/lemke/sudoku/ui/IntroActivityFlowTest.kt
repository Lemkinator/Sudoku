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

import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
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
import de.lemke.sudoku.R
import de.lemke.sudoku.di.DispatchersModule
import de.lemke.sudoku.domain.model.Position
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog

/**
 * Drives [IntroActivity]'s scripted tutorial state machine (`nextIntroStep`/`select`/`selectButton` in this file and
 * `IntroActivityStepAnimations.kt`) the way a real tap on the tutorial board or number pad would, following the exact
 * script described by `intro_text0`..`intro_text10`. Real `.animate()`-driven loops inside `startAnimation` are not
 * driven to completion here (see the coverage status file); this covers the state machine, menu, note button and
 * notifications-dialog paths around them.
 *
 * sdk = 36: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(DispatchersModule::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class IntroActivityFlowTest {
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

    private fun launch(
        openedFromSettings: Boolean = false,
        block: (IntroActivity) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val intent =
            Intent(context, IntroActivity::class.java)
                .putExtra(IntroActivity.KEY_OPENED_FROM_SETTINGS, openedFromSettings)
        ActivityScenario.launch<IntroActivity>(intent).use { scenario ->
            scenario.onActivity { activity -> block(activity) }
        }
    }

    // region step 0

    @Test
    fun `onCreate starts the tutorial at step 0 showing the row-column-block explanation`() =
        launch { activity ->
            activity.introStep shouldBe 0
            activity.binding.introTextText.text
                .toString() shouldBe activity.getString(R.string.intro_text0)
        }

    @Test
    fun `selecting nothing while nothing is selected and no step condition matches is a no-op`() =
        launch { activity ->
            activity.select(null)
            activity.introStep shouldBe 0
            activity.selected.shouldBeNull()
        }

    @Test
    fun `tapping an unrelated field at step 0 does not advance the tutorial`() =
        launch { activity ->
            activity.select(0)
            activity.introStep shouldBe 0
            activity.selected.shouldBeNull()
        }

    @Test
    fun `nextIntroStep advances from 0 to 1 and stops the row-column-block animation`() =
        launch { activity ->
            activity.nextIntroStep()
            activity.introStep shouldBe 1
            activity.binding.introTextText.text
                .toString() shouldBe activity.getString(R.string.intro_text1)
        }

    // endregion

    // region step 2 -> 3: select the highlighted field

    private fun toStep2(activity: IntroActivity) {
        activity.nextIntroStep()
        activity.nextIntroStep()
    }

    @Test
    fun `nextIntroStep advances from 1 to 2 and hides the title while starting the field animation`() =
        launch { activity ->
            toStep2(activity)
            activity.introStep shouldBe 2
            activity.binding.introTitle.isVisible
                .shouldBeFalse()
            activity.binding.introTextText.text
                .toString() shouldBe activity.getString(R.string.intro_text2)
        }

    @Test
    fun `tapping the wrong field at step 2 does not advance`() =
        launch { activity ->
            toStep2(activity)
            activity.select(0)
            activity.introStep shouldBe 2
            activity.selected.shouldBeNull()
        }

    @Test
    fun `tapping the highlighted field at step 2 selects it and advances to step 3`() =
        launch { activity ->
            toStep2(activity)
            activity.select(DEMO_CELL_INDEX_4)
            activity.introStep shouldBe 3
            activity.selected shouldBe DEMO_CELL_INDEX_4
            activity.binding.gameButtons.isVisible
                .shouldBeTrue()
            activity.binding.otherButtons.isVisible
                .shouldBeFalse()
        }

    // endregion

    // region step 3 -> 4: enter the number

    private fun toStep3(activity: IntroActivity) {
        toStep2(activity)
        activity.select(DEMO_CELL_INDEX_4)
    }

    @Test
    fun `tapping the wrong number at step 3 does not place a value`() =
        launch { activity ->
            toStep3(activity)
            activity.select(activity.sudoku.itemCount)
            activity.introStep shouldBe 3
            activity.sudoku[DEMO_CELL_INDEX_4].value.shouldBeNull()
        }

    @Test
    fun `tapping the correct number at step 3 places it and advances to step 4`() =
        launch { activity ->
            toStep3(activity)
            activity.select(activity.sudoku.itemCount + DEMO_NUMBER_BUTTON_INDEX_4)
            activity.introStep shouldBe 4
            activity.sudoku[DEMO_CELL_INDEX_4].value shouldBe DEMO_NUMBER_BUTTON_INDEX_4 + 1
            activity.selected.shouldBeNull()
        }

    // endregion

    // region step 4 -> 5: select a number first

    private fun toStep4(activity: IntroActivity) {
        toStep3(activity)
        activity.select(activity.sudoku.itemCount + DEMO_NUMBER_BUTTON_INDEX_4)
    }

    @Test
    fun `tapping the wrong number button at step 4 does not select it`() =
        launch { activity ->
            toStep4(activity)
            activity.select(activity.sudoku.itemCount)
            activity.introStep shouldBe 4
            activity.selected.shouldBeNull()
        }

    @Test
    fun `tapping number 2 at step 4 selects the button and advances to step 5`() =
        launch { activity ->
            toStep4(activity)
            activity.select(activity.sudoku.itemCount + 1)
            activity.introStep shouldBe 5
            activity.selected shouldBe activity.sudoku.itemCount + 1
        }

    // endregion

    // region step 5 -> 6: place the selected number in the middle block

    private fun toStep5(activity: IntroActivity) {
        toStep4(activity)
        activity.select(activity.sudoku.itemCount + 1)
    }

    @Test
    fun `tapping the wrong field at step 5 does not place a value`() =
        launch { activity ->
            toStep5(activity)
            activity.select(1)
            activity.introStep shouldBe 5
            activity.sudoku[DEMO_CELL_INDEX_49].value.shouldBeNull()
        }

    @Test
    fun `tapping the correct field at step 5 places the number and advances to step 6`() =
        launch { activity ->
            toStep5(activity)
            activity.select(DEMO_CELL_INDEX_49)
            activity.introStep shouldBe 6
            activity.sudoku[DEMO_CELL_INDEX_49].value shouldBe 2
            activity.selected shouldBe activity.sudoku.itemCount + 1
        }

    // endregion

    // region step 6 -> 7: place the same number in the top-right block

    private fun toStep6(activity: IntroActivity) {
        toStep5(activity)
        activity.select(DEMO_CELL_INDEX_49)
    }

    @Test
    fun `tapping the wrong field at step 6 does not place a value`() =
        launch { activity ->
            toStep6(activity)
            activity.select(1)
            activity.introStep shouldBe 6
            activity.sudoku[DEMO_CELL_INDEX_24].value.shouldBeNull()
        }

    @Test
    fun `tapping the correct field at step 6 places the number and advances to step 7`() =
        launch { activity ->
            toStep6(activity)
            activity.select(DEMO_CELL_INDEX_24)
            activity.introStep shouldBe 7
            activity.sudoku[DEMO_CELL_INDEX_24].value shouldBe 2
            activity.binding.introTitle.isVisible
                .shouldBeTrue()
            activity.binding.introTitleText.text
                .toString() shouldBe activity.getString(R.string.intro_title7)
        }

    // endregion

    // region steps 7 -> 10: summary, caution, delete/hints, final step

    private fun toStep7(activity: IntroActivity) {
        toStep6(activity)
        activity.select(DEMO_CELL_INDEX_24)
    }

    @Test
    fun `nextIntroStep advances from 7 to 8 and starts the number-entry animation`() =
        launch { activity ->
            toStep7(activity)
            activity.nextIntroStep()
            activity.introStep shouldBe 8
            activity.binding.introTitleText.text
                .toString() shouldBe activity.getString(R.string.intro_title8)
        }

    @Test
    fun `nextIntroStep advances from 8 to 9 and stops the number-entry animation`() =
        launch { activity ->
            toStep7(activity)
            activity.nextIntroStep()
            activity.nextIntroStep()
            activity.introStep shouldBe 9
            activity.binding.introTitleText.text
                .toString() shouldBe activity.getString(R.string.intro_title9)
            activity.binding.otherButtons.isVisible
                .shouldBeTrue()
            activity.binding.numberButtons.isVisible
                .shouldBeFalse()
        }

    @Test
    fun `nextIntroStep advances from 9 to 10 and shows the continue button`() =
        launch { activity ->
            toStep7(activity)
            activity.nextIntroStep()
            activity.nextIntroStep()
            activity.nextIntroStep()
            activity.introStep shouldBe 10
            activity.binding.introTitle.isVisible
                .shouldBeFalse()
            activity.binding.introContinueLayout.isVisible
                .shouldBeTrue()
        }

    // endregion

    // region note button

    private fun toStep10(activity: IntroActivity) {
        toStep7(activity)
        repeat(3) { activity.nextIntroStep() }
    }

    @Test
    fun `toggling the note button before the final step is a no-op`() =
        launch { activity ->
            activity.toggleOrSetNoteButton()
            activity.notesEnabled.shouldBeFalse()
        }

    @Test
    fun `toggling the note button at the final step enables and disables note mode`() =
        launch { activity ->
            toStep10(activity)
            activity.toggleOrSetNoteButton()
            activity.notesEnabled.shouldBeTrue()
            activity.binding.noteButton.backgroundTintList
                ?.defaultColor shouldBe activity.colorPrimary

            activity.toggleOrSetNoteButton()
            activity.notesEnabled.shouldBeFalse()
        }

    @Test
    fun `setting the note button explicitly overrides the toggle`() =
        launch { activity ->
            toStep10(activity)
            activity.toggleOrSetNoteButton(true)
            activity.notesEnabled.shouldBeTrue()
            activity.toggleOrSetNoteButton(false)
            activity.notesEnabled.shouldBeFalse()
        }

    // endregion

    // region SudokuGameListener

    @Test
    fun `onFieldClicked routes through select the same way a tap on the board would`() =
        launch { activity ->
            toStep2(activity)
            activity.sudoku.gameListener?.onFieldClicked(Position.create(DEMO_CELL_INDEX_4, activity.sudoku.size))
            activity.introStep shouldBe 3
        }

    @Test
    fun `onTimeChanged, onError and onCompleted are no-ops on the onboarding board`() =
        launch { activity ->
            val listener = activity.sudoku.gameListener
            listener?.onTimeChanged()
            listener?.onError()
            listener?.onCompleted(Position.create(0, activity.sudoku.size))
            activity.introStep shouldBe 0
        }

    // endregion

    // region menu

    @Test
    fun `menu_skip shows the notifications dialog`() =
        launch { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(R.id.menu_skip)).shouldBeTrue()
            val dialog = ShadowDialog.getLatestDialog()
            dialog.shouldNotBeNull()
            dialog.isShowing.shouldBeTrue()
        }

    @Test
    fun `an unknown menu item falls through to the default behavior`() =
        launch { activity ->
            activity.onOptionsItemSelected(RoboMenuItem(-12345)).shouldBeFalse()
        }

    // endregion

    // region continue button / notifications dialog

    @Test
    fun `continue button finishes directly when opened from settings`() =
        launch(openedFromSettings = true) { activity ->
            activity.binding.introContinueButton.performClick()
            activity.isFinishing.shouldBeTrue()
        }

    @Test
    fun `continue button shows a notifications dialog when not opened from settings`() =
        launch { activity ->
            activity.binding.introContinueButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog?
            dialog.shouldNotBeNull()
            dialog.isShowing.shouldBeTrue()
        }

    @Test
    fun `declining notifications advances onboarding and finishes`() =
        launch { activity ->
            activity.binding.introContinueButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            activity.isFinishing.shouldBeTrue()
        }

    @Test
    fun `accepting notifications with permission already granted advances onboarding and finishes`() =
        launch { activity ->
            shadowOf(ApplicationProvider.getApplicationContext<HiltTestApplication>())
                .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            activity.binding.introContinueButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            activity.isFinishing.shouldBeTrue()
        }

    @Test
    fun `accepting notifications without permission requests it instead of finishing`() =
        launch { activity ->
            shadowOf(ApplicationProvider.getApplicationContext<HiltTestApplication>())
                .denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            activity.binding.introContinueButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            activity.isFinishing.shouldBeFalse()
        }

    // endregion
}
