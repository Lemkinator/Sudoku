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

import android.app.Application
import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import de.lemke.commonutils.idleMainLooper
import de.lemke.sudoku.data.FakeSudokuShareFiles
import de.lemke.sudoku.data.database.FakeSudokusRepository
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.GenerateFieldsUseCase
import de.lemke.sudoku.domain.GenerateSudokuLevelUseCase
import de.lemke.sudoku.domain.GenerateSudokuUseCase
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import de.lemke.sudoku.domain.GetMaxSudokuLevelUseCase
import de.lemke.sudoku.domain.GetSudokuUseCase
import de.lemke.sudoku.domain.PatternSolvedBoardGenerator
import de.lemke.sudoku.domain.QueueSudokuSaveUseCase
import de.lemke.sudoku.domain.ShareSudokuUseCase
import de.lemke.sudoku.testSudoku
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import io.kotest.matchers.shouldBe
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SudokuViewModelSavedStateTest {
    private val completed = testSudoku(completed = true)
    private val repository = FakeSudokusRepository()

    private fun viewModel(savedStateHandle: SavedStateHandle): SudokuViewModel {
        val dispatcher = Dispatchers.Main
        val generateFields = GenerateFieldsUseCase(dispatcher, PatternSolvedBoardGenerator())
        return SudokuViewModel(
            savedStateHandle,
            GetSudokuUseCase(repository, dispatcher),
            GenerateSudokuUseCase(generateFields, dispatcher),
            GenerateSudokuLevelUseCase(generateFields, dispatcher),
            GetMaxSudokuLevelUseCase(repository, dispatcher),
            QueueSudokuSaveUseCase(repository, CoroutineScope(SupervisorJob()), dispatcher),
            ShareSudokuUseCase(FakeSudokuShareFiles(File("game.sudoku")), dispatcher),
            CalculatePlayGamesSyncUseCase(GetAllSudokusUseCase(repository, dispatcher), dispatcher),
            Clock.systemDefaultZone(),
        ).also { idleMainLooper() }
    }

    private fun Bundle.throughParcel(): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(this)
            parcel.setDataPosition(0)
            return requireNotNull(parcel.readBundle(SudokuViewModel::class.java.classLoader))
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `a pending summary survives a saved state written to a parcel and read back`() {
        repository.store(completed)
        val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
        val viewModel = viewModel(savedStateHandle)
        viewModel.onGameStarted(SudokuGame.Ready(completed))
        viewModel.onCompleted()
        idleMainLooper()
        viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)

        val savedState = savedStateHandle.savedStateProvider().saveState().throughParcel()
        val restored = viewModel(SavedStateHandle.createHandle(savedState, null))

        restored.game.value shouldBe SudokuGame.Ready(completed)
        restored.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
    }
}
