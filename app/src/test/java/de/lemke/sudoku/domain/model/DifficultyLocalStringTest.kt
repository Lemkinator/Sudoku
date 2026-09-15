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
        Difficulty.VERY_EASY.getLocalString(resources) shouldBe "Very easy"
        Difficulty.EASY.getLocalString(resources) shouldBe "Easy"
        Difficulty.MEDIUM.getLocalString(resources) shouldBe "Medium"
        Difficulty.HARD.getLocalString(resources) shouldBe "Hard"
        Difficulty.EXPERT.getLocalString(resources) shouldBe "Expert"
    }

    @Test
    fun `companion getLocalString resolves the ordinal through fromInt before localizing`() {
        Difficulty.getLocalString(Difficulty.HARD.ordinal, resources) shouldBe "Hard"
        Difficulty.getLocalString(-1, resources) shouldBe "Medium"
    }
}
