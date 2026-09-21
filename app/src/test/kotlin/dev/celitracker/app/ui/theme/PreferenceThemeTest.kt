package dev.celitracker.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class PreferenceThemeTest {

    /** Values already written on users' devices by earlier versions. */
    private val stored = mapOf(ModeTheme.SYSTEME to "SYSTEME", ModeTheme.CLAIR to "CLAIR", ModeTheme.SOMBRE to "SOMBRE")

    @Test
    fun `each theme is written and read back under its shipped value`() {
        stored.forEach { (mode, value) ->
            assertEquals(value, mode.valeurEnregistree())
            assertEquals(mode, modeThemeEnregistre(value))
        }
    }

    @Test
    fun `a missing or unknown value falls back to the system theme`() {
        assertEquals(ModeTheme.SYSTEME, modeThemeEnregistre(null))
        assertEquals(ModeTheme.SYSTEME, modeThemeEnregistre("SEPIA"))
    }
}
