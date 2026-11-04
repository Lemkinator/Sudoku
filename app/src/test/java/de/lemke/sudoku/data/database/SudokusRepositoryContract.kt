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
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Behavior every [SudokusRepository] shares; each subclass runs it against one implementation. */
abstract class SudokusRepositoryContract {
    protected abstract val repository: SudokusRepository

    /** Stores [rows] unchecked, as a raw DAO insert does, so a test can add an unreadable row. */
    protected abstract suspend fun insertRows(rows: SudokuWithFields)

    /** The ids of every stored row, readable or not. */
    protected abstract suspend fun storedIds(): Set<String>

    private fun sudoku(
        size: SudokuSize = SudokuSize.FOUR,
        mode: GameMode = GameMode.Normal,
        created: LocalDateTime = LocalDateTime.now(),
        updated: LocalDateTime = created,
    ): Sudoku = testSudoku(size = size, mode = mode, created = created, updated = updated)

    private fun SudokuWithFields.withoutFirstField() = copy(fields = fields.drop(1))

    @Test
    fun `getAllSudokus returns an empty list when nothing was saved`() =
        runTest {
            repository.getAllSudokus().shouldBeEmpty()
        }

    @Test
    fun `saveSudoku then getAllSudokus round-trips the sudoku`() =
        runTest {
            val saved = sudoku()
            repository.saveSudoku(saved)

            repository
                .getAllSudokus()
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
    fun `getRecentlyUpdatedNormalSudoku returns null when there are no normal sudokus`() =
        runTest {
            repository.saveSudoku(sudoku(mode = GameMode.Level(1)))

            repository.getRecentlyUpdatedNormalSudoku().shouldBeNull()
        }

    @Test
    fun `getRecentlyUpdatedNormalSudoku returns the most recently updated normal sudoku`() =
        runTest {
            val older = sudoku(updated = LocalDateTime.now().minusDays(1))
            val newer = sudoku(updated = LocalDateTime.now())
            repository.saveSudoku(older)
            repository.saveSudoku(newer)

            repository.getRecentlyUpdatedNormalSudoku()?.info?.id shouldBe newer.info.id
        }

    @Test
    fun `getSudokuLevel returns level sudokus for the given size ordered by level descending`() =
        runTest {
            repository.saveSudoku(sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(1)))
            repository.saveSudoku(sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(2)))
            repository.saveSudoku(sudoku(size = SudokuSize.NINE, mode = GameMode.Level(1)))

