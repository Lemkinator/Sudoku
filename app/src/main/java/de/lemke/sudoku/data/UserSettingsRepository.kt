package de.lemke.sudoku.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import de.lemke.sudoku.domain.GetAllSudokusUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Provides CRUD operations for user settings. */
class UserSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** Returns the current user settings. */
    suspend fun getSettings(): UserSettings = dataStore.data.map(::settingsFromPreferences).first()

    /** Observe user settings. */
    fun observeSettings(): Flow<UserSettings> = dataStore.data.map(::settingsFromPreferences)

    /** Observe dailyShowUncompleted. */
    fun observeDailyShowUncompleted(): Flow<Boolean> = dataStore.data.map { it[KEY_DAILY_SHOW_UNCOMPLETED] == true }.distinctUntilChanged()

    /** Observe statisticsFilterFlags. */
    fun observeStatisticsFilterFlags(): Flow<Int> = dataStore.data.map {
        it[KEY_STATISTICS_FILTER_FLAGS]
            ?: (GetAllSudokusUseCase.TYPE_ALL or GetAllSudokusUseCase.SIZE_ALL or GetAllSudokusUseCase.DIFFICULTY_ALL)
    }.distinctUntilChanged()

    /**
     * Updates the current user settings and returns the new settings.
     * @param f Invoked with the current settings; The settings returned from this function will replace the current ones.
     */
    suspend fun updateSettings(f: (UserSettings) -> UserSettings): UserSettings {
        val prefs = dataStore.edit {
            val newSettings = f(settingsFromPreferences(it))
            it[KEY_DIFFICULTY_SLIDER_VALUE] = newSettings.difficultySliderValue
            it[KEY_SIZE_SLIDER_VALUE] = newSettings.sizeSliderValue
            it[KEY_KEEP_SCREEN_ON] = newSettings.keepScreenOn
            it[KEY_ANIMATIONS_ENABLED] = newSettings.animationsEnabled
            it[KEY_REGIONAL_HIGHLIGHT] = newSettings.highlightRegional
            it[KEY_NUMBER_HIGHLIGHT] = newSettings.highlightNumber
            it[KEY_ERROR_LIMIT] = newSettings.errorLimit
            it[KEY_STATISTICS_FILTER_FLAGS] = newSettings.filterFlags
            it[KEY_DAILY_SHOW_UNCOMPLETED] = newSettings.dailyShowUncompleted
            it[KEY_DAILY_SUDOKU_NOTIFICATION_ENABLED] = newSettings.dailySudokuNotificationEnabled
            it[KEY_DAILY_SUDOKU_NOTIFICATION_HOUR] = newSettings.dailySudokuNotificationHour
            it[KEY_DAILY_SUDOKU_NOTIFICATION_MINUTE] = newSettings.dailySudokuNotificationMinute
            it[KEY_CURRENT_LEVEL_TAB] = newSettings.currentLevelTab
        }
        return settingsFromPreferences(prefs)
    }


    private fun settingsFromPreferences(prefs: Preferences) = UserSettings(
        difficultySliderValue = prefs[KEY_DIFFICULTY_SLIDER_VALUE] ?: 2,
        sizeSliderValue = prefs[KEY_SIZE_SLIDER_VALUE] ?: 1,
        keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] != false,
        animationsEnabled = prefs[KEY_ANIMATIONS_ENABLED] != false,
        highlightRegional = prefs[KEY_REGIONAL_HIGHLIGHT] != false,
        highlightNumber = prefs[KEY_NUMBER_HIGHLIGHT] != false,
        errorLimit = prefs[KEY_ERROR_LIMIT] ?: 3,
        filterFlags = prefs[KEY_STATISTICS_FILTER_FLAGS]
            ?: (GetAllSudokusUseCase.TYPE_ALL or GetAllSudokusUseCase.SIZE_ALL or GetAllSudokusUseCase.DIFFICULTY_ALL),
        dailyShowUncompleted = prefs[KEY_DAILY_SHOW_UNCOMPLETED] != false,
        dailySudokuNotificationEnabled = prefs[KEY_DAILY_SUDOKU_NOTIFICATION_ENABLED] != false,
        dailySudokuNotificationHour = prefs[KEY_DAILY_SUDOKU_NOTIFICATION_HOUR] ?: 9,
        dailySudokuNotificationMinute = prefs[KEY_DAILY_SUDOKU_NOTIFICATION_MINUTE] ?: 0,
        currentLevelTab = prefs[KEY_CURRENT_LEVEL_TAB] ?: 1,
    )


    private companion object {
        private val KEY_DIFFICULTY_SLIDER_VALUE = intPreferencesKey("difficultySliderValue")
        private val KEY_SIZE_SLIDER_VALUE = intPreferencesKey("sizeSliderValue")
        private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keepScreenOn")
        private val KEY_ANIMATIONS_ENABLED = booleanPreferencesKey("animationsEnabled")
        private val KEY_REGIONAL_HIGHLIGHT = booleanPreferencesKey("regionalHighlight")
        private val KEY_NUMBER_HIGHLIGHT = booleanPreferencesKey("numberHighlight")
        private val KEY_ERROR_LIMIT = intPreferencesKey("errorLimit")
        private val KEY_STATISTICS_FILTER_FLAGS = intPreferencesKey("statisticsFilterFlags")
        private val KEY_DAILY_SHOW_UNCOMPLETED = booleanPreferencesKey("dailyShowUncompleted")
        private val KEY_DAILY_SUDOKU_NOTIFICATION_ENABLED = booleanPreferencesKey("dailySudokuNotificationEnabled")
        private val KEY_DAILY_SUDOKU_NOTIFICATION_HOUR = intPreferencesKey("dailySudokuNotificationHour")
        private val KEY_DAILY_SUDOKU_NOTIFICATION_MINUTE = intPreferencesKey("dailySudokuNotificationMinute")
        private val KEY_CURRENT_LEVEL_TAB = intPreferencesKey("currentLevelTab")
    }
}

/** Settings associated with the current user. */
data class UserSettings(
    /** value of difficulty slider*/
    val difficultySliderValue: Int,
    /** value of size slider*/
    val sizeSliderValue: Int,
    /** keep screen on */
    val keepScreenOn: Boolean,
    /** animations enabled */
    val animationsEnabled: Boolean,
    /** highlight sudoku neighbors*/
    val highlightRegional: Boolean,
    /** highlight selected Number*/
    val highlightNumber: Boolean,
    /** error limit*/
    val errorLimit: Int,
    /** Statistics filter Flags*/
    val filterFlags: Int,
    /** show uncompleted daily sudokus */
    val dailyShowUncompleted: Boolean,
    /** daily Sudoku Notification enabled */
    val dailySudokuNotificationEnabled: Boolean,
    /** daily Sudoku Notification hour */
    val dailySudokuNotificationHour: Int,
    /** daily Sudoku Notification minute */
    val dailySudokuNotificationMinute: Int,
    /** the number of the currently selected Tab in SudokuLevelActivity */
    val currentLevelTab: Int,
)
