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
import android.content.Context
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup.LayoutParams
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.R
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class GameLayoutTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun layout(
        layout: GameLayout,
        width: Int,
        height: Int,
    ) {
        layout.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        layout.layout(0, 0, width, height)
    }

    private fun GameLayout.with(controlsParams: LayoutParams): Pair<View, View> {
        val board = View(context)
        val controls = View(context)
        addView(board, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(controls, controlsParams)
        return board to controls
    }

    private fun View.bounds() = listOf(left, top, right, bottom)

    @Test
    fun `controls sit below the board by default`() {
        val layout = GameLayout(context)
        val (board, controls) = layout.with(LayoutParams(LayoutParams.MATCH_PARENT, 200))

        layout(layout, 500, 1000)

        board.bounds() shouldBe listOf(0, 150, 500, 650)
        controls.bounds() shouldBe listOf(0, 650, 500, 850)
    }

    @Test
    fun `controls of a fixed width sit at the end of the board`() {
        val attributes = Robolectric.buildAttributeSet().addAttribute(R.attr.controlsPosition, "end").build()
        val layout = GameLayout(context, attributes)
        val (board, controls) = layout.with(LayoutParams(320, LayoutParams.MATCH_PARENT))

        layout(layout, 1000, 600)

        board.bounds() shouldBe listOf(40, 0, 640, 600)
        controls.bounds() shouldBe listOf(640, 0, 960, 600)
    }

    private class TallView(context: Context) : View(context) {
        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) = setMeasuredDimension(resolveSize(DESIRED, widthMeasureSpec), resolveSize(DESIRED, heightMeasureSpec))

        private companion object {
            const val DESIRED = 5000
        }
    }

    @Test
    fun `scrolling controls below the board leave half the board side`() {
        val layout = GameLayout(context)
        val board = View(context)
        val controls = TallView(context)
        layout.addView(board, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        layout.addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        layout(layout, 500, 1000)

        board.bounds() shouldBe listOf(125, 0, 375, 250)
        controls.bounds() shouldBe listOf(0, 250, 500, 1000)
    }

    @Test
    fun `scrolling controls at the end leave half the board side`() {
        val attributes = Robolectric.buildAttributeSet().addAttribute(R.attr.controlsPosition, "end").build()
        val layout = GameLayout(context, attributes)
        val board = View(context)
        val controls = TallView(context)
        layout.addView(board, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        layout.addView(controls, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))

        layout(layout, 1000, 600)

        board.bounds() shouldBe listOf(0, 150, 300, 450)
        controls.bounds() shouldBe listOf(300, 0, 1000, 600)
    }
}
