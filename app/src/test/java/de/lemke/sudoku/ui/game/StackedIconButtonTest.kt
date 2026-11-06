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
import android.view.ContextThemeWrapper
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class StackedIconButtonTest {
    private val context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), androidx.appcompat.R.style.Theme_AppCompat_Light)

    private fun drawnIconTop(height: Int): Pair<Int, Int> {
        val button = StackedIconButton(context)
        button.setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.ic_menu_add, 0, 0)
        button.text = "Note"
        button.measure(MeasureSpec.makeMeasureSpec(300, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        button.layout(0, 0, 300, height)
        button.draw(Canvas(Bitmap.createBitmap(300, height, Bitmap.Config.ARGB_8888)))
        val icon = button.compoundDrawables[1]
        val slack = height - button.paddingTop - button.paddingBottom - icon.bounds.height() - button.layout.height
        return icon.bounds.top to slack
    }

    @Test
    fun `icon moves down by half the slack in a stretched button`() {
        val (top, slack) = drawnIconTop(height = 600)
        (slack > 0) shouldBe true
        top shouldBe slack / 2
    }

    @Test
    fun `icon stays at the top edge when the button has no slack`() {
        val (top, slack) = drawnIconTop(height = 1)
        (slack <= 0) shouldBe true
        top shouldBe 0
    }
}
