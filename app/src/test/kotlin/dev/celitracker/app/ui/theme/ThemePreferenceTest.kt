package dev.celitracker.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemePreferenceTest {

    private val stored = mapOf(ThemeMode.SYSTEM to "SYSTEME", ThemeMode.LIGHT to "CLAIR", ThemeMode.DARK to "SOMBRE")

    @Test
    fun `each theme is written and read back under its shipped value`() {
        stored.forEach { (mode, value) ->
            assertEquals(value, mode.storedValue())
            assertEquals(mode, storedThemeMode(value))
        }
    }

    @Test
    fun `a missing or unknown value falls back to the system theme`() {
        assertEquals(ThemeMode.SYSTEM, storedThemeMode(null))
        assertEquals(ThemeMode.SYSTEM, storedThemeMode("SEPIA"))
    }
}
