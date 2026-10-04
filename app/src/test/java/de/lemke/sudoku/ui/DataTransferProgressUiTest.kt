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

import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.ExportProgress
import de.lemke.sudoku.domain.model.ImportProgress
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class DataTransferProgressUiTest : ShouldSpec(
    {
        context("an export") {
            should("show the indeterminate export message while reading") {
                DataTransfer.Exporting(ExportProgress.Reading).toProgressUi() shouldBe
                    DataTransferProgressUi(R.string.export_data, R.string.export_data_ongoing, ProgressIndicator.Indeterminate)
            }

            should("show the converted count of the total while converting") {
                DataTransfer.Exporting(ExportProgress.Converting(done = 3, total = 7)).toProgressUi() shouldBe
                    DataTransferProgressUi(
                        R.string.export_data,
                        R.string.export_data_ongoing,
                        ProgressIndicator.Determinate(done = 3, total = 7),
                    )
            }

            should("show the indeterminate writing message while writing the file") {
                DataTransfer.Exporting(ExportProgress.Writing).toProgressUi() shouldBe
                    DataTransferProgressUi(R.string.export_data, R.string.export_data_ongoing_writing_file, ProgressIndicator.Indeterminate)
            }
        }

        context("an import") {
            should("show the indeterminate import message while reading") {
                DataTransfer.Importing(ImportProgress.Reading).toProgressUi() shouldBe
                    DataTransferProgressUi(R.string.import_data, R.string.import_data_ongoing, ProgressIndicator.Indeterminate)
            }

            should("show the parsed count of the total while parsing") {
                DataTransfer.Importing(ImportProgress.Parsing(done = 2, total = 5)).toProgressUi() shouldBe
                    DataTransferProgressUi(
                        R.string.import_data,
                        R.string.import_data_ongoing,
                        ProgressIndicator.Determinate(done = 2, total = 5),
                    )
            }

            should("show the saved count of the total with the processing message while saving") {
                DataTransfer.Importing(ImportProgress.Saving(done = 4, total = 9)).toProgressUi() shouldBe
                    DataTransferProgressUi(
                        R.string.import_data,
                        R.string.import_data_ongoing_processing,
                        ProgressIndicator.Determinate(done = 4, total = 9),
                    )
            }
        }
    },
)
