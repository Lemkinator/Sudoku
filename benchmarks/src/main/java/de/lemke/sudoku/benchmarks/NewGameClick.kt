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

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice

// TabSudoku.refresh() resizes the weighted button row asynchronously, and shows dailyAvailableButton last;
// a tap before that lands in the gap the shrinking newGameButton leaves.
fun UiDevice.clickNewGame(timeout: Long) {
    waitAndFindObject(By.res(PACKAGE_NAME, "dailyAvailableButton"), TIMEOUT_MS)
    clickAndWaitForNewWindow(By.res(PACKAGE_NAME, "newGameButton"), timeout)
}
