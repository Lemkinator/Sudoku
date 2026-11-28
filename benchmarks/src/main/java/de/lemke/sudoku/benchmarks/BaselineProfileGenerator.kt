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

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    // Startup profile drives dex layout optimization, so it must stay limited to the actual
    // cold-start path — a secondary screen here would bloat startup-prof.txt with non-startup code.
    @Test
    fun startup() =
        rule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndSkipOnboarding()
        }

    // Split from levelsJourney so a flaky game-generation wait doesn't risk losing the other
    // journey's contribution, and each gets its own stableIterations/maxIterations budget.
    @Test
    fun newGameJourney() =
        rule.collect(
            packageName = PACKAGE_NAME,
            stableIterations = 3,
            maxIterations = 10,
        ) {
            pressHome()
            startActivityAndSkipOnboarding()
            device.selectBoardSize(SizeSliderStop.DEFAULT)
            openNewGame()
            device.awaitDefaultBoardSize()
            playBoard()
            playBoardInLandscape()
            device.pressBack()
            device.waitForIdle()
        }

    @Test
    fun largeBoardJourney() =
        rule.collect(
            packageName = PACKAGE_NAME,
            stableIterations = 3,
            maxIterations = 10,
        ) {
            pressHome()
            startActivityAndSkipOnboarding()
            device.selectBoardSize(SizeSliderStop.LARGEST)
            openNewGame(LARGE_GENERATION_TIMEOUT_MS)
            device.waitAndFindObject(By.res(PACKAGE_NAME, LARGEST_DIGIT_BUTTON), TIMEOUT_MS)
            playBoard()
            device.pressBack()
            device.waitForIdle()
        }

    @Test
    fun levelsJourney() =
        rule.collect(
            packageName = PACKAGE_NAME,
            stableIterations = 3,
            maxIterations = 10,
        ) {
            pressHome()
            startActivityAndSkipOnboarding()
            navigateToLevelsAndBack()
        }
}

private fun MacrobenchmarkScope.navigateToLevelsAndBack() {
    device.clickAndWaitForNewWindow(By.res(PACKAGE_NAME, "levelsButton"), TIMEOUT_MS)
    device.waitAndFindObject(By.res(PACKAGE_NAME, "viewPagerLevel"), TIMEOUT_MS)
    device.pressBack()
    device.waitForIdle()
}

private const val LARGEST_DIGIT_BUTTON = "number_button_g"
private const val DEFAULT_LARGEST_DIGIT_BUTTON = "number_button_9"

// Upper bound of a conditional wait: a 16x16 generation can take 30 to 60 s on a device.
private const val LARGE_GENERATION_TIMEOUT_MS = 120_000L

// The cell texts come from the app's own string resources, so the matching follows the device language.
private class CellTexts(
    private val description: String,
    private val empty: String,
    private val wrong: String,
    private val notes: String,
) {
    fun empty(): String = Pattern.quote(empty)

    fun value(value: String) = "(?:${Pattern.quote(value)}|${format(wrong, Pattern.quote(value))})"

    fun notes(
        content: String,
        notes: String,
    ) = format(this.notes, content, Pattern.quote(notes))

    fun description(
        row: String,
        column: String,
        content: String,
    ) = format(description, row, column, content)

    companion object {
        fun load(): CellTexts {
            val resources =
                InstrumentationRegistry
                    .getInstrumentation()
                    .context
                    .createPackageContext(PACKAGE_NAME, 0)
                    .resources

            fun string(name: String) = resources.getString(resources.getIdentifier(name, "string", PACKAGE_NAME))
            return CellTexts(string("cell_description"), string("cell_empty"), string("cell_wrong"), string("cell_notes"))
        }
    }
}

private val FORMAT_ARGUMENT = Regex("""%(\d+)\$[ds]""")

// Builds a regex from a string resource: the literal parts are quoted, each argument is already a regex.
private fun format(
    template: String,
    vararg arguments: String,
): String {
    var end = 0
    return buildString {
        FORMAT_ARGUMENT.findAll(template).forEach { match ->
            append(Pattern.quote(template.substring(end, match.range.first)))
            append(arguments[match.groupValues[1].toInt() - 1])
            end = match.range.last + 1
        }
        append(Pattern.quote(template.substring(end)))
    }
}

