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
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

/** Hands out the queued [results] in order, then a succeeding fetch of [readyIntent]. */
internal class FakePlayGamesIntentProvider : PlayGamesIntentProvider {
    val readyIntent = Intent("de.lemke.sudoku.test.PLAY_GAMES_SCREEN")
    val results = ArrayDeque<Task<Intent>>()
    val requests = mutableListOf<PlayGamesScreen>()

    override fun getIntent(
        activity: Activity,
        screen: PlayGamesScreen,
    ): Task<Intent> {
        requests += screen
        return results.removeFirstOrNull() ?: Tasks.forResult(readyIntent)
    }
}
