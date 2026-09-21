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

/** The value written to the preferences file; must not change once shipped. */
internal fun ThemeMode.storedValue(): String = when (this) {
    ThemeMode.SYSTEM -> "SYSTEME"
    ThemeMode.LIGHT -> "CLAIR"
    ThemeMode.DARK -> "SOMBRE"
}

internal fun storedThemeMode(value: String?): ThemeMode = ThemeMode.entries.find { it.storedValue() == value } ?: ThemeMode.SYSTEM

/**
 * Light, dark, or system choice, kept on the device. This is a display
 * preference, not financial data: it lives outside the database, so outside
 * the JSON export and the backup.
 *
 * SharedPreferences rather than DataStore: the read is synchronous, and the
 * theme must be known before the first frame so it does not flash.
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

    /**
     * Since Android 12, the system knows the app's own mode: the
     * splash screen and system bars follow the choice, not just
     * the Compose UI.
     */
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
