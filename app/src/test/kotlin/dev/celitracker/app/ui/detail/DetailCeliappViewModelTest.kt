@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.detail

import dev.celitracker.app.DepotDeTest
import dev.celitracker.engine.CeliappMoteur
import dev.celitracker.engine.Profil
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class DetailCeliappViewModelTest {

    private val fixture = DepotDeTest()
    private val depot = fixture.depot
    private val anneeCourante = LocalDate.now().year

    @AfterTest
    fun apres() = fixture.fermer()

    companion object {
        // Cf. DetailCeliViewModelTest: Main reste dispo sur toute la classe,
        // pas seulement par test, pour ne pas planter sur une coroutine encore
        // en vol sur le vrai dispatcher IO de Room.
        @JvmStatic
        @BeforeAll
        fun avant() = Dispatchers.setMain(UnconfinedTestDispatcher())

        @JvmStatic
        @AfterAll
        fun apresTout() = Dispatchers.resetMain()
    }

    @Test
    fun `sans CELIAPP ouvert, aucune ligne`() = runTest {
        depot.enregistrerProfil(Profil(anneeCourante, 1990, dateOuvertureCeliapp = null))

        val viewModel = DetailCeliappViewModel(depot)

        assertEquals(emptyList(), viewModel.uiState.value.lignes)
    }

    @Test
    fun `avec CELIAPP ouvert, les lignes proviennent du moteur`() = runTest {
        val ouverture = LocalDate.of(anneeCourante, 3, 1)
        val profil = Profil(anneeCourante, 1990, dateOuvertureCeliapp = ouverture)
        depot.enregistrerProfil(profil)

        val viewModel = DetailCeliappViewModel(depot)
        val etat = viewModel.uiState.first { it.lignes.isNotEmpty() }

        val attendu = CeliappMoteur.droitsParAnnee(profil, depot.transactions(), anneeCourante)
        assertEquals(attendu, etat.lignes)
    }
}
