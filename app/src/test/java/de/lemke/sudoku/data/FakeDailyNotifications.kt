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

package de.lemke.sudoku.data

/** Keeps the scheduled alarm time and counts posted notifications; [permissionGranted] and [blocked] set the system state. */
class FakeDailyNotifications(
    var permissionGranted: Boolean = true,
    var blocked: Boolean = false,
) : DailyNotifications {
    data class AlarmTime(
        val hour: Int,
        val minute: Int,
    )

    var scheduled: AlarmTime? = null
        private set
    var postedCount = 0
        private set

    override fun isPermissionGranted(): Boolean = permissionGranted

    override fun isBlocked(): Boolean = blocked

    override fun schedule(
        hour: Int,
        minute: Int,
    ) {
        scheduled = AlarmTime(hour, minute)
    }

    override fun cancel() {
        scheduled = null
    }

    override fun post() {
        postedCount++
    }
}
