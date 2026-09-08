@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.detail

import dev.celitracker.app.DepotDeTest
import dev.celitracker.engine.Compte
import dev.celitracker.engine.PlafondAnnuel
import dev.celitracker.engine.Profil
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class DetailCeliViewModelTest {

    private val fixture = DepotDeTest()
    private val depot = fixture.depot
    private val anneeCourante = LocalDate.now().year

    @AfterTest
    fun apres() = fixture.fermer()

    companion object {
        // Sur toute la classe, pas par test: un test peut laisser une
        // coroutine de viewModelScope encore en vol sur le vrai dispatcher IO
        // de Room. Reinitialiser Main entre deux tests la ferait planter en
        // reprenant sur un Main deja reinitialise, et polluer un test suivant.
        @JvmStatic
        @BeforeAll
        fun avant() = Dispatchers.setMain(UnconfinedTestDispatcher())

        @JvmStatic
        @AfterAll
        fun apresTout() = Dispatchers.resetMain()
    }

    @Test
    fun `sans profil, aucune ligne`() = runTest {
        val viewModel = DetailCeliViewModel(depot)

        assertEquals(emptyList(), viewModel.uiState.value.lignes)
    }

    @Test
    fun `un plafond absent est signale plafondManquant`() = runTest {
        depot.enregistrerProfil(Profil(anneeCourante - 1, 1990, null))
        // Aucun plafond enregistre pour anneeCourante - 1 ni anneeCourante.

        val viewModel = DetailCeliViewModel(depot)
        val etat = viewModel.uiState.first { it.lignes.isNotEmpty() }

        assertTrue(etat.lignes.all { it.plafondManquant })
    }

    @Test
    fun `un plafond confirme n'est pas signale`() = runTest {
        depot.enregistrerProfil(Profil(anneeCourante, 1990, null))
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELI, anneeCourante, BigDecimal("7000.00"), confirme = true))

        val viewModel = DetailCeliViewModel(depot)
        val etat = viewModel.uiState.first { it.lignes.isNotEmpty() }

        assertEquals(listOf(false), etat.lignes.map { it.plafondManquant })
    }
}
