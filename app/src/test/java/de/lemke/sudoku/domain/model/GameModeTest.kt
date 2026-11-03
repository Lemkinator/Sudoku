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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class GameModeTest : ShouldSpec(
    {
        should("keep stable mode codes") {
            listOf(GameMode.Normal.code, GameMode.Daily.code, GameMode.Level(3).code) shouldBe listOf(0, 1, 2)
        }

        should("read normal and daily without a level") {
            GameMode.fromCode(0, null) shouldBe GameMode.Normal
            GameMode.fromCode(1, null) shouldBe GameMode.Daily
        }

        should("read a level with its number") { GameMode.fromCode(2, 3) shouldBe GameMode.Level(3) }

        should("read no mode for the level code without a level") { GameMode.fromCode(2, null) shouldBe null }

        should("read no mode for level 0") { GameMode.fromCode(2, 0) shouldBe null }

        should("read no mode for an unknown code") { GameMode.fromCode(3, null) shouldBe null }

        should("reject a level number below 1") { shouldThrow<IllegalArgumentException> { GameMode.Level(0) } }
    },
)
