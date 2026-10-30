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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.idleMainLooper
import de.lemke.commonutils.receivePickerResult
import de.lemke.sudoku.R
import de.lemke.sudoku.TestDispatchers
import de.lemke.sudoku.data.database.SudokuDao
import de.lemke.sudoku.data.database.SudokuWithFields
import de.lemke.sudoku.data.database.sudokuWithFieldsToDb
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.testSudoku
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class SettingsBackupTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var sudokuDao: SudokuDao

    @Inject
    lateinit var testDispatchers: TestDispatchers

    private val backupFile = File.createTempFile("settings-backup", ".sudoku")

    @Before
    fun setup() {
        hiltRule.inject()
        testDispatchers.io.redirectTo(UnconfinedTestDispatcher())
        settings.bypassOobe()
    }

    @After
    fun tearDown() {
        testDispatchers.reset()
        backupFile.delete()
    }

    private fun launch(block: (SettingsActivity) -> Unit) {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity(block)
        }
    }

    private fun SettingsActivity.click(key: String) {
        val fragment = supportFragmentManager.findFragmentById(R.id.settings) as SettingsActivity.SettingsFragment
        val preference = checkNotNull(fragment.findPreference<Preference>(key))
        preference.onPreferenceClickListener?.onPreferenceClick(preference)
        idleMainLooper()
    }

    private fun SettingsActivity.exportTo(file: File): Intent {
        click("commonutilsExportBackup")
        val picker = receivePickerResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        idleMainLooper()
        latestDialog().click(AlertDialog.BUTTON_POSITIVE)
        return picker
    }

    private fun SettingsActivity.pickImportFile(file: File): Intent {
        click("commonutilsImportBackup")
        val picker = receivePickerResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        idleMainLooper()
        return picker
    }

    private fun latestDialog(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog

    private fun AlertDialog.message(): String = findViewById<TextView>(android.R.id.message)?.text.toString()

    private fun AlertDialog.click(button: Int) {
        getButton(button).performClick()
        idleMainLooper()
    }

    private fun storedGames(): List<SudokuWithFields> =
        runBlocking { sudokuDao.getAll() }.map { it.copy(fields = it.fields.sortedBy { field -> field.index }) }.sortedBy { it.sudoku.id }

    private fun store(vararg sudokus: Sudoku) =
        runBlocking {
            sudokus.forEach {
                val rows = sudokuWithFieldsToDb(it)
                sudokuDao.insert(rows.sudoku, rows.fields)
            }
        }

    private fun twoGames(): Array<Sudoku> {
        val created = LocalDateTime.of(2026, 3, 14, 9, 30)
        return arrayOf(
            testSudoku(created = created, updated = created.plusMinutes(12), completed = true),
            testSudoku(created = created.plusDays(1)),
        )
    }

    @Test
    fun `the export picker offers an octet-stream document named after the app and the sudoku extension`() =
        launch { activity ->
            store(*twoGames())
            activity.click("commonutilsExportBackup")

            val picker = activity.receivePickerResult(Activity.RESULT_CANCELED, null)

            picker.action shouldBe Intent.ACTION_CREATE_DOCUMENT
            picker.type shouldBe "application/octet-stream"
            picker.getStringExtra(Intent.EXTRA_TITLE)!! shouldMatch Regex(".+_backup_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}\\.sudoku")
        }

    @Test
    fun `an export reports that the backup was saved`() =
        launch { activity ->
            store(*twoGames())
            activity.click("commonutilsExportBackup")
            activity.receivePickerResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(backupFile)))
            idleMainLooper()

            latestDialog().message() shouldBe "Backup exported"
            backupFile.length() shouldBeGreaterThan 0L
        }

    @Test
    fun `an export then a merge import restores the sudokus`() =
        launch { activity ->
            store(*twoGames())
            val before = storedGames()
            activity.exportTo(backupFile)
            runBlocking { sudokuDao.deleteAll() }
            storedGames().shouldBeEmpty()

            activity.pickImportFile(backupFile)
            val preview = latestDialog()
            preview.message() shouldStartWith "Backup from "
            preview.message() shouldContain "Sudokus: 2 entries"
            preview.click(AlertDialog.BUTTON_POSITIVE)

            storedGames() shouldBe before
        }

    @Test
    fun `a replace import asks for confirmation and keeps the data until then`() =
        launch { activity ->
            store(*twoGames())
            activity.exportTo(backupFile)
            val localOnly = testSudoku(created = LocalDateTime.of(2026, 4, 1, 8, 0))
            store(localOnly)
            val before = storedGames()

            activity.pickImportFile(backupFile)
            latestDialog().click(AlertDialog.BUTTON_NEUTRAL)

            latestDialog().message() shouldBe
                "Attention: This deletes the current data of the parts in this backup and replaces it with the backup. This cannot be undone."
            storedGames() shouldBe before
        }

    @Test
    fun `a confirmed replace import removes the sudokus the file lacks`() =
        launch { activity ->
            store(*twoGames())
            val inFile = storedGames()
            activity.exportTo(backupFile)
            store(testSudoku(created = LocalDateTime.of(2026, 4, 1, 8, 0)))

            activity.pickImportFile(backupFile)
            latestDialog().click(AlertDialog.BUTTON_NEUTRAL)
            latestDialog().click(AlertDialog.BUTTON_POSITIVE)

            storedGames() shouldBe inFile
        }
}
