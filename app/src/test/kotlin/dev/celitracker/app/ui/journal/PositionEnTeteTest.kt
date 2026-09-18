package dev.celitracker.app.ui.journal

import dev.celitracker.engine.Compte
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TypeTx
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PositionEnTeteTest {

    private fun depot(annee: Int, jour: Int) = Transaction(Compte.CELI, LocalDate.of(annee, 1, jour), TypeTx.DEPOT, BigDecimal("10.00"))

    /** Dans l'ordre d'affichage: 2026 (deux lignes), puis 2024 (une ligne). */
    private val transactions = listOf(depot(2026, 2), depot(2026, 1), depot(2024, 1))

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
