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
import android.os.Looper
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import de.lemke.sudoku.data.database.SudokusRepository
import de.lemke.sudoku.domain.CalculatePlayGamesSyncUseCase
import de.lemke.sudoku.domain.GetSudokuUseCase
import de.lemke.sudoku.domain.QueueSudokuSaveUseCase
import de.lemke.sudoku.domain.model.Difficulty
import de.lemke.sudoku.domain.model.Field
import de.lemke.sudoku.domain.model.PlayGamesSync
import de.lemke.sudoku.domain.model.Position
import de.lemke.sudoku.domain.model.Sudoku
import de.lemke.sudoku.domain.model.SudokuSize
import de.lemke.sudoku.ui.SudokuActivity.Companion.KEY_SUDOKU_ID
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SudokuViewModelSavedStateTest {
    private val completed =
        Sudoku.create(
            size = SudokuSize.FOUR,
            difficulty = Difficulty.EASY,
            modeLevel = Sudoku.MODE_NORMAL,
            fields = MutableList(SudokuSize.FOUR.cellCount) { Field(Position.create(it, SudokuSize.FOUR), solution = 1, value = 1) },
        )
    private val getSudoku = mockk<GetSudokuUseCase>()
    private val calculatePlayGamesSync = mockk<CalculatePlayGamesSyncUseCase>()

    private fun viewModel(savedStateHandle: SavedStateHandle): SudokuViewModel =
        SudokuViewModel(
            savedStateHandle,
            getSudoku,
            mockk(),
            mockk(),
            mockk(),
            QueueSudokuSaveUseCase(mockk<SudokusRepository>(relaxUnitFun = true), CoroutineScope(SupervisorJob()), Dispatchers.Main),
            mockk(),
            calculatePlayGamesSync,
        )

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
        coEvery { getSudoku(completed.id) } returns completed
        coEvery { calculatePlayGamesSync(completed) } returns PlayGamesSync()
        val savedStateHandle = SavedStateHandle(mapOf(KEY_SUDOKU_ID to completed.id.value))
        val viewModel = viewModel(savedStateHandle)
        viewModel.onGameStarted(SudokuGame.Ready(completed))
        viewModel.onCompleted()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)

        val savedState = savedStateHandle.savedStateProvider().saveState().throughParcel()
        val restored = viewModel(SavedStateHandle.createHandle(savedState, null))

        restored.game.value shouldBe SudokuGame.Ready(completed)
        restored.completion.value shouldBe SudokuCompletion.Summary(completed, FollowUp.NEW_GAME)
    }
}
