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
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Tasks
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll

private val AUTHENTICATED: AuthenticationResult = listOf(AuthenticationResult.zza, AuthenticationResult.zzb).first { it.isAuthenticated }
private val NOT_AUTHENTICATED: AuthenticationResult =
    listOf(AuthenticationResult.zza, AuthenticationResult.zzb).first { !it.isAuthenticated }

class DefaultGamesSignInTest : ShouldSpec(
    {
        val activity = mockk<Activity>()
        val client = mockk<GamesSignInClient>()

        beforeEach {
            mockkStatic(PlayGames::class)
            every { PlayGames.getGamesSignInClient(activity) } returns client
        }

        afterEach { unmockkAll() }

        should("report the activity's client as authenticated when its check is") {
            every { client.isAuthenticated } returns Tasks.forResult(AUTHENTICATED)

            DefaultGamesSignIn().isAuthenticated(activity).result.shouldBeTrue()
        }

        should("report the activity's client as not authenticated when its check is not") {
            every { client.isAuthenticated } returns Tasks.forResult(NOT_AUTHENTICATED)

            DefaultGamesSignIn().isAuthenticated(activity).result.shouldBeFalse()
        }

        should("report the outcome of the activity's client sign-in") {
            every { client.signIn() } returns Tasks.forResult(AUTHENTICATED)

            DefaultGamesSignIn().signIn(activity).result.shouldBeTrue()
        }

        should("fail the sign-in task when the client's sign-in fails") {
            every { client.signIn() } returns Tasks.forException(IllegalStateException("no network"))

            DefaultGamesSignIn().signIn(activity).isSuccessful shouldBe false
        }
    },
)