            repository.getSudokuLevel(SudokuSize.FOUR).map { it.info.mode } shouldBe listOf(GameMode.Level(2), GameMode.Level(1))
        }

    @Test
    fun `getAllSudokus orders by updated descending`() =
        runTest {
            val middle = sudoku(updated = LocalDateTime.of(2026, 3, 14, 12, 0))
            val oldest = sudoku(mode = GameMode.Level(1), updated = LocalDateTime.of(2026, 3, 13, 12, 0))
            val newest = sudoku(mode = GameMode.Daily, updated = LocalDateTime.of(2026, 3, 15, 12, 0))
            repository.saveSudoku(middle)
            repository.saveSudoku(oldest)
            repository.saveSudoku(newest)

            repository.getAllSudokus().map { it.info.id } shouldBe listOf(newest.info.id, middle.info.id, oldest.info.id)
        }

    @Test
    fun `observeAllSudokus orders by updated descending`() =
        runTest {
            val middle = sudoku(updated = LocalDateTime.of(2026, 3, 14, 12, 0))
            val oldest = sudoku(mode = GameMode.Level(1), updated = LocalDateTime.of(2026, 3, 13, 12, 0))
            val newest = sudoku(mode = GameMode.Daily, updated = LocalDateTime.of(2026, 3, 15, 12, 0))
            repository.saveSudoku(middle)
            repository.saveSudoku(oldest)
            repository.saveSudoku(newest)

            repository.observeAllSudokus().test {
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

            repository.observeAllNormalSudokus().test {
                awaitItem().map { it.info.id } shouldBe listOf(newest.info.id, middle.info.id, oldest.info.id)
            }
        }

    @Test
    fun `getDailySudokus orders by created descending`() =
        runTest {
            val middle = sudoku(mode = GameMode.Daily, created = LocalDateTime.of(2026, 3, 14, 12, 0))
            val oldest =
                sudoku(
                    mode = GameMode.Daily,
                    created = LocalDateTime.of(2026, 3, 13, 12, 0),
                    updated = LocalDateTime.of(2026, 3, 20, 12, 0),
                )
            val newest = sudoku(mode = GameMode.Daily, created = LocalDateTime.of(2026, 3, 15, 12, 0))
            repository.saveSudoku(middle)
            repository.saveSudoku(oldest)
            repository.saveSudoku(newest)

            repository.getDailySudokus().map { it.info.id } shouldBe listOf(newest.info.id, middle.info.id, oldest.info.id)
        }

    @Test
    fun `observeDailySudokus orders by created descending`() =
        runTest {
            val middle = sudoku(mode = GameMode.Daily, created = LocalDateTime.of(2026, 3, 14, 12, 0))
            val oldest =
                sudoku(
                    mode = GameMode.Daily,
                    created = LocalDateTime.of(2026, 3, 13, 12, 0),
                    updated = LocalDateTime.of(2026, 3, 20, 12, 0),
                )
            val newest = sudoku(mode = GameMode.Daily, created = LocalDateTime.of(2026, 3, 15, 12, 0))
            repository.saveSudoku(middle)
            repository.saveSudoku(oldest)
            repository.saveSudoku(newest)

            repository.observeDailySudokus().test {
                awaitItem().map { it.info.id } shouldBe listOf(newest.info.id, middle.info.id, oldest.info.id)
            }
        }

    @Test
    fun `getDailySudokus returns only daily sudokus`() =
        runTest {
            repository.saveSudoku(sudoku(mode = GameMode.Daily))
            repository.saveSudoku(sudoku(mode = GameMode.Normal))

            val dailies = repository.getDailySudokus()
            dailies shouldHaveSize 1
            dailies.single().info.mode shouldBe GameMode.Daily
        }

    @Test
    fun `deleteSudoku removes the sudoku and its fields`() =
        runTest {
            val saved = sudoku()
            repository.saveSudoku(saved)

            repository.deleteSudoku(saved)

            repository.getAllSudokus().shouldBeEmpty()
        }

    @Test
    fun `deleteSudoku of a sudoku that was never saved keeps the stored sudokus`() =
        runTest {
            val kept = sudoku()
            repository.saveSudoku(kept)

            repository.deleteSudoku(sudoku())

            storedIds() shouldBe setOf(kept.info.id.value)
        }

    @Test
    fun `saveSudoku replaces the existing daily sudoku for the same day`() =
        runTest {
            val day = LocalDateTime.now()
            val first = sudoku(mode = GameMode.Daily, created = day)
            repository.saveSudoku(first)

            val second = sudoku(mode = GameMode.Daily, created = day)
            repository.saveSudoku(second)

            val all = repository.getAllSudokus()
            all shouldHaveSize 1
            all.single().info.id shouldBe second.info.id
        }

    @Test
    fun `saveSudoku keeps daily sudokus from different days`() =
        runTest {
            val first = sudoku(mode = GameMode.Daily, created = LocalDateTime.now().minusDays(1))
            repository.saveSudoku(first)

            val second = sudoku(mode = GameMode.Daily, created = LocalDateTime.now())
            repository.saveSudoku(second)

            repository.getAllSudokus() shouldHaveSize 2
        }

    @Test
    fun `saveSudoku replaces the existing level sudoku for the same size and level`() =
        runTest {
            val first = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3))
            repository.saveSudoku(first)

            val second = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3))
            repository.saveSudoku(second)

            val all = repository.getAllSudokus()
            all shouldHaveSize 1
            all.single().info.id shouldBe second.info.id
        }

    @Test
    fun `saveSudoku keeps an unreadable level sudoku for the same size and level`() =
        runTest {
            val unreadable = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3))
            insertRows(sudokuWithFieldsToDb(unreadable).withoutFirstField())

            val second = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3))
            repository.saveSudoku(second)

            storedIds() shouldBe setOf(unreadable.info.id.value, second.info.id.value)
        }

    @Test
    fun `saveSudoku keeps an unreadable daily sudoku for the same day`() =
        runTest {
            val day = LocalDateTime.now()
            val unreadable = sudoku(mode = GameMode.Daily, created = day)
            insertRows(sudokuWithFieldsToDb(unreadable).withoutFirstField())

            val second = sudoku(mode = GameMode.Daily, created = day)
            repository.saveSudoku(second)

            storedIds() shouldBe setOf(unreadable.info.id.value, second.info.id.value)
        }

    @Test
    fun `saveSudoku replaces the readable level sudoku behind an unreadable one for the same size and level`() =
        runTest {
            val unreadable = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3))
            insertRows(sudokuWithFieldsToDb(unreadable).withoutFirstField())
            repository.saveSudoku(sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3)))

            val third = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3))
            repository.saveSudoku(third)

            storedIds() shouldBe setOf(unreadable.info.id.value, third.info.id.value)
        }

    @Test
    fun `saveSudoku replaces the readable daily sudoku behind an unreadable one for the same day`() =
        runTest {
            val day = LocalDateTime.of(2026, 3, 14, 12, 0)
            val unreadable = sudoku(mode = GameMode.Daily, created = day)
            insertRows(sudokuWithFieldsToDb(unreadable).withoutFirstField())
            repository.saveSudoku(sudoku(mode = GameMode.Daily, created = day.minusSeconds(1)))

            val third = sudoku(mode = GameMode.Daily, created = day)
            repository.saveSudoku(third)

            storedIds() shouldBe setOf(unreadable.info.id.value, third.info.id.value)
        }

    @Test
    fun `saveSudoku replaces the daily sudoku of the new day when an existing id is saved with another day`() =
        runTest {
            val today = LocalDateTime.of(2026, 3, 15, 12, 0)
            val first = sudoku(mode = GameMode.Daily, created = today.minusDays(1))
            val other = sudoku(mode = GameMode.Daily, created = today)
            repository.saveSudoku(first)
            repository.saveSudoku(other)

            val moved = first.copy(info = first.info.copy(created = today))
            repository.saveSudoku(moved)

            val all = repository.getAllSudokus()
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

            val all = repository.getAllSudokus()
            all shouldHaveSize 1
            all.single().info.id shouldBe first.info.id
        }

    @Test
    fun `saveSudoku does not delete the daily sudoku being updated`() =
        runTest {
            val daily = sudoku(mode = GameMode.Daily)
            repository.saveSudoku(daily)

            repository.saveSudoku(daily)

            val all = repository.getAllSudokus()
            all shouldHaveSize 1
            all.single().info.id shouldBe daily.info.id
        }

    @Test
    fun `saveSudoku does not delete the level sudoku being updated`() =
        runTest {
            val level = sudoku(size = SudokuSize.FOUR, mode = GameMode.Level(3))
            repository.saveSudoku(level)

            repository.saveSudoku(level)

            val all = repository.getAllSudokus()
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
            repository.saveSudoku(sudoku(size = SudokuSize.NINE, mode = GameMode.Daily))

            repository.getMaxSudokuLevel(SudokuSize.NINE) shouldBe 0
        }

    @Test
    fun `deleteInvalidSudokus leaves sudokus whose stored field count matches size squared untouched`() =
        runTest {
            val valid = sudoku(size = SudokuSize.FOUR)
            repository.saveSudoku(valid)

            repository.deleteInvalidSudokus()

            storedIds() shouldHaveSize 1
        }

    @Test
    fun `deleteInvalidSudokus deletes a row whose stored field count does not match size squared and keeps valid rows`() =
        runTest {
            val valid = sudoku(size = SudokuSize.FOUR)
            repository.saveSudoku(valid)
            val invalid = sudoku(size = SudokuSize.FOUR)
            insertRows(sudokuWithFieldsToDb(invalid).let { it.copy(fields = it.fields.dropLast(1)) })
            storedIds() shouldHaveSize 2

            repository.deleteInvalidSudokus()

            storedIds() shouldBe setOf(valid.info.id.value)
        }

    @Test
    fun `deleteInvalidSudokus deletes a row with a correct field count but a null solution and keeps valid rows`() =
        runTest {
            val valid = sudoku(size = SudokuSize.FOUR)
            repository.saveSudoku(valid)
            val invalid = sudoku(size = SudokuSize.FOUR)
            val rows = sudokuWithFieldsToDb(invalid)
            val fields = rows.fields.mapIndexed { index, field -> if (index == 0) field.copy(solution = null) else field }
            insertRows(rows.copy(fields = fields))
            storedIds() shouldHaveSize 2

            repository.deleteInvalidSudokus()

            storedIds() shouldBe setOf(valid.info.id.value)
        }

    @Test
    fun `deleteInvalidSudokus deletes a zero-sized corrupted row instead of throwing`() =
        runTest {
            val valid = sudoku(size = SudokuSize.FOUR)
            repository.saveSudoku(valid)
            val invalid = sudoku(size = SudokuSize.FOUR)
            insertRows(
                SudokuWithFields(
                    sudokuToDb(invalid).copy(size = 0),
                    listOf(sudokuWithFieldsToDb(invalid).fields.first().copy(gameSize = 0)),
                ),
            )
            storedIds() shouldHaveSize 2

            repository.deleteInvalidSudokus()

            storedIds() shouldBe setOf(valid.info.id.value)
        }

    @Test
    fun `deleteInvalidSudokus deletes a row with an unsupported size and keeps valid rows`() =
        runTest {
            val valid = sudoku()
            repository.saveSudoku(valid)
            val invalid = sudokuToDb(sudoku()).copy(size = 6)
            insertRows(
                SudokuWithFields(
                    invalid,
                    List(36) { index ->
                        FieldDb(
                            sudokuId = invalid.id,
                            gameSize = 6,
                            index = index,
                            solution = index % 6 + 1,
                            value = null,
                            given = false,
                            hint = false,
                            notes = "",
                        )
                    },
                ),
            )
            storedIds() shouldHaveSize 2

            repository.deleteInvalidSudokus()

            storedIds() shouldBe setOf(valid.info.id.value)
        }

    @Test
    fun `observeAllSudokus starts empty and emits after a sudoku is saved`() =
        runTest {
            repository.observeAllSudokus().test {
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

            repository.observeAllSudokus().test {
                awaitItem().single().info.id shouldBe saved.info.id

                repository.saveSudoku(saved)

                awaitItem().single().info.id shouldBe saved.info.id
            }
        }

    @Test
    fun `observeAllNormalSudokus only emits normal-mode sudokus`() =
        runTest {
            repository.observeAllNormalSudokus().test {
                awaitItem().shouldBeEmpty()

                // Every write re-emits, even one that leaves the filtered list unchanged.
                repository.saveSudoku(sudoku(mode = GameMode.Daily))
                awaitItem().shouldBeEmpty()

                repository.saveSudoku(sudoku(mode = GameMode.Normal))
                awaitItem().single().info.mode shouldBe GameMode.Normal
            }
        }

    @Test
    fun `observeSudokuLevel only emits level sudokus for the requested size`() =
        runTest {
            repository.observeSudokuLevel(SudokuSize.FOUR).test {
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
            repository.observeDailySudokus().test {
                awaitItem().shouldBeEmpty()

                repository.saveSudoku(sudoku(mode = GameMode.Normal))
                awaitItem().shouldBeEmpty()

                repository.saveSudoku(sudoku(mode = GameMode.Daily))
                awaitItem().single().info.mode shouldBe GameMode.Daily
            }
        }
}
