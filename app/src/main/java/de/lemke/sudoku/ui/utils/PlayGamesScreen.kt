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
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private val SILENT_TASK_TIMEOUT = 10.seconds

/** A Play Games overlay screen that the drawer opens. */
enum class PlayGamesScreen {
    ACHIEVEMENTS,
    LEADERBOARDS,
    ;

    fun intent(activity: Activity): Task<Intent> =
        when (this) {
            ACHIEVEMENTS -> PlayGames.getAchievementsClient(activity).achievementsIntent
            LEADERBOARDS -> PlayGames.getLeaderboardsClient(activity).allLeaderboardsIntent
        }
}

/** The outcome of preparing a [PlayGamesScreen] for launch. */
sealed interface PlayGamesScreenLaunch {
    data class Ready(
        val intent: Intent,
    ) : PlayGamesScreenLaunch

    data object SignInFailed : PlayGamesScreenLaunch

    data object Unavailable : PlayGamesScreenLaunch
}

/**
 * Signs in to Play Games if needed and fetches the intent of [screen].
 *
 * The silent authentication check and the intent fetch give up after [SILENT_TASK_TIMEOUT]; the interactive sign-in
 * waits for the user.
 */
suspend fun Activity.preparePlayGamesScreen(
    client: GamesSignInClient,
    screen: PlayGamesScreen,
): PlayGamesScreenLaunch {
    val authenticated =
        client.isAuthenticated.silentResultOrNull()?.isAuthenticated == true ||
            client.signIn().resultOrNull()?.isAuthenticated == true
    if (!authenticated) return PlayGamesScreenLaunch.SignInFailed
    return screen.intent(this).silentResultOrNull()?.let(PlayGamesScreenLaunch::Ready) ?: PlayGamesScreenLaunch.Unavailable
}

private suspend fun <T> Task<T>.silentResultOrNull(): T? = withTimeoutOrNull(SILENT_TASK_TIMEOUT) { resultOrNull() }

private suspend fun <T> Task<T>.resultOrNull(): T? =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task -> continuation.resume(task.takeIf { it.isSuccessful }?.result) }
    }
