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

object SudokuFilterFlags {
    const val TYPE_ALL = 1 shl 1
    const val TYPE_NORMAL = 1 shl 2
    const val TYPE_DAILY = 1 shl 3
    const val TYPE_LEVEL = 1 shl 4
    const val DIFFICULTY_ALL = 1 shl 10
    const val DIFFICULTY_VERY_EASY = 1 shl 11
    const val DIFFICULTY_EASY = 1 shl 12
    const val DIFFICULTY_MEDIUM = 1 shl 13
    const val DIFFICULTY_HARD = 1 shl 14
    const val DIFFICULTY_EXPERT = 1 shl 15
    const val SIZE_ALL = 1 shl 20
}

fun Int.matchesSudokuFilterFlags(info: GameInfo): Boolean = matchesType(info) && matchesSize(info) && matchesDifficulty(info)

private fun Int.matchesType(info: GameInfo): Boolean =
    this and SudokuFilterFlags.TYPE_ALL != 0 ||
        (this and SudokuFilterFlags.TYPE_NORMAL != 0 && info.mode == GameMode.Normal) ||
        (this and SudokuFilterFlags.TYPE_DAILY != 0 && info.mode is GameMode.Daily) ||
        (this and SudokuFilterFlags.TYPE_LEVEL != 0 && info.mode is GameMode.Level)

private fun Int.matchesSize(info: GameInfo): Boolean = this and SudokuFilterFlags.SIZE_ALL != 0 || this and info.size.filterFlag != 0

private fun Int.matchesDifficulty(info: GameInfo): Boolean =
    this and SudokuFilterFlags.DIFFICULTY_ALL != 0 ||
        (this and SudokuFilterFlags.DIFFICULTY_VERY_EASY != 0 && info.difficulty == Difficulty.VERY_EASY) ||
        (this and SudokuFilterFlags.DIFFICULTY_EASY != 0 && info.difficulty == Difficulty.EASY) ||
        (this and SudokuFilterFlags.DIFFICULTY_MEDIUM != 0 && info.difficulty == Difficulty.MEDIUM) ||
        (this and SudokuFilterFlags.DIFFICULTY_HARD != 0 && info.difficulty == Difficulty.HARD) ||
        (this and SudokuFilterFlags.DIFFICULTY_EXPERT != 0 && info.difficulty == Difficulty.EXPERT)
