package de.lemke.sudoku.domain

import de.lemke.sudoku.data.UserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ObserveUserSettingsUseCase @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
) {
    operator fun invoke() = userSettingsRepository.observeSettings().flowOn(Dispatchers.Default)
}