private val cellTexts by lazy { CellTexts.load() }

private data class Cell(
    val row: String,
    val column: String,
)

private enum class SizeSliderStop(val index: Int) {
    SMALLEST(0),
    DEFAULT(1),
    LARGEST(2),
}

private fun UiDevice.selectBoardSize(stop: SizeSliderStop) {
    val bounds = waitAndFindObject(By.res(PACKAGE_NAME, "size_seekbar"), TIMEOUT_MS).visibleBounds
    click(bounds.left + (bounds.width() - 1) * stop.index / SizeSliderStop.LARGEST.index, bounds.centerY())
    waitForIdle()
}

private fun UiDevice.awaitDefaultBoardSize() {
    waitAndFindObject(By.res(PACKAGE_NAME, DEFAULT_LARGEST_DIGIT_BUTTON), TIMEOUT_MS)
    check(wait(Until.gone(By.res(PACKAGE_NAME, LARGEST_DIGIT_BUTTON)), TIMEOUT_MS)) { "Board is larger than the default size" }
}

// Covers game generation and board rendering, which levelsButton alone never touches.
private fun MacrobenchmarkScope.openNewGame(timeout: Long = GENERATION_TIMEOUT_MS) {
    device.clickNewGame(timeout)
    device.waitAndFindObject(By.res(PACKAGE_NAME, "board"), timeout)
}

private fun MacrobenchmarkScope.playBoardInLandscape() {
    try {
        device.setOrientationLeft()
        device.waitAndFindObject(By.res(PACKAGE_NAME, "board"), TIMEOUT_MS)
        playBoard()
    } finally {
        device.setOrientationNatural()
        device.unfreezeRotation()
    }
    device.waitAndFindObject(By.res(PACKAGE_NAME, "board"), TIMEOUT_MS)
}

// Enters a digit, erases it, then enters a note and clears it, on a cell that is empty at the start.
private fun MacrobenchmarkScope.playBoard() {
    val emptyCell = Pattern.compile(cellTexts.description("([0-9]+)", "([0-9]+)", cellTexts.empty()))
    val description = device.waitAndFindObject(By.desc(emptyCell), TIMEOUT_MS).contentDescription
    val match = emptyCell.matcher(description)
    check(match.matches()) { "Unexpected cell description '$description'" }
    val cell = Cell(match.group(1), match.group(2))
    val digit = device.firstEnabledDigit()
    device.tapCell(cell)
    device.tapButton("number_button_$digit")
    device.awaitCell(cell, cellTexts.value(digit))
    device.tapCell(cell)
    device.tapButton("delete_button")
    device.awaitCell(cell, cellTexts.empty())
    device.tapButton("note_button")
    device.tapCell(cell)
    device.tapButton("number_button_$digit")
    device.awaitCell(cell, cellTexts.notes(cellTexts.empty(), digit))
    device.tapCell(cell)
    device.tapButton("delete_button")
    device.awaitCell(cell, cellTexts.empty())
    device.tapButton("note_button")
}

// A digit whose cells are all placed correctly has a disabled button, and givens count as placed.
private fun UiDevice.firstEnabledDigit(): String =
    ('1'..'9').first { findObject(By.res(PACKAGE_NAME, "number_button_$it"))?.isEnabled == true }.toString()

private fun cellSelector(
    cell: Cell,
    content: String,
) = By.desc(Pattern.compile(cellTexts.description(Pattern.quote(cell.row), Pattern.quote(cell.column), content)))

private fun UiDevice.tapCell(cell: Cell) {
    waitAndFindObject(cellSelector(cell, ".*"), TIMEOUT_MS).click()
}

private fun UiDevice.tapButton(id: String) {
    waitAndFindObject(By.res(PACKAGE_NAME, id), TIMEOUT_MS).click()
}

private fun UiDevice.awaitCell(
    cell: Cell,
    content: String,
) {
    check(wait(Until.hasObject(cellSelector(cell, content)), TIMEOUT_MS)) { "Cell did not match '$content' (cell=$cell)" }
}
