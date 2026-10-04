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

import androidx.annotation.StringRes
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.ExportProgress
import de.lemke.sudoku.domain.model.ImportProgress

/** What the progress dialog of a [DataTransfer.Running] shows. */
data class DataTransferProgressUi(
    @param:StringRes val title: Int,
    @param:StringRes val message: Int,
    val indicator: ProgressIndicator,
)

sealed interface ProgressIndicator {
    data object Indeterminate : ProgressIndicator

    data class Determinate(
        val done: Int,
        val total: Int,
    ) : ProgressIndicator
}

fun DataTransfer.Running.toProgressUi(): DataTransferProgressUi =
    when (this) {
        is DataTransfer.Exporting -> progress.toProgressUi()
        is DataTransfer.Importing -> progress.toProgressUi()
    }

private fun ExportProgress.toProgressUi(): DataTransferProgressUi =
    when (this) {
        ExportProgress.Reading -> {
            DataTransferProgressUi(R.string.export_data, R.string.export_data_ongoing, ProgressIndicator.Indeterminate)
        }

        is ExportProgress.Converting -> {
            DataTransferProgressUi(R.string.export_data, R.string.export_data_ongoing, ProgressIndicator.Determinate(done, total))
        }

        ExportProgress.Writing -> {
            DataTransferProgressUi(R.string.export_data, R.string.export_data_ongoing_writing_file, ProgressIndicator.Indeterminate)
        }
    }

private fun ImportProgress.toProgressUi(): DataTransferProgressUi =
    when (this) {
        ImportProgress.Reading -> {
            DataTransferProgressUi(R.string.import_data, R.string.import_data_ongoing, ProgressIndicator.Indeterminate)
        }

        is ImportProgress.Parsing -> {
            DataTransferProgressUi(R.string.import_data, R.string.import_data_ongoing, ProgressIndicator.Determinate(done, total))
        }

        is ImportProgress.Saving -> {
            DataTransferProgressUi(
                R.string.import_data,
                R.string.import_data_ongoing_processing,
                ProgressIndicator.Determinate(done, total),
            )
        }
    }
