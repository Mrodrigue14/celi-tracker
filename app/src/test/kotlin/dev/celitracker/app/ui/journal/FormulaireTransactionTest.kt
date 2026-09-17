package dev.celitracker.app.ui.journal

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormulaireTransactionTest {

    @Test
    fun `la virgule est acceptee comme separateur decimal`() {
        assertEquals(BigDecimal("12.50"), FormulaireTransaction(montant = "12,50").montantValide)
    }

    @Test
    fun `un montant nul n'est pas valide`() {
        assertNull(FormulaireTransaction(montant = "0").montantValide)
    }

    @Test
    fun `une date hors format ISO n'est pas valide`() {
        assertNull(FormulaireTransaction(date = "15/01/2026").dateValide)
    }
}
