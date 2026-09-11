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
import de.lemke.sudoku.domain.model.Difficulty.EASY
import de.lemke.sudoku.domain.model.Difficulty.MEDIUM
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private fun testSudoku(
    completed: Boolean,
    difficulty: Difficulty = MEDIUM,
    size: Int = 9,
    seconds: Int = 0,
    errorsMade: Int = 0,
    hintsUsed: Int = 0,
    notesMade: Int = 0,
    eraserUsed: Boolean = false,
    updated: LocalDateTime = LocalDateTime.now(),
): Sudoku =
    Sudoku.create(
        size = size,
        difficulty = difficulty,
        modeLevel = Sudoku.MODE_NORMAL,
        errorsMade = errorsMade,
        hintsUsed = hintsUsed,
        notesMade = notesMade,
        eraserUsed = eraserUsed,
        updated = updated,
        seconds = seconds,
        fields = mutableListOf(Field(position = Position.create(0, size), solution = 1, value = if (completed) 1 else null)),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class CalculateStatisticsUseCaseTest : ShouldSpec(
    {
        val useCase = CalculateStatisticsUseCase(UnconfinedTestDispatcher())

        should("guard every empty-list aggregate when there are no games") {
            val stats = useCase(emptyList())

            stats.gamesStarted shouldBe 0
            stats.gamesCompleted shouldBe 0
            stats.winRate shouldBe 0
            stats.averageTime shouldBe -1
            stats.mostErrors shouldBe 0
            stats.mostHints shouldBe 0
            stats.mostNotes shouldBe 0
            stats.mostGamesStartedDifficulty shouldBe null
            stats.mostGamesWonDifficulty shouldBe null
            stats.mostGamesStartedSize shouldBe null
            stats.mostGamesWonSize shouldBe null
            stats.currentGameStreak shouldBe 0
            stats.bestGameStreak shouldBe 0
        }

        should("round the win rate to the nearest percent") {
            val sudokus = listOf(testSudoku(completed = true), testSudoku(completed = false), testSudoku(completed = false))

            useCase(sudokus).winRate shouldBe 33
        }

        should("report averageTime as 0, not -1, once at least one game is completed") {
            val sudokus = listOf(testSudoku(completed = true, seconds = 10), testSudoku(completed = true, seconds = 20))

            useCase(sudokus).averageTime shouldBe 15
        }

        should("count the current streak back from the most recently updated game, stopping at the first loss") {
            val sudokus =
                listOf(
                    testSudoku(completed = false, updated = LocalDateTime.now().minusMinutes(3)),
                    testSudoku(completed = true, updated = LocalDateTime.now().minusMinutes(2)),
                    testSudoku(completed = true, updated = LocalDateTime.now().minusMinutes(1)),
                )

            useCase(sudokus).currentGameStreak shouldBe 2
        }

        should("track the best streak even when it isn't the current one") {
            val sudokus =
                listOf(
                    testSudoku(completed = true, updated = LocalDateTime.now().minusMinutes(4)),
                    testSudoku(completed = true, updated = LocalDateTime.now().minusMinutes(3)),
                    testSudoku(completed = false, updated = LocalDateTime.now().minusMinutes(2)),
                    testSudoku(completed = true, updated = LocalDateTime.now().minusMinutes(1)),
                )

            val stats = useCase(sudokus)
            stats.bestGameStreak shouldBe 2
            stats.currentGameStreak shouldBe 1
        }

        should("break a tie between equally-played difficulties by first appearance") {
            val sudokus =
                listOf(
                    testSudoku(completed = false, difficulty = EASY),
                    testSudoku(completed = false, difficulty = MEDIUM),
                    testSudoku(completed = false, difficulty = EASY),
                    testSudoku(completed = false, difficulty = MEDIUM),
                )

            useCase(sudokus).mostGamesStartedDifficulty shouldBe EASY
        }

        should("report the max errors/hints/notes made across all games, not just completed ones") {
            val sudokus =
                listOf(
                    testSudoku(completed = false, errorsMade = 5, hintsUsed = 1, notesMade = 2),
                    testSudoku(completed = true, errorsMade = 1, hintsUsed = 4, notesMade = 9),
                )

            val stats = useCase(sudokus)
            stats.mostErrors shouldBe 5
            stats.mostHints shouldBe 4
            stats.mostNotes shouldBe 9
        }
    },
)
