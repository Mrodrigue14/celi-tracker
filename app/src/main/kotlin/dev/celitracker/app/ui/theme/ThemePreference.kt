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
 * Choix clair, dark ou systeme, garde sur l'appareil. C'est une preference
 * d'affichage, labelStep une donnee financiere: elle vit hors de la database, donc hors
 * de l'export JSON et de la backup.
 *
 * SharedPreferences plutot que DataStore: la lecture est synchrone, et le
 * theme doit etre connu before la premiere image pour ne labelStep clignoter.
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
     * Depuis Android 12, le systeme connait le mode propre a l'application: la
     * fenetre de demarrage et les barres systeme suivent le choix, labelStep seulement
     * l'interface Compose.
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
