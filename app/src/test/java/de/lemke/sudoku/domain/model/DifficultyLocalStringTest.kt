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

package de.lemke.sudoku.domain.model

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.R
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DifficultyLocalStringTest {
    private val resources = ApplicationProvider.getApplicationContext<Application>().resources

    @Test
    fun `getLocalString returns the resource array entry at the difficulty's ordinal`() {
        val expected = resources.getStringArray(R.array.difficulty)

        Difficulty.entries.forEach { difficulty ->
            difficulty.getLocalString(resources) shouldBe expected[difficulty.ordinal]
        }
    }

    @Test
    fun `companion getLocalString resolves the ordinal through fromInt before localizing`() {
        val expected = resources.getStringArray(R.array.difficulty)

        Difficulty.getLocalString(Difficulty.HARD.ordinal, resources) shouldBe expected[Difficulty.HARD.ordinal]
        Difficulty.getLocalString(-1, resources) shouldBe Difficulty.MEDIUM.getLocalString(resources)
    }
}
