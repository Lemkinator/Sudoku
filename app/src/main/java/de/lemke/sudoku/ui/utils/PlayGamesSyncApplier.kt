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
import javax.inject.Inject

/** Performs the `PlayGames` client calls described by a [PlayGamesSync]; needs an [Activity] for the Play Games UI overlays. */
interface PlayGamesSyncApplier {
    fun apply(
        activity: Activity,
        sync: PlayGamesSync,
    )
}

class DefaultPlayGamesSyncApplier @Inject constructor() : PlayGamesSyncApplier {
    override fun apply(
        activity: Activity,
        sync: PlayGamesSync,
    ) {
        val achievements = PlayGames.getAchievementsClient(activity)
        val leaderboards = PlayGames.getLeaderboardsClient(activity)
        sync.leaderboardScores.forEach { (leaderboardId, score) -> leaderboards.submitScore(activity.getString(leaderboardId), score) }
        sync.achievementUnlocks.forEach { achievementId -> achievements.unlock(activity.getString(achievementId)) }
        sync.achievementIncrements.forEach { (achievementId, amount) -> achievements.increment(activity.getString(achievementId), amount) }
    }
}
