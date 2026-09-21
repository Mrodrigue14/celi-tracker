package dev.celitracker.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class AxisMaxTest {

    @Test
    fun `le sommet de l'axe est un montant rond au-dessus du maximum`() {
        assertEquals(40_000.0, roundedAxisMax(33_500.0))
        assertEquals(25_000.0, roundedAxisMax(24_612.0))
        assertEquals(8_000.0, roundedAxisMax(7_000.0))
        assertEquals(10_000.0, roundedAxisMax(10_000.0))
    }

    @Test
    fun `sans montant positif, l'axe garde une hauteur`() {
        assertEquals(1.0, roundedAxisMax(0.0))
    }
}
