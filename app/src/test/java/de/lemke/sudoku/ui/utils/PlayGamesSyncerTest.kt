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
import android.app.Application
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.PlayGamesSync
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private val SYNC =
    PlayGamesSync(
        leaderboardScores = listOf(R.string.leaderboard_total_wins to 3L),
        achievementUnlocks = listOf(R.string.achievement_first_win),
        achievementSteps = listOf(R.string.achievement_10_sudokus_44 to 3),
    )

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class PlayGamesSyncerTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()
    private val gamesSignIn = FakeGamesSignIn()
    private val applier = FakePlayGamesSyncApplier()

    private fun sync(check: Task<Boolean>) {
        gamesSignIn.authenticationCheck = check
        val scope = TestScope()
        val job = scope.launch { PlayGamesSyncer(gamesSignIn, applier)(activity, SYNC) }
        scope.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        scope.advanceTimeBy(10_001)
        scope.runCurrent()
        job.isCompleted shouldBe true
    }

    @Test
    fun `applies the sync when the player is authenticated`() {
        sync(Tasks.forResult(true))

        applier.applied shouldBe listOf(SYNC)
    }

    @Test
    fun `applies nothing when the player is not authenticated`() {
        sync(Tasks.forResult(false))

        applier.applied shouldBe emptyList()
    }

    @Test
    fun `applies nothing when the authentication check never completes`() {
        sync(TaskCompletionSource<Boolean>().task)

        applier.applied shouldBe emptyList()
        gamesSignIn.authenticationChecks shouldBe 1
    }

    @Test
    fun `applies nothing when the authentication check failed`() {
        sync(Tasks.forException(IllegalStateException("no network")))

        applier.applied shouldBe emptyList()
    }
}
