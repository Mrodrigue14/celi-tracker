package dev.celitracker.app.ui.journal

import dev.celitracker.engine.Account
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HeaderPositionTest {

    private fun deposit(year: Int, day: Int) = Transaction(Account.TFSA, LocalDate.of(year, 1, day), TransactionType.DEPOSIT, BigDecimal("10.00"))

    /** In display order: 2026 (two rows), then 2024 (one row). */
    private val transactions = listOf(deposit(2026, 2), deposit(2026, 1), deposit(2024, 1))

    @Test
    fun `a year's header follows the header and rows of more recent years`() {
        assertEquals(0, headerPosition(transactions, 2026))
        assertEquals(3, headerPosition(transactions, 2024))
    }

    @Test
    fun `a year absent from the journal gives no position`() {
        assertEquals(-1, headerPosition(transactions, 2025))
    }
}
