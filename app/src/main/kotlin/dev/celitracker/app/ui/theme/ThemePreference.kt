package dev.celitracker.app.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private const val PREFERENCES_FILE = "affichage"
private const val THEME_KEY = "theme"

/** Already persisted on users' devices: never rename these values. */
internal fun ThemeMode.storedValue(): String = when (this) {
    ThemeMode.SYSTEM -> "SYSTEME"
    ThemeMode.LIGHT -> "CLAIR"
    ThemeMode.DARK -> "SOMBRE"
}

internal fun storedThemeMode(value: String?): ThemeMode = ThemeMode.entries.find { it.storedValue() == value } ?: ThemeMode.SYSTEM

/**
 * SharedPreferences, not DataStore: the synchronous read gives the theme before the first frame, so it does not flash.
 * Kept outside the database on purpose, so it is not in the JSON export or the backup.
 */
class ThemePreference(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(storedThemeMode(preferences.getString(THEME_KEY, null)))
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    init {
        applyToSystem(_mode.value)
    }

    fun choose(choice: ThemeMode) {
        preferences.edit().putString(THEME_KEY, choice.storedValue()).apply()
        _mode.value = choice
        applyToSystem(choice)
    }

    /** Since Android 12 the system tracks the app's mode, so the splash screen and system bars follow the choice. */
    private fun applyToSystem(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiMode = context.getSystemService(UiModeManager::class.java) ?: return
        uiMode.setApplicationNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }
}
