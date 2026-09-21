package dev.celitracker.app.ui.journal

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransactionFormTest {

    @Test
    fun `the comma is accepted as a decimal separator`() {
        assertEquals(BigDecimal("12.50"), TransactionForm(amount = "12,50").validAmount)
    }

    @Test
    fun `a zero amount is not valid`() {
        assertNull(TransactionForm(amount = "0").validAmount)
    }

    @Test
    fun `a date outside ISO format is not valid`() {
        assertNull(TransactionForm(date = "15/01/2026").validDate)
    }
}
