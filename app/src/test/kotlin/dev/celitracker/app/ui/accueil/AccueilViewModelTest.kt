@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.accueil

import dev.celitracker.app.DepotDeTest
import dev.celitracker.app.MainDeTest
import dev.celitracker.engine.CeliMoteur
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
import kotlin.test.assertFalse

class AccueilViewModelTest {

    private val fixture = DepotDeTest()
    private val depot = fixture.depot
    private val anneeCourante = LocalDate.now().year

    companion object {
        @JvmStatic
        @BeforeAll
        fun avant() = MainDeTest.installer()
    }

    @Test
    fun `sans profil, l'etat reste vide`() = runTest {
        val viewModel = AccueilViewModel(depot)

        val etat = viewModel.uiState.value

        assertFalse(etat.profilEnregistre)
    }

    @Test
    fun `avec profil et plafond, les droits proviennent du moteur`() = runTest {
        val profil = Profil(
            anneeNaissance = 1990,
            dateOuvertureCeliapp = null,
        )
        depot.enregistrerProfil(profil)
        val plafond = PlafondAnnuel(Compte.CELI, anneeCourante, BigDecimal("7000.00"), confirme = true)
        depot.enregistrerPlafond(plafond)
        depot.ajouterTransaction(
            Transaction(Compte.CELI, LocalDate.of(anneeCourante, 1, 15), TypeTx.DEPOT, BigDecimal("1000.00")),
        )

        val viewModel = AccueilViewModel(depot)
        val etat = viewModel.uiState.first { it.profilEnregistre }

        val attendu = CeliMoteur.droitsParAnnee(
            profil,
            listOf(plafond),
            depot.transactions(),
            anneeCourante,
        ).last()
        assertEquals(attendu, etat.celiAnneeCourante)
    }
}
