package dev.celitracker.app.ui.format

import java.math.BigDecimal
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AmountTest {

    @Test
    fun `a positive amount contains the dollar sign and the decimals`() {
        val text = BigDecimal("7000.00").formatAmount()
        assertTrue("$" in text, "expected a dollar sign in '$text'")
        assertTrue("7" in text && "000" in text, "expected the digits of the amount in '$text'")
        assertTrue("00" in text, "expected the decimals in '$text'")
    }

    @Test
    fun `a negative amount keeps the sign`() {
        val text = BigDecimal("-150.50").formatAmount()
        assertTrue("-" in text, "expected a minus sign in '$text'")
    }

    @Test
    fun `a zero amount does not throw`() {
        val text = BigDecimal.ZERO.setScale(2).formatAmount()
        assertTrue("0" in text)
    }

    @Test
    fun `a date is spelled out in full, in the requested language`() {
        val date = java.time.LocalDate.of(2026, 9, 4)

        assertEquals("4 septembre 2026", date.formatDate(Locale.CANADA_FRENCH))
        assertEquals("September 4, 2026", date.formatDate(Locale.CANADA))
    }

    @Test
    fun `the currency stays the Canadian dollar regardless of language`() {
        val amount = BigDecimal("1234.50")

        assertEquals("CA$1,234.50", amount.formatAmount(Locale.US))
        assertTrue(amount.formatAmount(Locale.CANADA_FRENCH).startsWith("1"))
        assertTrue(amount.formatAmount(Locale.CANADA_FRENCH).endsWith("$"))
    }

    @Test
    fun `a difference shows a plus sign only when positive`() {
        assertEquals("+CA$150.00", BigDecimal("150.00").formatSignedAmount(Locale.US))
        assertEquals("-CA$150.00", BigDecimal("-150.00").formatSignedAmount(Locale.US))
        assertEquals("CA$0.00", BigDecimal("0.00").formatSignedAmount(Locale.US))
    }

    @Test
    fun `an entered amount must be positive`() {
        assertEquals(BigDecimal("12.50"), "12,50".toEnteredAmount())
        assertNull("0".toEnteredAmount())
        assertNull("abc".toEnteredAmount())
    }

    @Test
    fun `an entered room may be zero but not negative`() {
        assertEquals(BigDecimal("0"), "0".toEnteredRoom())
        assertEquals(BigDecimal("0.00"), "0,00".toEnteredRoom())
        assertEquals(BigDecimal("41800.00"), "41800.00".toEnteredRoom())
        assertNull("-1".toEnteredRoom())
        assertNull("".toEnteredRoom())
    }
}
