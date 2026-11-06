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

package de.lemke.sudoku.ui.game

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.View.MeasureSpec
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class StackedIconButtonTest {
    private val context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), androidx.appcompat.R.style.Theme_AppCompat_Light)

    private fun stackedButton(): StackedIconButton {
        val button = StackedIconButton(context)
        button.setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.ic_menu_add, 0, 0)
        button.compoundDrawablePadding = 10
        button.text = "Note"
        return button
    }

    private fun layOut(
        button: StackedIconButton,
        height: Int = 600,
    ) {
        button.measure(MeasureSpec.makeMeasureSpec(300, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        button.layout(0, 0, 300, height)
    }

    private fun drawOnBitmap(
        button: StackedIconButton,
        height: Int = 600,
    ) = button.draw(Canvas(Bitmap.createBitmap(300, height, Bitmap.Config.ARGB_8888)))

    private fun textTop(button: StackedIconButton) = button.totalPaddingTop + button.layout.getLineTop(0)

    @Test
    fun `icon sits directly above the label and the pair is centered in a stretched button`() {
        val button = stackedButton()

        layOut(button)

        val icon = button.compoundDrawables[1]
        icon.bounds.top shouldBe 205
        button.layout.height shouldBeGreaterThan 0
        button.paddingTop + icon.bounds.bottom + button.compoundDrawablePadding shouldBe textTop(button)
    }

    @Test
    fun `icon stays at the top edge when the button has no slack`() {
        val button = stackedButton()

        layOut(button, height = 1)

        button.compoundDrawables[1].bounds.top shouldBe 0
    }

    @Test
    fun `layout without a measure pass leaves the icon bounds alone`() {
        val button = stackedButton()
        val icon = button.compoundDrawables[1]
        val before = Rect(icon.bounds)
        button.layout shouldBe null

        button.layout(0, 0, 300, 600)

        icon.bounds shouldBe before
    }

    @Test
    fun `draws without a text layout and leaves the icon bounds alone`() {
        val button = stackedButton()
        val icon = button.compoundDrawables[1]
        val before = Rect(icon.bounds)
        button.layout shouldBe null

        drawOnBitmap(button)

        icon.bounds shouldBe before
    }

    @Test
    fun `draws after setText without a new layout pass and recenters on the next one`() {
        val button = stackedButton()
        button.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        layOut(button)
        button.layout shouldNotBe null
        val icon = button.compoundDrawables[1]

        button.text = "A different label"
        button.layout shouldBe null
        drawOnBitmap(button)

        layOut(button)
        button.layout shouldNotBe null
        icon.bounds.top shouldBe 182
        button.paddingTop + icon.bounds.bottom + button.compoundDrawablePadding shouldBe textTop(button)
    }

    @Test
    fun `setText under a fixed size requests a layout and recenters the icon`() {
        val button = stackedButton()
        button.layoutParams = ViewGroup.LayoutParams(300, 600)
        layOut(button)
        val icon = button.compoundDrawables[1]
        icon.bounds.top shouldBe 205

        button.text = "A much longer label that wraps over several lines in this narrow button"
        button.isLayoutRequested shouldBe true
        layOut(button)

        button.layout.lineCount shouldBeGreaterThan 1
        icon.bounds.top shouldBe 182
        button.paddingTop + icon.bounds.bottom + button.compoundDrawablePadding shouldBe textTop(button)
    }

    @Test
    fun `lays out and draws a button without a top icon`() {
        val button = StackedIconButton(context)
        button.text = "Note"

        layOut(button)
        drawOnBitmap(button)

        button.layout shouldNotBe null
        button.compoundDrawables[1] shouldBe null
    }
}
