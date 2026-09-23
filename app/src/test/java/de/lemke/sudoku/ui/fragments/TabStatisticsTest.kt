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

package de.lemke.sudoku.ui.fragments

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class TabStatisticsTest : ShouldSpec(
    {
        context("secondsToTimeString") {
            should("returns a placeholder for a negative value") {
                TabStatistics().secondsToTimeString(-1) shouldBe "--:--"
            }

            should("formats minutes and seconds below one hour") {
                TabStatistics().secondsToTimeString(125) shouldBe "02:05"
            }

            should("formats hours, minutes and seconds at exactly one hour") {
                TabStatistics().secondsToTimeString(3600) shouldBe "01:00:00"
            }

            should("formats hours, minutes and seconds above one hour") {
                TabStatistics().secondsToTimeString(7325) shouldBe "02:02:05"
            }
        }

        context("totalSecondsToString") {
            should("returns a placeholder for zero seconds") {
                TabStatistics().totalSecondsToString(0L) shouldBe "-"
            }

            should("formats seconds only below one minute") {
                TabStatistics().totalSecondsToString(45L) shouldBe "45s"
            }

            should("formats minutes and seconds below one hour") {
                TabStatistics().totalSecondsToString(125L) shouldBe "2m 5s"
            }

            should("formats hours and minutes below one day") {
                TabStatistics().totalSecondsToString(7325L) shouldBe "2h 2m"
            }

            should("formats days, hours and minutes at or above one day") {
                TabStatistics().totalSecondsToString(90065L) shouldBe "1d 1h 1m"
            }
        }
    },
)
