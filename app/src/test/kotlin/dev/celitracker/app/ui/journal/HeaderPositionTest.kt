package dev.celitracker.app.ui.journal

import dev.celitracker.engine.Account
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HeaderPositionTest {

    private fun repository(year: Int, day: Int) = Transaction(Account.TFSA, LocalDate.of(year, 1, day), TransactionType.DEPOSIT, BigDecimal("10.00"))

    /** Dans l'ordre d'affichage: 2026 (deux rows), puis 2024 (une row). */
    private val transactions = listOf(repository(2026, 2), repository(2026, 1), repository(2024, 1))

    @Test
    fun `l'en-tete d'une annee suit l'en-tete et les lignes des annees plus recentes`() {
        assertEquals(0, positionEnTete(transactions, 2026))
        assertEquals(3, positionEnTete(transactions, 2024))
    }

    @Test
    fun `une annee absente du journal ne donne aucune position`() {
        assertEquals(-1, positionEnTete(transactions, 2025))
    }
}
