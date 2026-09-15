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

package de.lemke.sudoku.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatCheckBox
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.sudoku.data.UserSettings
import de.lemke.sudoku.databinding.DialogStatisticsFilterBinding
import de.lemke.sudoku.domain.model.SudokuFilterFlags.DIFFICULTY_ALL
import de.lemke.sudoku.domain.model.SudokuFilterFlags.DIFFICULTY_EASY
import de.lemke.sudoku.domain.model.SudokuFilterFlags.DIFFICULTY_EXPERT
import de.lemke.sudoku.domain.model.SudokuFilterFlags.DIFFICULTY_HARD
import de.lemke.sudoku.domain.model.SudokuFilterFlags.DIFFICULTY_MEDIUM
import de.lemke.sudoku.domain.model.SudokuFilterFlags.DIFFICULTY_VERY_EASY
import de.lemke.sudoku.domain.model.SudokuFilterFlags.SIZE_16X16
import de.lemke.sudoku.domain.model.SudokuFilterFlags.SIZE_4X4
import de.lemke.sudoku.domain.model.SudokuFilterFlags.SIZE_9X9
import de.lemke.sudoku.domain.model.SudokuFilterFlags.SIZE_ALL
import de.lemke.sudoku.domain.model.SudokuFilterFlags.TYPE_ALL
import de.lemke.sudoku.domain.model.SudokuFilterFlags.TYPE_DAILY
import de.lemke.sudoku.domain.model.SudokuFilterFlags.TYPE_LEVEL
import de.lemke.sudoku.domain.model.SudokuFilterFlags.TYPE_NORMAL
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun DialogStatisticsFilterBinding.allCheckboxes(): List<AppCompatCheckBox> =
    listOf(
        filterNormal,
        filterDaily,
        filterLevel,
        filterSize4,
        filterSize9,
        filterSize16,
        filterDifficultyVeryEasy,
        filterDifficultyEasy,
        filterDifficultyMedium,
        filterDifficultyHard,
        filterDifficultyExpert,
    )

private val ALL_FLAGS =
    TYPE_ALL or TYPE_NORMAL or TYPE_DAILY or TYPE_LEVEL or
        SIZE_ALL or SIZE_4X4 or SIZE_9X9 or SIZE_16X16 or
        DIFFICULTY_ALL or DIFFICULTY_VERY_EASY or DIFFICULTY_EASY or DIFFICULTY_MEDIUM or DIFFICULTY_HARD or DIFFICULTY_EXPERT

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityFilterDialogTest {
    private lateinit var settings: UserSettings
    private lateinit var dialogBinding: DialogStatisticsFilterBinding

    @Before
    fun setUp() {
        val context = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        settings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
        dialogBinding = DialogStatisticsFilterBinding.inflate(context.layoutInflater)
    }

    @Test
    fun `initFilterDialog checks only the checkboxes matching a specific bit when no ALL bit is set`() {
        settings.filterFlags = TYPE_NORMAL or SIZE_9X9 or DIFFICULTY_MEDIUM

        dialogBinding.initFilterDialog(settings)

        dialogBinding.filterNormal.isChecked shouldBe true
        dialogBinding.filterDaily.isChecked shouldBe false
        dialogBinding.filterLevel.isChecked shouldBe false
        dialogBinding.filterSize9.isChecked shouldBe true
        dialogBinding.filterSize4.isChecked shouldBe false
        dialogBinding.filterSize16.isChecked shouldBe false
        dialogBinding.filterDifficultyMedium.isChecked shouldBe true
        dialogBinding.filterDifficultyEasy.isChecked shouldBe false
    }

    @Test
    fun `initFilterDialog checks every checkbox in a group when only that group's ALL bit is set`() {
        settings.filterFlags = TYPE_ALL

        dialogBinding.initFilterDialog(settings)

        dialogBinding.filterNormal.isChecked shouldBe true
        dialogBinding.filterDaily.isChecked shouldBe true
        dialogBinding.filterLevel.isChecked shouldBe true
        dialogBinding.filterSize4.isChecked shouldBe false
        dialogBinding.filterDifficultyVeryEasy.isChecked shouldBe false
    }

    @Test
    fun `initFilterDialog checks every checkbox when every flag is set`() {
        settings.filterFlags = ALL_FLAGS

        dialogBinding.initFilterDialog(settings)

        dialogBinding.allCheckboxes().forEach { it.isChecked shouldBe true }
    }

    @Test
    fun `initFilterDialog unchecks every checkbox when no flag is set`() {
        settings.filterFlags = 0

        dialogBinding.initFilterDialog(settings)

        dialogBinding.allCheckboxes().forEach { it.isChecked shouldBe false }
    }

    @Test
    fun `updateFilterSettings combines the checked checkboxes without any group's ALL bit`() {
        dialogBinding.filterNormal.isChecked = true
        dialogBinding.filterDaily.isChecked = true
        dialogBinding.filterSize4.isChecked = true
        dialogBinding.filterDifficultyVeryEasy.isChecked = true

        updateFilterSettings(dialogBinding, settings)

        settings.filterFlags shouldBe (TYPE_NORMAL or TYPE_DAILY or SIZE_4X4 or DIFFICULTY_VERY_EASY)
    }

    @Test
    fun `updateFilterSettings ORs in every group's ALL bit when every checkbox is checked`() {
        dialogBinding.allCheckboxes().forEach { it.isChecked = true }

        updateFilterSettings(dialogBinding, settings)

        settings.filterFlags shouldBe ALL_FLAGS
    }

    @Test
    fun `combineFlags returns 0 when no entry is checked`() {
        combineFlags(false to TYPE_NORMAL, false to TYPE_DAILY, allFlag = TYPE_ALL) shouldBe 0
    }

    @Test
    fun `combineFlags ORs in just the one checked entry's flag`() {
        combineFlags(true to TYPE_NORMAL, false to TYPE_DAILY, false to TYPE_LEVEL, allFlag = TYPE_ALL) shouldBe TYPE_NORMAL
    }

    @Test
    fun `combineFlags does not OR in allFlag when all but one entry is checked`() {
        combineFlags(true to TYPE_NORMAL, true to TYPE_DAILY, false to TYPE_LEVEL, allFlag = TYPE_ALL) shouldBe
            (TYPE_NORMAL or TYPE_DAILY)
    }

    @Test
    fun `combineFlags also ORs in allFlag when every entry is checked`() {
        combineFlags(true to TYPE_NORMAL, true to TYPE_DAILY, true to TYPE_LEVEL, allFlag = TYPE_ALL) shouldBe
            (TYPE_NORMAL or TYPE_DAILY or TYPE_LEVEL or TYPE_ALL)
    }
}
