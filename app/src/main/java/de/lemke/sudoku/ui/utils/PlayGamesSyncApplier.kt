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

package de.lemke.sudoku.ui.utils

import android.app.Activity
import com.google.android.gms.games.PlayGames
import de.lemke.sudoku.domain.model.PlayGamesSync

/** Performs the actual `PlayGames` client calls described by [sync]; requires an [Activity] for the Play Games UI overlays. */
fun Activity.applyPlayGamesSync(sync: PlayGamesSync) {
    val achievements = PlayGames.getAchievementsClient(this)
    val leaderboards = PlayGames.getLeaderboardsClient(this)
    sync.leaderboardScores.forEach { (leaderboardId, score) -> leaderboards.submitScore(getString(leaderboardId), score) }
    sync.achievementUnlocks.forEach { achievementId -> achievements.unlock(getString(achievementId)) }
    sync.achievementIncrements.forEach { (achievementId, amount) -> achievements.increment(getString(achievementId), amount) }
}
