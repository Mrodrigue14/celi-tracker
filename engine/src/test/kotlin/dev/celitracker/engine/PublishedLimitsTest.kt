package dev.celitracker.engine

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class PublishedLimitsTest {

    @Test
    fun `the amounts are the ones published by the CRA`() {
        assertEquals(BigDecimal("5000.00"), PUBLISHED_TFSA_LIMITS.getValue(2009))
        assertEquals(BigDecimal("10000.00"), PUBLISHED_TFSA_LIMITS.getValue(2015))
        assertEquals(BigDecimal("6500.00"), PUBLISHED_TFSA_LIMITS.getValue(2023))
        assertEquals(BigDecimal("7000.00"), PUBLISHED_TFSA_LIMITS.getValue(2026))
    }

    @Test
    fun `the table covers every year since 2009, with no gaps`() {
        val years = PUBLISHED_TFSA_LIMITS.keys.sorted()

        assertEquals((2009..years.last()).toList(), years)
    }
}
