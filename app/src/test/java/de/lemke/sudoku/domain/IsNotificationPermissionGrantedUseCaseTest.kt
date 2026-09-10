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

package de.lemke.sudoku.domain

import android.content.Context
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.mockk

/**
 * [IsNotificationPermissionGrantedUseCase] evaluates `SDK_INT < TIRAMISU || ContextCompat.checkSelfPermission(...)`.
 * On a plain JVM unit test (no Robolectric, per this task's pure-JVM Kotest style) `Build.VERSION.SDK_INT` resolves
 * to the android.jar stub's default of `0`, so the `SDK_INT < TIRAMISU` check is always true and the
 * `checkSelfPermission` branch is permanently unreachable here — same precedent as
 * [de.lemke.sudoku.ui.SettingsViewModelTest]'s doc comment for `systemNotificationsEnabled`. Exercising the
 * permission-check branch is out of scope for this pure-JVM test; see the task report.
 */
class IsNotificationPermissionGrantedUseCaseTest : ShouldSpec(
    {
        val context = mockk<Context>(relaxed = true)
        val useCase = IsNotificationPermissionGrantedUseCase(context)

        should("returns true because SDK_INT is always below TIRAMISU on the plain JVM") {
            useCase().shouldBeTrue()
        }
    },
)
