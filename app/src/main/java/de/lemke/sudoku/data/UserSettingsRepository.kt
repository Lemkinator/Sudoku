package de.lemke.sudoku.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Provides CRUD operations for user settings. */
class UserSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** Returns the current user settings. */
    suspend fun getSettings(): UserSettings = dataStore.data.map(::settingsFromPreferences).first()

    /**
     * Updates the current user settings and returns the new settings.
     * @param f Invoked with the current settings; The settings returned from this function will replace the current ones.
     */
    suspend fun updateSettings(f: (UserSettings) -> UserSettings): UserSettings {
        val prefs = dataStore.edit {
            val newSettings = f(settingsFromPreferences(it))
            it[KEY_LAST_VERSION_CODE] = newSettings.lastVersionCode
            it[KEY_LAST_VERSION_NAME] = newSettings.lastVersionName
            it[KEY_TOS_ACCEPTED] = newSettings.tosAccepted
            it[KEY_DEV_MODE_ENABLED] = newSettings.devModeEnabled
            it[KEY_DIFFICULTY_SLIDER_VALUE] = newSettings.difficultySliderValue
            it[KEY_CONFIRM_EXIT] = newSettings.confirmExit
            it[KEY_ANIMATIONS_ENABLED] = newSettings.animationsEnabled
            it[KEY_REGIONAL_HIGHLIGHT] = newSettings.highlightRegional
            it[KEY_NUMBER_HIGHLIGHT] = newSettings.highlightNumber
            it[KEY_ERROR_LIMIT] = newSettings.errorLimit
            it[KEY_STATISTICS_FILTER_DIFFICULTY] = newSettings.statisticsFilterDifficulty
            it[KEY_STATISTICS_FILTER_INCLUDE_NORMAL] = newSettings.statisticsFilterIncludeNormal
            it[KEY_STATISTICS_FILTER_INCLUDE_DAILY] = newSettings.statisticsFilterIncludeDaily
            it[KEY_STATISTICS_FILTER_INCLUDE_LEVELS] = newSettings.statisticsFilterIncludeLevels
            it[KEY_DAILY_SUDOKU_NOTIFICATION_ENABLED] = newSettings.dailySudokuNotificationEnabled
        }
        return settingsFromPreferences(prefs)
    }


    private fun settingsFromPreferences(prefs: Preferences) = UserSettings(
        lastVersionCode = prefs[KEY_LAST_VERSION_CODE] ?: -1,
        lastVersionName = prefs[KEY_LAST_VERSION_NAME] ?: "0.0",
        tosAccepted = prefs[KEY_TOS_ACCEPTED] ?: false,
        devModeEnabled = prefs[KEY_DEV_MODE_ENABLED] ?: false,
        difficultySliderValue = prefs[KEY_DIFFICULTY_SLIDER_VALUE] ?: 2,
        confirmExit = prefs[KEY_CONFIRM_EXIT] ?: true,
        highlightRegional = prefs[KEY_REGIONAL_HIGHLIGHT] ?: true,
        highlightNumber = prefs[KEY_NUMBER_HIGHLIGHT] ?: true,
        animationsEnabled = prefs[KEY_ANIMATIONS_ENABLED] ?: true,
        errorLimit = prefs[KEY_ERROR_LIMIT] ?: 3,
        statisticsFilterDifficulty = prefs[KEY_STATISTICS_FILTER_DIFFICULTY] ?: -1,
        statisticsFilterIncludeNormal = prefs[KEY_STATISTICS_FILTER_INCLUDE_NORMAL] ?: true,
        statisticsFilterIncludeDaily = prefs[KEY_STATISTICS_FILTER_INCLUDE_DAILY] ?: false,
        statisticsFilterIncludeLevels = prefs[KEY_STATISTICS_FILTER_INCLUDE_LEVELS] ?: false,
        dailySudokuNotificationEnabled = prefs[KEY_DAILY_SUDOKU_NOTIFICATION_ENABLED] ?: false,
    )


    private companion object {
        private val KEY_LAST_VERSION_CODE = intPreferencesKey("lastVersionCode")
        private val KEY_LAST_VERSION_NAME = stringPreferencesKey("lastVersionName")
        private val KEY_TOS_ACCEPTED = booleanPreferencesKey("tosAccepted")
        private val KEY_DEV_MODE_ENABLED = booleanPreferencesKey("devModeEnabled")
        private val KEY_DIFFICULTY_SLIDER_VALUE = intPreferencesKey("difficultySliderValue")
        private val KEY_CONFIRM_EXIT = booleanPreferencesKey("confirmExit")
        private val KEY_ANIMATIONS_ENABLED = booleanPreferencesKey("animationsEnabled")
        private val KEY_REGIONAL_HIGHLIGHT = booleanPreferencesKey("regionalHighlight")
        private val KEY_NUMBER_HIGHLIGHT = booleanPreferencesKey("numberHighlight")
        private val KEY_ERROR_LIMIT = intPreferencesKey("errorLimit")
        private val KEY_STATISTICS_FILTER_DIFFICULTY = intPreferencesKey("statisticsFilterDifficulty")
        private val KEY_STATISTICS_FILTER_INCLUDE_NORMAL = booleanPreferencesKey("statisticsFilterIncludeNormal")
        private val KEY_STATISTICS_FILTER_INCLUDE_DAILY = booleanPreferencesKey("statisticsFilterIncludeDaily")
        private val KEY_STATISTICS_FILTER_INCLUDE_LEVELS = booleanPreferencesKey("statisticsFilterIncludeLevels")
        private val KEY_DAILY_SUDOKU_NOTIFICATION_ENABLED = booleanPreferencesKey("dailySudokuNotificationEnabled")
    }
}

/** Settings associated with the current user. */
data class UserSettings(
    /** Last App-Version-Code */
    val lastVersionCode: Int,
    /** Last App-Version-Name */
    val lastVersionName: String,
    /** terms of service accepted by user */
    val tosAccepted: Boolean,
    /** devMode enabled */
    val devModeEnabled: Boolean,
    /** value of difficulty slider*/
    val difficultySliderValue: Int,
    /** confirm Exit*/
    val confirmExit: Boolean,
    /** animations enabled */
    val animationsEnabled: Boolean,
    /** highlight sudoku neighbors*/
    val highlightRegional: Boolean,
    /** highlight selected Number*/
    val highlightNumber: Boolean,
    /** error limit*/
    val errorLimit: Int,
    /** Statistics filter Difficulty*/
    val statisticsFilterDifficulty: Int,
    /** Statistics filter include normal */
    val statisticsFilterIncludeNormal: Boolean,
    /** Statistics filter include daily */
    val statisticsFilterIncludeDaily: Boolean,
    /** Statistics filter include levels*/
    val statisticsFilterIncludeLevels: Boolean,
    /** daily Sudoku Notification enabled */
    val dailySudokuNotificationEnabled: Boolean,

    )
