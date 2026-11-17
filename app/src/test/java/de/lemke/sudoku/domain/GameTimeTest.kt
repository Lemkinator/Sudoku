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

package de.lemke.sudoku.domain

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class GameTimeTest : ShouldSpec(
    {
        val clock = Clock.fixed(Instant.parse("2026-03-05T22:30:15Z"), ZoneOffset.UTC)
        val timeSource = TestTimeSource()
        val gameTime = GameTime(clock, timeSource)

        should("report today as the date of the clock") {
            gameTime.today() shouldBe LocalDate.of(2026, 3, 5)
        }

        should("report now as the date and time of the clock") {
            gameTime.now() shouldBe LocalDateTime.of(2026, 3, 5, 22, 30, 15)
        }

        should("use the zone of the clock for today and now") {
            val tokyo = GameTime(clock.withZone(ZoneOffset.ofHours(9)), timeSource)

            tokyo.today() shouldBe LocalDate.of(2026, 3, 6)
            tokyo.now() shouldBe LocalDateTime.of(2026, 3, 6, 7, 30, 15)
        }

        should("mark the time source so the mark measures the time elapsed since") {
            val mark = gameTime.markNow()

            mark.elapsedNow() shouldBe 0.seconds
            timeSource += 42.seconds
            mark.elapsedNow() shouldBe 42.seconds
        }
    },
)
