package dev.celitracker.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class AxisMaxTest {

    @Test
    fun `the top of the axis is a round amount above the maximum`() {
        assertEquals(40_000.0, roundedAxisMax(33_500.0))
        assertEquals(25_000.0, roundedAxisMax(24_612.0))
        assertEquals(8_000.0, roundedAxisMax(7_000.0))
        assertEquals(10_000.0, roundedAxisMax(10_000.0))
    }

    @Test
    fun `with no positive amount, the axis keeps a height`() {
        assertEquals(1.0, roundedAxisMax(0.0))
    }
}
