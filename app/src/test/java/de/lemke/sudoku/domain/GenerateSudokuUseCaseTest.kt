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

import de.lemke.sudoku.domain.model.Difficulty.HARD
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Sudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateSudokuUseCaseTest : ShouldSpec(
    {
        val generateFields = mockk<GenerateFieldsUseCase>()
        val useCase = GenerateSudokuUseCase(generateFields, UnconfinedTestDispatcher())

        should("pass size/difficulty through to GenerateFieldsUseCase and mark the sudoku as normal mode") {
            val fields = mutableListOf<Field>()
            coEvery { generateFields(9, HARD) } returns fields

            val sudoku = useCase(9, HARD)

            sudoku.size shouldBe 9
            sudoku.difficulty shouldBe HARD
            sudoku.modeLevel shouldBe Sudoku.MODE_NORMAL
            sudoku.fields shouldBe fields
        }
    },
)
