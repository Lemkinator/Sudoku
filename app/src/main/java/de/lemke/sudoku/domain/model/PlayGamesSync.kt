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

/**
 * Data-only description of what to sync to Play Games — leaderboard scores to submit, achievements to unlock, and
 * achievements to increment, each keyed by string resource id. Deliberately holds no [android.app.Activity] reference
 * so it can be computed on a background dispatcher; the UI layer resolves the string resources and performs the
 * actual `PlayGames` client calls.
 */
data class PlayGamesSync(
    val leaderboardScores: List<Pair<Int, Long>> = emptyList(),
    val achievementUnlocks: List<Int> = emptyList(),
    val achievementIncrements: List<Pair<Int, Int>> = emptyList(),
)
