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
import com.google.android.gms.games.AuthenticationResult
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Task
import java.util.concurrent.Executor
import javax.inject.Inject

/** Checks and starts the Play Games sign-in; each task results in whether the player is signed in. */
interface GamesSignIn {
    fun isAuthenticated(activity: Activity): Task<Boolean>

    fun signIn(activity: Activity): Task<Boolean>
}

class DefaultGamesSignIn @Inject constructor() : GamesSignIn {
    override fun isAuthenticated(activity: Activity): Task<Boolean> =
        PlayGames.getGamesSignInClient(activity).isAuthenticated.authenticated()

    override fun signIn(activity: Activity): Task<Boolean> = PlayGames.getGamesSignInClient(activity).signIn().authenticated()

    private fun Task<AuthenticationResult>.authenticated(): Task<Boolean> = continueWith(directExecutor) { it.result.isAuthenticated }

    private companion object {
        val directExecutor = Executor { it.run() }
    }
}
