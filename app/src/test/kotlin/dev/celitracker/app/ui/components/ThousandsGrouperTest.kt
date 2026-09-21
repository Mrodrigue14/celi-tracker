package dev.celitracker.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ThousandsGrouperTest {

    @Test
    fun `groups by thousands starting from the right`() {
        assertEquals("1,234,567", ThousandsGrouper("1234567", ',').text)
        assertEquals("1,234", ThousandsGrouper("1234", ',').text)
        assertEquals("123", ThousandsGrouper("123", ',').text)
        assertEquals("", ThousandsGrouper("", ',').text)
    }

    @Test
    fun `any separator character is accepted, eg the French non-breaking space`() {
        assertEquals("1 234 567", ThousandsGrouper("1234567", ' ').text)
    }

    @Test
    fun `an original position becomes its position after grouping`() {
        val grouper = ThousandsGrouper("1234567", ',')

        assertEquals(0, grouper.toGrouped(0))
        assertEquals(2, grouper.toGrouped(1))
        assertEquals(6, grouper.toGrouped(4))
        assertEquals(9, grouper.toGrouped(7))
    }

    @Test
    fun `a cursor placed on a separator falls back onto the last digit already typed`() {
        val grouper = ThousandsGrouper("1234567", ',')

        assertEquals(0, grouper.toOriginal(1))
    }

    @Test
    fun `round trip from original to grouped to original returns the starting position`() {
        val grouper = ThousandsGrouper("1234567", ',')

        for (i in 0..7) {
            assertEquals(i, grouper.toOriginal(grouper.toGrouped(i)))
        }
    }
}

class LimitToTwoDecimalsTest {

    @Test
    fun `with no separator, nothing changes`() {
        assertEquals("1234", "1234".limitToTwoDecimals())
        assertEquals("", "".limitToTwoDecimals())
    }

    @Test
    fun `a third decimal and everything after it are ignored`() {
        assertEquals("12.34", "12.345".limitToTwoDecimals())
        assertEquals("12,34", "12,3456789".limitToTwoDecimals())
    }

    @Test
    fun `a single decimal is kept as is`() {
        assertEquals("12.3", "12.3".limitToTwoDecimals())
        assertEquals("12.", "12.".limitToTwoDecimals())
    }

    @Test
    fun `a second separator typed by mistake disappears along with what follows`() {
        assertEquals("12.34", "12.34.56".limitToTwoDecimals())
    }
}
