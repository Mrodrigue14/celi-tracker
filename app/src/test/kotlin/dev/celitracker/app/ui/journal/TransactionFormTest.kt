package dev.celitracker.app.ui.journal

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransactionFormTest {

    @Test
    fun `la virgule est acceptee comme separateur decimal`() {
        assertEquals(BigDecimal("12.50"), TransactionForm(amount = "12,50").validAmount)
    }

    @Test
    fun `un montant nul n'est pas valide`() {
        assertNull(TransactionForm(amount = "0").validAmount)
    }

    @Test
    fun `une date hors format ISO n'est pas valide`() {
        assertNull(TransactionForm(date = "15/01/2026").validDate)
    }
}
