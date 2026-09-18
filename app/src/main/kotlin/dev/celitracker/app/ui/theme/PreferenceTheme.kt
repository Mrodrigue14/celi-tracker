package dev.celitracker.app.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ModeTheme { SYSTEME, CLAIR, SOMBRE }

private const val FICHIER = "affichage"
private const val CLE_THEME = "theme"

/**
 * Choix clair, sombre ou systeme, garde sur l'appareil. C'est une preference
 * d'affichage, pas une donnee financiere: elle vit hors de la base, donc hors
 * de l'export JSON et de la sauvegarde.
 *
 * SharedPreferences plutot que DataStore: la lecture est synchrone, et le
 * theme doit etre connu avant la premiere image pour ne pas clignoter.
 */
class PreferenceTheme(private val contexte: Context) {
    private val preferences = contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        preferences.getString(CLE_THEME, null)
            ?.let { enregistre -> ModeTheme.entries.find { it.name == enregistre } }
            ?: ModeTheme.SYSTEME,
    )
    val mode: StateFlow<ModeTheme> = _mode.asStateFlow()

    init {
        appliquerAuSysteme(_mode.value)
    }

    fun choisir(nouveau: ModeTheme) {
        preferences.edit().putString(CLE_THEME, nouveau.name).apply()
        _mode.value = nouveau
        appliquerAuSysteme(nouveau)
    }

    /**
     * Depuis Android 12, le systeme connait le mode propre a l'application: la
     * fenetre de demarrage et les barres systeme suivent le choix, pas seulement
     * l'interface Compose.
     */
    private fun appliquerAuSysteme(mode: ModeTheme) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val uiMode = contexte.getSystemService(UiModeManager::class.java) ?: return
        uiMode.setApplicationNightMode(
            when (mode) {
                ModeTheme.SYSTEME -> UiModeManager.MODE_NIGHT_AUTO
                ModeTheme.CLAIR -> UiModeManager.MODE_NIGHT_NO
                ModeTheme.SOMBRE -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }
}
