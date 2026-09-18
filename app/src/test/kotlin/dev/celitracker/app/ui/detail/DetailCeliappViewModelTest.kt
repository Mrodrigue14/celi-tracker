@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.detail

import dev.celitracker.app.DepotDeTest
import dev.celitracker.app.MainDeTest
import dev.celitracker.engine.CeliappMoteur
import dev.celitracker.engine.Profil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DetailCeliappViewModelTest {

    private val fixture = DepotDeTest()
    private val depot = fixture.depot
    private val anneeCourante = LocalDate.now().year

    companion object {
        @JvmStatic
        @BeforeAll
        fun avant() = MainDeTest.installer()
    }

    @Test
    fun `sans CELIAPP ouvert, aucune ligne`() = runTest {
        depot.enregistrerProfil(Profil(1990, dateOuvertureCeliapp = null))

        val viewModel = DetailCeliappViewModel(depot)

        assertEquals(emptyList(), viewModel.uiState.value.lignes)
    }

    @Test
    fun `avec CELIAPP ouvert, les lignes proviennent du moteur`() = runTest {
        val ouverture = LocalDate.of(anneeCourante, 3, 1)
        val profil = Profil(1990, dateOuvertureCeliapp = ouverture)
        depot.enregistrerProfil(profil)

        val viewModel = DetailCeliappViewModel(depot)
        val etat = viewModel.uiState.first { it.lignes.isNotEmpty() }

        val attendu = CeliappMoteur.droitsParAnnee(profil, depot.transactions(), anneeCourante)
        assertEquals(attendu, etat.lignes)
    }
}
