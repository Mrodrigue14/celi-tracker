package dev.celitracker.engine

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class PlafondsPubliesTest {

    @Test
    fun `les montants sont ceux publies par l'ARC`() {
        assertEquals(BigDecimal("5000.00"), PLAFONDS_CELI_PUBLIES.getValue(2009))
        assertEquals(BigDecimal("10000.00"), PLAFONDS_CELI_PUBLIES.getValue(2015))
        assertEquals(BigDecimal("6500.00"), PLAFONDS_CELI_PUBLIES.getValue(2023))
        assertEquals(BigDecimal("7000.00"), PLAFONDS_CELI_PUBLIES.getValue(2026))
    }

    @Test
    fun `la table couvre chaque annee depuis 2009, sans trou`() {
        val annees = PLAFONDS_CELI_PUBLIES.keys.sorted()

        assertEquals((2009..annees.last()).toList(), annees)
    }
}
