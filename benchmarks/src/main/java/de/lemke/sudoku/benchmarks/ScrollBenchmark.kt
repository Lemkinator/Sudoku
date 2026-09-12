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
package de.lemke.sudoku.benchmarks

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Level list stays at exactly 1 row on a fresh install (levels only accumulate by finishing
// puzzles, which this black-box UI test can't do). History instead grows just by starting games
// — TabSudokuViewModel.createNewSudoku saves immediately, before SudokuActivity even opens — so
// seed that list directly instead.
//
// All seeded rows land under one date separator, and each two-line row is
// ?android:listPreferredItemHeight (~72dp) tall — a tall device like pixel9Api35 fits ~10-12 rows
// without scrolling at all, so the count needs real margin above that, not just >1.
private const val HISTORY_SEED_COUNT = 20

@RunWith(AndroidJUnit4::class)
@LargeTest
class ScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollBaselineProfile() = scroll(CompilationMode.Partial())

    private fun scroll(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 10,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
                startActivityAndSkipOnboarding()
                repeat(HISTORY_SEED_COUNT) {
                    device.waitAndFindObject(By.res(PACKAGE_NAME, "newGameButton"), TIMEOUT_MS).click()
                    device.waitAndFindObject(By.res(PACKAGE_NAME, "game_recycler"), GENERATION_TIMEOUT_MS)
                    device.pressBack()
                    device.waitForIdle()
                }
                device.waitAndFindObject(By.res(PACKAGE_NAME, "history_dest"), TIMEOUT_MS).click()
                device.waitAndFindObject(
                    By.res(PACKAGE_NAME, "sudokuHistoryList").hasDescendant(By.res(PACKAGE_NAME, "item_text")),
                    TIMEOUT_MS,
                )
            },
        ) {
            val recycler =
                checkNotNull(device.findObject(By.res(PACKAGE_NAME, "sudokuHistoryList"))) { "sudokuHistoryList not found" }
            device.flingElementDownUp(recycler)
        }
}
