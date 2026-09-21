package dev.celitracker.engine

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class PublishedLimitsTest {

    @Test
    fun `les montants sont ceux publies par l'ARC`() {
        assertEquals(BigDecimal("5000.00"), PUBLISHED_TFSA_LIMITS.getValue(2009))
        assertEquals(BigDecimal("10000.00"), PUBLISHED_TFSA_LIMITS.getValue(2015))
        assertEquals(BigDecimal("6500.00"), PUBLISHED_TFSA_LIMITS.getValue(2023))
        assertEquals(BigDecimal("7000.00"), PUBLISHED_TFSA_LIMITS.getValue(2026))
    }

    @Test
    fun `la table couvre chaque annee depuis 2009, sans trou`() {
        val years = PUBLISHED_TFSA_LIMITS.keys.sorted()

        assertEquals((2009..years.last()).toList(), years)
    }
}
