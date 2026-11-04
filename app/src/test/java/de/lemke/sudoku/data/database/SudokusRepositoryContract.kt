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

package de.lemke.sudoku.data.database

import app.cash.turbine.test
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Behavior every [SudokusRepository] shares; each subclass runs it against one implementation. */
abstract class SudokusRepositoryContract {
    protected abstract val repository: SudokusRepository

    /** Stores [sudoku] with a board that cannot be read, so a test can add an unreadable game. */
    protected abstract suspend fun storeUnreadable(sudoku: Sudoku)

    /** The ids of every stored game, readable or not. */
    protected abstract suspend fun storedIds(): Set<String>

    private suspend fun allSummaries() = repository.observeAllSummaries().first()

    private fun sudoku(
        size: SudokuSize = SudokuSize.FOUR,
        mode: GameMode = GameMode.Normal,
        created: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
        updated: LocalDateTime = created,
    ): Sudoku = testSudoku(size = size, mode = mode, created = created, updated = updated)

    private fun daily(
        size: SudokuSize = SudokuSize.FOUR,
        created: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
        updated: LocalDateTime = created,
    ): Sudoku = sudoku(size = size, mode = GameMode.Daily(created.toLocalDate()), created = created, updated = updated)

    @Test
    fun `observeAllSummaries returns an empty list when nothing was saved`() =
        runTest {
            allSummaries().shouldBeEmpty()
        }

    @Test
    fun `saveSudoku then observeAllSummaries lists the sudoku`() =
        runTest {
            val saved = sudoku()
            repository.saveSudoku(saved)

            allSummaries()
                .single()
                .info.id shouldBe saved.info.id
        }

    @Test
    fun `getSudokuById returns null for an unknown id`() =
        runTest {
            repository.getSudokuById(sudoku().info.id).shouldBeNull()
        }

    @Test
    fun `getSudokuById returns the matching sudoku`() =
        runTest {
            val saved = sudoku()
            repository.saveSudoku(saved)

            repository.getSudokuById(saved.info.id)?.info?.id shouldBe saved.info.id
        }

    @Test
    fun `getRecentlyUpdatedNormalSummary returns null when there are no normal sudokus`() =
        runTest {
            repository.saveSudoku(sudoku(mode = GameMode.Level(1)))

            repository.getRecentlyUpdatedNormalSummary().shouldBeNull()
        }

    @Test
    fun `getRecentlyUpdatedNormalSummary returns the most recently updated normal sudoku`() =
        runTest {
            val older = sudoku(updated = LocalDateTime.now().minusDays(1))
            val newer = sudoku(updated = LocalDateTime.now())
            repository.saveSudoku(older)
            repository.saveSudoku(newer)

            repository.getRecentlyUpdatedNormalSummary()?.info?.id shouldBe newer.info.id
        }

