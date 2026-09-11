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

import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Sudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateDailySudokuUseCaseTest : ShouldSpec(
    {
        val generateFields = mockk<GenerateFieldsUseCase>()
        val fixedInstant = Instant.parse("2026-02-15T10:00:00Z")
        val clock = mockk<Clock>()
        val useCase = GenerateDailySudokuUseCase(generateFields, clock, UnconfinedTestDispatcher())

        should("mark the sudoku as daily mode, stamp created from the injected clock, and pick a supported difficulty") {
            every { clock.instant() } returns fixedInstant
            every { clock.zone } returns ZoneOffset.UTC
            coEvery { generateFields(9, any()) } returns mutableListOf<Field>()

            val sudoku = useCase(9)

            sudoku.modeLevel shouldBe Sudoku.MODE_DAILY
            sudoku.created shouldBe LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC)
            sudoku.difficulty shouldBeIn listOf(Difficulty.VERY_EASY, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
        }
    },
)
