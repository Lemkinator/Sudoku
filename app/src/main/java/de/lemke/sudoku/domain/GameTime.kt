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

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

/** The wall clock that dates a game and the monotonic clock that times its ticks. */
class GameTime @Inject constructor(
    private val clock: Clock,
    private val timeSource: TimeSource.WithComparableMarks,
) {
    fun today(): LocalDate = LocalDate.now(clock)

    fun now(): LocalDateTime = LocalDateTime.now(clock)

    fun markNow(): ComparableTimeMark = timeSource.markNow()
}
