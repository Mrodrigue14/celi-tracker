package dev.celitracker.app.ui.composants

import kotlin.test.Test
import kotlin.test.assertEquals

class SommetArrondiTest {

    @Test
    fun `le sommet de l'axe est un montant rond au-dessus du maximum`() {
        assertEquals(40_000.0, sommetArrondi(33_500.0))
        assertEquals(25_000.0, sommetArrondi(24_612.0))
        assertEquals(8_000.0, sommetArrondi(7_000.0))
        assertEquals(10_000.0, sommetArrondi(10_000.0))
    }

    @Test
    fun `sans montant positif, l'axe garde une hauteur`() {
        assertEquals(1.0, sommetArrondi(0.0))
    }
}
