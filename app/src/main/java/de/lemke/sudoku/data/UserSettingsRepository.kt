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
            it[KEY_DEV_MODE_ENABLED] = newSettings.devModeEnabled
            it[KEY_DIFFICULTY_SLIDER_VALUE] = newSettings.difficultySliderValue
            it[KEY_ERROR_LIMIT] = newSettings.errorLimit
            it[KEY_CONFIRM_EXIT] = newSettings.confirmExit
            it[KEY_REGIONAL_HIGHLIGHT] = newSettings.highlightRegional
            it[KEY_NUMBER_HIGHLIGHT] = newSettings.highlightNumber
        }
        return settingsFromPreferences(prefs)
    }


    private fun settingsFromPreferences(prefs: Preferences) = UserSettings(
        lastVersionCode = prefs[KEY_LAST_VERSION_CODE] ?: -1,
        lastVersionName = prefs[KEY_LAST_VERSION_NAME] ?: "0.0",
        devModeEnabled = prefs[KEY_DEV_MODE_ENABLED] ?: false,
        difficultySliderValue = prefs[KEY_DIFFICULTY_SLIDER_VALUE] ?: -1,
        errorLimit = prefs[KEY_ERROR_LIMIT] ?: 0,
        confirmExit = prefs[KEY_CONFIRM_EXIT] ?: true,
        highlightRegional = prefs[KEY_REGIONAL_HIGHLIGHT] ?: true,
        highlightNumber = prefs[KEY_NUMBER_HIGHLIGHT] ?: true,
    )


    private companion object {
        private val KEY_LAST_VERSION_CODE = intPreferencesKey("lastVersionCode")
        private val KEY_LAST_VERSION_NAME = stringPreferencesKey("lastVersionName")
        private val KEY_DEV_MODE_ENABLED = booleanPreferencesKey("devModeEnabled")
        private val KEY_DIFFICULTY_SLIDER_VALUE = intPreferencesKey("difficultySliderValue")
        private val KEY_ERROR_LIMIT = intPreferencesKey("errorLimit")
        private val KEY_CONFIRM_EXIT = booleanPreferencesKey("confirmExit")
        private val KEY_REGIONAL_HIGHLIGHT = booleanPreferencesKey("regionalHighlight")
        private val KEY_NUMBER_HIGHLIGHT = booleanPreferencesKey("numberHighlight")
    }
}

/** Settings associated with the current user. */
data class UserSettings(
    /** Last App-Version-Code */
    val lastVersionCode: Int,
    /** Last App-Version-Name */
    val lastVersionName: String,
    /** devMode enabled */
    val devModeEnabled: Boolean,
    /** value of difficulty slider*/
    val difficultySliderValue: Int,
    /** confirm Exit*/
    val confirmExit: Boolean,
    /** highlight sudoku neighbors*/
    val highlightRegional: Boolean,
    /** highlight selected Number*/
    val highlightNumber: Boolean,
    /** error limit*/
    val errorLimit: Int,
)
