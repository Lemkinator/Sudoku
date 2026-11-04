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

import de.lemke.sudoku.data.codec.BoardCodec
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.GameMode
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.testSudoku
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.time.ZoneOffset

class GameEntityMapperTest : ShouldSpec(
    {
        val utc = ZoneOffset.UTC
        val created = LocalDateTime.of(2026, 10, 1, 8, 0, 0)
        val updated = LocalDateTime.of(2026, 10, 1, 9, 30, 15)

        fun sudoku(mode: GameMode = GameMode.Normal) =
            testSudoku(mode = mode, difficulty = Difficulty.HARD, created = created, updated = updated).let {
                it.copy(
                    info = it.info.copy(seconds = 95, hintsUsed = 2, notesMade = 3, errorsMade = 4, eraserUsed = true, checklistNumber = 5),
                )
            }

        fun columns(header: GameHeader) =
            listOf(
                header.mode,
                header.level,
                header.dailyDate,
                header.size,
                header.difficulty,
                header.createdAt,
                header.updatedAt,
                header.seconds,
                header.hintsUsed,
                header.notesMade,
                header.errorsMade,
                header.checklistNumber,
                header.progress,
            )

        should("write a normal game's header columns") {
            columns(sudoku().toEntity(utc).header) shouldBe
                listOf(0, null, null, 4, 3, 1790841600000L, 1790847015000L, 95, 2, 3, 4, 5, 0)
        }

        should("write a level game's level and no day") {
            val header = sudoku(GameMode.Level(7)).toEntity(utc).header

            listOf(header.mode, header.level, header.dailyDate) shouldBe listOf(2, 7, null)
        }

        should("write a daily game's day as its epoch day and no level") {
            val header = sudoku(GameMode.Daily).toEntity(utc).header

            listOf(header.mode, header.level, header.dailyDate) shouldBe listOf(1, null, 20727L)
        }

        should("write the usage flags and the outcome") {
            val completed = testSudoku(completed = true).toEntity(utc).header

            listOf(completed.completed, completed.progress, completed.eraserUsed, completed.isChecklist) shouldBe
                listOf(true, 100, false, false)
        }

        should("put the board in the board codec format") {
            val game = sudoku()

            game.toEntity(utc).board.toList() shouldBe BoardCodec.encode(game.board).toList()
        }

        should("round trip a game of every mode through its row") {
            listOf(GameMode.Normal, GameMode.Daily, GameMode.Level(7)).forEach { mode ->
                val game = sudoku(mode)

                game.toEntity(utc).toSudoku(utc) shouldBe game
            }
        }

        should("place the stamps on the zone it is read in") {
            val read = sudoku().toEntity(utc).toSudoku(ZoneOffset.ofHours(2)).shouldNotBeNull()

            read.info.created shouldBe LocalDateTime.of(2026, 10, 1, 10, 0, 0)
        }

        should("read no summary from a header with an unknown size, mode or difficulty") {
            val row = sudoku().toEntity(utc)
            val header = row.header

            header.copy(size = 6).toSummary(utc).shouldBeNull()
            header.copy(mode = 9).toSummary(utc).shouldBeNull()
            header.copy(difficulty = 7).toSummary(utc).shouldBeNull()
            header.toSummary(utc).shouldNotBeNull()
        }

        should("read no game from a board of another size than the header names") {
            val nine = testSudoku(size = SudokuSize.NINE).toEntity(utc)

            GameEntity(sudoku().toEntity(utc).header, nine.board).toSudoku(utc).shouldBeNull()
        }

        should("read no game from a board that cannot be decoded") {
            GameEntity(sudoku().toEntity(utc).header, byteArrayOf(1)).toSudoku(utc).shouldBeNull()
        }
    },
)
