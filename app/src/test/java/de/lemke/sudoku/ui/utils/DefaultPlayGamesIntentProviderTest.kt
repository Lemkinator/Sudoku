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
import android.content.Intent
import com.google.android.gms.games.AchievementsClient
import com.google.android.gms.games.LeaderboardsClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Task
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll

class DefaultPlayGamesIntentProviderTest : ShouldSpec(
    {
        val activity = mockk<Activity>()
        val achievementsTask = mockk<Task<Intent>>()
        val leaderboardsTask = mockk<Task<Intent>>()

        beforeEach {
            mockkStatic(PlayGames::class)
            every { PlayGames.getAchievementsClient(activity) } returns
                mockk<AchievementsClient> { every { achievementsIntent } returns achievementsTask }
            every { PlayGames.getLeaderboardsClient(activity) } returns
                mockk<LeaderboardsClient> { every { allLeaderboardsIntent } returns leaderboardsTask }
        }

        afterEach { unmockkAll() }

        should("fetch the achievements intent from the activity's achievements client") {
            DefaultPlayGamesIntentProvider().getIntent(activity, PlayGamesScreen.ACHIEVEMENTS) shouldBeSameInstanceAs achievementsTask
        }

        should("fetch the all-leaderboards intent from the activity's leaderboards client") {
            DefaultPlayGamesIntentProvider().getIntent(activity, PlayGamesScreen.LEADERBOARDS) shouldBeSameInstanceAs leaderboardsTask
        }
    },
)