    @Test
    fun `observeLevelSummaries returns level sudokus for the given size ordered by level descending`() =
        runTest {
            repository.saveSudoku(sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(1)))
            repository.saveSudoku(sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(2)))
            repository.saveSudoku(sudoku(size = SudokuSize.NINE, mode = GameMode.Level(1)))

            repository.observeLevelSummaries(SudokuSize.FOUR).first().map { it.info.mode } shouldBe
                listOf(GameMode.Level(2), GameMode.Level(1))
        }

    @Test
    fun `observeAllSummaries orders by updated descending`() =
        runTest {
            val middle = sudoku(updated = LocalDateTime.of(2026, 3, 14, 12, 0))
            val oldest = sudoku(mode = GameMode.Level(1), updated = LocalDateTime.of(2026, 3, 13, 12, 0))
            val newest = daily(updated = LocalDateTime.of(2026, 3, 15, 12, 0))
            repository.saveSudoku(middle)
            repository.saveSudoku(oldest)
            repository.saveSudoku(newest)

            allSummaries().map { it.info.id } shouldBe listOf(newest.info.id, middle.info.id, oldest.info.id)
        }

    @Test
    fun `observeAllSudokus orders by updated descending`() =
        runTest {
            val middle = sudoku(updated = LocalDateTime.of(2026, 3, 14, 12, 0))
            val oldest = sudoku(mode = GameMode.Level(1), updated = LocalDateTime.of(2026, 3, 13, 12, 0))
            val newest = daily(updated = LocalDateTime.of(2026, 3, 15, 12, 0))
            repository.saveSudoku(middle)
            repository.saveSudoku(oldest)
            repository.saveSudoku(newest)

            repository.observeAllSummaries().test {
                awaitItem().map { it.info.id } shouldBe listOf(newest.info.id, middle.info.id, oldest.info.id)
            }
        }

    @Test
    fun `observeAllNormalSudokus orders by updated descending`() =
        runTest {
            val middle = sudoku(updated = LocalDateTime.of(2026, 3, 14, 12, 0))
            val oldest = sudoku(updated = LocalDateTime.of(2026, 3, 13, 12, 0))
            val newest = sudoku(updated = LocalDateTime.of(2026, 3, 15, 12, 0))
            repository.saveSudoku(middle)
            repository.saveSudoku(oldest)
            repository.saveSudoku(newest)

            repository.observeNormalSummaries().test {
                awaitItem().map { it.info.id } shouldBe listOf(newest.info.id, middle.info.id, oldest.info.id)
            }
        }

    @Test
    fun `observeDailySummaries orders by created descending`() =
        runTest {
            val middle = daily(created = LocalDateTime.of(2026, 3, 14, 12, 0))
            val oldest =
                daily(
                    created = LocalDateTime.of(2026, 3, 13, 12, 0),
                    updated = LocalDateTime.of(2026, 3, 20, 12, 0),
                )
            val newest = daily(created = LocalDateTime.of(2026, 3, 15, 12, 0))
            repository.saveSudoku(middle)
            repository.saveSudoku(oldest)
            repository.saveSudoku(newest)

            repository.observeDailySummaries().first().map { it.info.id } shouldBe listOf(newest.info.id, middle.info.id, oldest.info.id)
        }

    @Test
    fun `observeDailySudokus orders by created descending`() =
        runTest {
            val middle = daily(created = LocalDateTime.of(2026, 3, 14, 12, 0))
            val oldest =
                daily(
                    created = LocalDateTime.of(2026, 3, 13, 12, 0),
                    updated = LocalDateTime.of(2026, 3, 20, 12, 0),
                )
            val newest = daily(created = LocalDateTime.of(2026, 3, 15, 12, 0))
            repository.saveSudoku(middle)
            repository.saveSudoku(oldest)
            repository.saveSudoku(newest)

            repository.observeDailySummaries().test {
                awaitItem().map { it.info.id } shouldBe listOf(newest.info.id, middle.info.id, oldest.info.id)
            }
        }

    @Test
    fun `observeDailySummaries returns only daily sudokus`() =
        runTest {
            repository.saveSudoku(daily(created = LocalDateTime.of(2026, 3, 14, 12, 0)))
            repository.saveSudoku(sudoku(mode = GameMode.Normal))

            val dailies = repository.observeDailySummaries().first()
            dailies shouldHaveSize 1
            dailies.single().info.mode shouldBe GameMode.Daily(LocalDate.of(2026, 3, 14))
        }

    @Test
    fun `deleteSudokus removes the sudoku`() =
        runTest {
            val saved = sudoku()
            repository.saveSudoku(saved)

            repository.deleteSudokus(listOf(saved.info.id))

            allSummaries().shouldBeEmpty()
        }

    @Test
    fun `deleteSudokus of a sudoku that was never saved keeps the stored sudokus`() =
        runTest {
            val kept = sudoku()
            repository.saveSudoku(kept)

            repository.deleteSudokus(listOf(sudoku().info.id))

            storedIds() shouldBe setOf(kept.info.id.value)
        }

    @Test
    fun `saveSudoku replaces the existing daily sudoku for the same day`() =
        runTest {
            val day = LocalDateTime.now()
            val first = daily(created = day)
            repository.saveSudoku(first)

            val second = daily(created = day)
            repository.saveSudoku(second)

            val all = allSummaries()
            all shouldHaveSize 1
            all.single().info.id shouldBe second.info.id
        }

    @Test
    fun `saveSudoku keeps daily sudokus from different days`() =
        runTest {
            val first = daily(created = LocalDateTime.now().minusDays(1))
            repository.saveSudoku(first)

            val second = daily(created = LocalDateTime.now())
            repository.saveSudoku(second)

            allSummaries() shouldHaveSize 2
        }

    @Test
    fun `saveSudoku replaces the existing level sudoku for the same size and level`() =
        runTest {
            val first = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3))
            repository.saveSudoku(first)

            val second = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3))
            repository.saveSudoku(second)

            val all = allSummaries()
            all shouldHaveSize 1
            all.single().info.id shouldBe second.info.id
        }

    @Test
    fun `saveSudoku replaces an unreadable level sudoku of the same size and level`() =
        runTest {
            val unreadable = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3))
            storeUnreadable(unreadable)

            val second = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3))
            repository.saveSudoku(second)

            storedIds() shouldBe setOf(second.info.id.value)
        }

    @Test
    fun `saveSudoku replaces an unreadable daily sudoku of the same day`() =
        runTest {
            val day = LocalDateTime.of(2026, 3, 14, 12, 0)
            storeUnreadable(daily(created = day))

            val second = daily(created = day.plusHours(1))
            repository.saveSudoku(second)

            storedIds() shouldBe setOf(second.info.id.value)
        }

    @Test
    fun `saveSudoku keeps a level sudoku of another size with the same level`() =
        runTest {
            val four = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(1))
            repository.saveSudoku(four)

            val nine = sudoku(size = SudokuSize.NINE, mode = GameMode.Level(1))
            repository.saveSudoku(nine)

            storedIds() shouldBe setOf(four.info.id.value, nine.info.id.value)
        }

    @Test
    fun `saveSudoku replaces the first of two level 1 sudokus of one size`() =
        runTest {
            val first = sudoku(size = SudokuSize.NINE, mode = GameMode.Level(1))
            val second = sudoku(size = SudokuSize.NINE, mode = GameMode.Level(1))
            repository.saveSudoku(first)
            repository.saveSudoku(second)

            repository.observeLevelSummaries(SudokuSize.NINE).first().map { it.info.id } shouldBe listOf(second.info.id)
        }

    @Test
    fun `an unreadable board reads as null`() =
        runTest {
            val unreadable = sudoku()
            storeUnreadable(unreadable)

            repository.getSudokuById(unreadable.info.id).shouldBeNull()
            allSummaries().map { it.info.id } shouldBe listOf(unreadable.info.id)
        }

    @Test
    fun `saveSudoku then getSudokuById restores every field of the game`() =
        runTest {
            val played =
                sudoku(mode = GameMode.Level(4)).let {
                    it.copy(
                        info =
                            it.info.copy(
                                seconds = 95,
                                hintsUsed = 1,
                                notesMade = 2,
                                errorsMade = 3,
                                eraserUsed = true,
                                checklistNumber = 5,
                            ),
                    )
                }
            repository.saveSudoku(played)

            repository.getSudokuById(played.info.id) shouldBe played
        }

    @Test
    fun `saveSudoku replaces the daily sudoku of the new day when an existing id is saved with another day`() =
        runTest {
            val today = LocalDateTime.of(2026, 3, 15, 12, 0)
            val first = daily(created = today.minusDays(1))
            val other = daily(created = today)
            repository.saveSudoku(first)
            repository.saveSudoku(other)

            val moved = first.copy(info = first.info.copy(mode = GameMode.Daily(today.toLocalDate()), created = today))
            repository.saveSudoku(moved)

            val all = allSummaries()
            all shouldHaveSize 1
            all.single().info.id shouldBe first.info.id
        }

    @Test
    fun `saveSudoku replaces the level sudoku of the new level when an existing id is saved with another level`() =
        runTest {
            val first = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(1))
            val other = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(2))
            repository.saveSudoku(first)
            repository.saveSudoku(other)

            repository.saveSudoku(first.copy(info = first.info.copy(mode = GameMode.Level(2))))

            val all = allSummaries()
            all shouldHaveSize 1
            all.single().info.id shouldBe first.info.id
        }

    @Test
    fun `saveSudoku does not delete the daily sudoku being updated`() =
        runTest {
            val saved = daily()
            repository.saveSudoku(saved)

            repository.saveSudoku(saved)

            val all = allSummaries()
            all shouldHaveSize 1
            all.single().info.id shouldBe saved.info.id
        }

    @Test
    fun `saveSudoku does not delete the level sudoku being updated`() =
        runTest {
            val level = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3))
            repository.saveSudoku(level)

            repository.saveSudoku(level)

            val all = allSummaries()
            all shouldHaveSize 1
            all.single().info.id shouldBe level.info.id
        }

    @Test
    fun `getMaxSudokuLevel returns 0 when no level sudoku exists for the size`() =
        runTest {
            repository.getMaxSudokuLevel(SudokuSize.FOUR) shouldBe 0
        }

    @Test
    fun `getMaxSudokuLevel returns the highest existing level for the size`() =
        runTest {
            repository.saveSudoku(sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(1)))
            repository.saveSudoku(sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(5)))

            repository.getMaxSudokuLevel(SudokuSize.FOUR) shouldBe 5
        }

    @Test
    fun `getMaxSudokuLevel ignores daily sudokus`() =
        runTest {
            repository.saveSudoku(daily(size = SudokuSize.NINE))

            repository.getMaxSudokuLevel(SudokuSize.NINE) shouldBe 0
        }

    @Test
    fun `observeAllSudokus starts empty and emits after a sudoku is saved`() =
        runTest {
            repository.observeAllSummaries().test {
                awaitItem().shouldBeEmpty()

                val saved = sudoku()
                repository.saveSudoku(saved)

                awaitItem().single().info.id shouldBe saved.info.id
            }
        }

    @Test
    fun `observeAllSudokus re-emits when a save leaves the rows unchanged`() =
        runTest {
            val saved = sudoku()
            repository.saveSudoku(saved)

            repository.observeAllSummaries().test {
                awaitItem().single().info.id shouldBe saved.info.id

                repository.saveSudoku(saved)

                awaitItem().single().info.id shouldBe saved.info.id
            }
        }

    @Test
    fun `observeAllNormalSudokus only emits normal-mode sudokus`() =
        runTest {
            repository.observeNormalSummaries().test {
                awaitItem().shouldBeEmpty()

                // Every write re-emits, even one that leaves the filtered list unchanged.
                repository.saveSudoku(daily())
                awaitItem().shouldBeEmpty()

                repository.saveSudoku(sudoku(mode = GameMode.Normal))
                awaitItem().single().info.mode shouldBe GameMode.Normal
            }
        }

    @Test
    fun `observeSudokuLevel only emits level sudokus for the requested size`() =
        runTest {
            repository.observeLevelSummaries(SudokuSize.FOUR).test {
                awaitItem().shouldBeEmpty()

                repository.saveSudoku(sudoku(size = SudokuSize.NINE, mode = GameMode.Level(1)))
                awaitItem().shouldBeEmpty()

                repository.saveSudoku(sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(1)))
                awaitItem().single().info.size shouldBe SudokuSize.FOUR
            }
        }

    @Test
    fun `observeDailySudokus only emits daily sudokus`() =
        runTest {
            repository.observeDailySummaries().test {
                awaitItem().shouldBeEmpty()

                repository.saveSudoku(sudoku(mode = GameMode.Normal))
                awaitItem().shouldBeEmpty()

                repository.saveSudoku(daily(created = LocalDateTime.of(2026, 3, 14, 12, 0)))
                awaitItem().single().info.mode shouldBe GameMode.Daily(LocalDate.of(2026, 3, 14))
            }
        }

    @Test
    fun `a summary carries the outcome of the board`() =
        runTest {
            val completed = testSudoku(completed = true)
            val open = sudoku()
            repository.saveSudoku(completed)
            repository.saveSudoku(open)

            allSummaries().associate { it.info.id to (it.completed to it.progress) } shouldBe
                mapOf(completed.info.id to (true to 100), open.info.id to (false to 0))
        }

    @Test
    fun `getCompletedSummaries lists only the completed games`() =
        runTest {
            val completed = testSudoku(completed = true)
            repository.saveSudoku(completed)
            repository.saveSudoku(sudoku())

            repository.getCompletedSummaries().map { it.info.id } shouldBe listOf(completed.info.id)
        }

    @Test
    fun `isDailyCompleted sees the completed daily of the day`() =
        runTest {
            val day = LocalDateTime.of(2026, 11, 3, 8, 0)
            repository.saveSudoku(testSudoku(mode = GameMode.Daily(day.toLocalDate()), created = day, completed = true))

            repository.isDailyCompleted(LocalDate.of(2026, 11, 3)) shouldBe true
            repository.isDailyCompleted(LocalDate.of(2026, 11, 2)) shouldBe false
        }

    @Test
    fun `isDailyCompleted is false for an uncompleted daily of the day`() =
        runTest {
            repository.saveSudoku(daily(created = LocalDateTime.of(2026, 11, 3, 8, 0)))

            repository.isDailyCompleted(LocalDate.of(2026, 11, 3)) shouldBe false
        }

    @Test
    fun `isDailyCompleted ignores a completed game that is not daily`() =
        runTest {
            repository.saveSudoku(testSudoku(created = LocalDateTime.of(2026, 11, 3, 8, 0), completed = true))

            repository.isDailyCompleted(LocalDate.of(2026, 11, 3)) shouldBe false
        }

    @Test
    fun `hasDaily is true only for a day with a daily`() =
        runTest {
            repository.saveSudoku(daily(created = LocalDateTime.of(2026, 11, 3, 8, 0)))

            repository.hasDaily(LocalDate.of(2026, 11, 3)) shouldBe true
            repository.hasDaily(LocalDate.of(2026, 11, 4)) shouldBe false
        }

    @Test
    fun `getLevelNumbers lists the levels of one size`() =
        runTest {
            repository.saveSudoku(sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(1)))
            repository.saveSudoku(sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3)))
            repository.saveSudoku(sudoku(size = SudokuSize.NINE, mode = GameMode.Level(2)))
            repository.saveSudoku(sudoku(size = SudokuSize.FOUR))

            repository.getLevelNumbers(SudokuSize.FOUR) shouldBe setOf(1, 3)
        }

    @Test
    fun `deleteSudokus removes more games than one query can bind`() =
        runTest {
            val games = List(MORE_THAN_ONE_CHUNK) { sudoku() }
            games.forEach { repository.saveSudoku(it) }

            repository.deleteSudokus(games.map { it.info.id })

            storedIds() shouldBe emptySet()
        }
}

private const val MORE_THAN_ONE_CHUNK = 501
