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

package de.lemke.sudoku

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.ShouldSpec

class ArchitectureTest : ShouldSpec() {
    private val codeScope = Konsist.scopeFromProduction()

    init {
        should("data layer does not depend on ui") {
            codeScope.files
                .withPackage("de.lemke.sudoku.data..")
                .assertFalse(testName = this.testCase.name.toString()) {
                    it.hasImport { import -> import.name.startsWith("de.lemke.sudoku.ui.") }
                }
        }
        // data may depend on domain.model (repositories/mappers construct domain models from DB/DataStore rows —
        // see DomainMapper.kt, SudokusRepository.kt) but not on domain use cases/business logic.
        should("data layer does not depend on domain use cases") {
            codeScope.files
                .withPackage("de.lemke.sudoku.data..")
                .assertFalse(testName = this.testCase.name.toString()) {
                    it.hasImport { import ->
                        import.name.startsWith("de.lemke.sudoku.domain.") && !import.name.startsWith("de.lemke.sudoku.domain.model.")
                    }
                }
        }
        // TODO(Plan 3 MVVM refactor): ObserveDailySudokusUseCase/ObserveSudokuHistoryUseCase/ObserveSudokuLevelUseCase
        // currently build ui.utils.SudokuListItem (presentation model) directly, and SendDailyNotificationUseCase
        // references ui.utils.AlarmReceiver to target its PendingIntent. Both are pre-existing debt from the
        // no-ViewModel architecture (Activities inject use cases directly) — move list-item mapping into the
        // ViewModel layer once introduced. Excluded here by name so the rule still catches new violations.
        should("domain layer does not depend on ui") {
            val knownExceptions =
                setOf(
                    "ObserveDailySudokusUseCase",
                    "ObserveSudokuHistoryUseCase",
                    "ObserveSudokuLevelUseCase",
                    "SendDailyNotificationUseCase",
                )
            codeScope.files
                .withPackage("de.lemke.sudoku.domain..")
                .filterNot { it.name in knownExceptions }
                .assertFalse(testName = this.testCase.name.toString()) {
                    it.hasImport { import -> import.name.startsWith("de.lemke.sudoku.ui.") }
                }
        }
        should("use case classes declare operator fun invoke") {
            codeScope
                .classes()
                .filter { it.name.endsWith("UseCase") }
                .assertTrue(testName = this.testCase.name.toString()) { koClass ->
                    koClass
                        .functions(includeNested = false, includeLocal = false)
                        .any { it.name == "invoke" && it.hasModifier(KoModifier.OPERATOR) }
                }
        }
        should("classes named ViewModel extend ViewModel") {
            codeScope
                .classes()
                .filter { it.name.endsWith("ViewModel") }
                .assertTrue(testName = this.testCase.name.toString()) {
                    it.hasParent { parent -> parent.name == "ViewModel" }
                }
        }
        should("HiltViewModel classes use Inject constructor") {
            codeScope
                .classes()
                .filter { it.hasAnnotation { ann -> ann.name == "HiltViewModel" } }
                .assertTrue(testName = this.testCase.name.toString()) {
                    it.primaryConstructor?.hasAnnotation { ann -> ann.name == "Inject" } == true
                }
        }
    }
}
