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
import com.google.android.gms.games.AchievementsClient
import com.google.android.gms.games.LeaderboardsClient
import com.google.android.gms.games.PlayGames
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.PlayGamesSync
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify

class DefaultPlayGamesSyncApplierTest : ShouldSpec(
    {
        val activity = mockk<Activity>()
        val achievements = mockk<AchievementsClient>(relaxed = true)
        val leaderboards = mockk<LeaderboardsClient>(relaxed = true)

        beforeEach {
            mockkStatic(PlayGames::class)
            every { PlayGames.getAchievementsClient(activity) } returns achievements
            every { PlayGames.getLeaderboardsClient(activity) } returns leaderboards
            every { activity.getString(R.string.leaderboard_total_wins) } returns "leaderboard-total-wins"
            every { activity.getString(R.string.achievement_eraser) } returns "achievement-eraser"
            every { activity.getString(R.string.achievement_use_notes) } returns "achievement-use-notes"
        }

        afterEach {
            clearMocks(achievements, leaderboards)
            unmockkAll()
        }

        should("submit every leaderboard score, unlock and increment every achievement under its resolved id") {
            DefaultPlayGamesSyncApplier().apply(
                activity,
                PlayGamesSync(
                    leaderboardScores = listOf(R.string.leaderboard_total_wins to 42L),
                    achievementUnlocks = listOf(R.string.achievement_eraser),
                    achievementIncrements = listOf(R.string.achievement_use_notes to 3),
                ),
            )

            verify(exactly = 1) { leaderboards.submitScore("leaderboard-total-wins", 42L) }
            verify(exactly = 1) { achievements.unlock("achievement-eraser") }
            verify(exactly = 1) { achievements.increment("achievement-use-notes", 3) }
        }

        should("call no Play Games client for an empty sync") {
            DefaultPlayGamesSyncApplier().apply(activity, PlayGamesSync())

            verify(exactly = 0) { leaderboards.submitScore(any(), any()) }
            verify(exactly = 0) { achievements.unlock(any()) }
            verify(exactly = 0) { achievements.increment(any(), any()) }
        }
    },
)
