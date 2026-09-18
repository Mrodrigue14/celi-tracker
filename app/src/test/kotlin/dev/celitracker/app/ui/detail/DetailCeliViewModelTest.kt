@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.detail

import dev.celitracker.app.DepotDeTest
import dev.celitracker.app.MainDeTest
import dev.celitracker.engine.Compte
import dev.celitracker.engine.PlafondAnnuel
import dev.celitracker.engine.Profil
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TypeTx
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetailCeliViewModelTest {

    private val fixture = DepotDeTest()
    private val depot = fixture.depot
    private val anneeCourante = LocalDate.now().year

    companion object {
        @JvmStatic
        @BeforeAll
        fun avant() = MainDeTest.installer()
    }

    @Test
    fun `sans profil, aucune ligne`() = runTest {
        val viewModel = DetailCeliViewModel(depot)

        assertEquals(emptyList(), viewModel.uiState.value.lignes)
    }

    @Test
    fun `un plafond absent est signale plafondManquant`() = runTest {
        // Naissance il y a 19 ans: admissible au CELI depuis l'an dernier.
        depot.enregistrerProfil(Profil(anneeCourante - 19, null))
        // Aucun plafond enregistre pour anneeCourante - 1 ni anneeCourante.

        val viewModel = DetailCeliViewModel(depot)
        val etat = viewModel.uiState.first { it.lignes.isNotEmpty() }

        assertTrue(etat.lignes.all { it.plafondManquant })
    }

    @Test
    fun `un plafond confirme n'est pas signale`() = runTest {
        depot.enregistrerProfil(Profil(anneeCourante - 18, null))
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELI, anneeCourante, BigDecimal("7000.00"), confirme = true))

        val viewModel = DetailCeliViewModel(depot)
        val etat = viewModel.uiState.first { it.lignes.isNotEmpty() }

        assertEquals(listOf(false), etat.lignes.map { it.plafondManquant })
    }

    @Test
    fun `seules les annees avec des transactions CELI offrent un lien vers le journal`() = runTest {
        depot.enregistrerProfil(Profil(anneeCourante - 20, LocalDate.of(anneeCourante - 1, 1, 1)))
        depot.ajouterTransaction(Transaction(Compte.CELI, LocalDate.of(anneeCourante, 1, 5), TypeTx.DEPOT, BigDecimal("100.00")))
        depot.ajouterTransaction(Transaction(Compte.CELIAPP, LocalDate.of(anneeCourante - 1, 6, 1), TypeTx.DEPOT, BigDecimal("100.00")))

        val viewModel = DetailCeliViewModel(depot)
        val etat = viewModel.uiState.first { it.lignes.isNotEmpty() }

        assertEquals(setOf(anneeCourante), etat.anneesAvecTransactions)
    }
}
