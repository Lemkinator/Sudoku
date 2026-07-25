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

data class SudokuStatistics(
    val gamesStarted: Int,
    val gamesCompleted: Int,
    val winRate: Int,
    val bestTimeSudoku: Sudoku?,
    val averageTime: Int,
    val winsWithoutErrors: Int,
    val mostErrors: Int,
    val averageErrors: Int,
    val winsWithoutHints: Int,
    val mostHints: Int,
    val averageHints: Int,
    val winsWithoutNotes: Int,
    val mostNotes: Int,
    val averageNotes: Int,
    val mostGamesStartedDifficulty: Difficulty?,
    val mostGamesWonDifficulty: Difficulty?,
    val mostGamesStartedSize: Int?,
    val mostGamesWonSize: Int?,
    val currentGameStreak: Int,
    val bestGameStreak: Int,
    val totalSecondsPlayed: Long,
    val hintUsageRate: Float,
    val notesUsageRate: Float,
    val eraserUsageRate: Float,
    val perfectGames: Int,
)
