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
import com.google.android.gms.games.AuthenticationResult
import com.google.android.gms.games.GamesSignInClient
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
 * Signs in to Play Games if needed and fetches the intent of [screen] from [intentProvider].
 *
 * The silent authentication check and the intent fetch give up after [SILENT_TASK_TIMEOUT] and end
 * [PlayGamesScreenLaunch.Unavailable]; the interactive sign-in waits for the user.
 */
suspend fun Activity.preparePlayGamesScreen(
    client: GamesSignInClient,
    intentProvider: PlayGamesIntentProvider,
    screen: PlayGamesScreen,
): PlayGamesScreenLaunch {
    val check = client.isAuthenticated.silentCompletionOrNull() ?: return PlayGamesScreenLaunch.Unavailable
    return if (check.authenticated || client.signIn().awaitCompletion().authenticated) {
        intentProvider
            .getIntent(this, screen)
            .silentCompletionOrNull()
            ?.resultOrNull()
            ?.let(PlayGamesScreenLaunch::Ready) ?: PlayGamesScreenLaunch.Unavailable
    } else {
        PlayGamesScreenLaunch.SignInFailed
    }
}

private val Task<AuthenticationResult>.authenticated: Boolean get() = resultOrNull()?.isAuthenticated == true

private suspend fun <T> Task<T>.silentCompletionOrNull(): Task<T>? = withTimeoutOrNull(SILENT_TASK_TIMEOUT) { awaitCompletion() }

private suspend fun <T> Task<T>.awaitCompletion(): Task<T> =
    suspendCancellableCoroutine { continuation -> addOnCompleteListener { task -> continuation.resume(task) } }

private fun <T> Task<T>.resultOrNull(): T? = takeIf { it.isSuccessful }?.result
