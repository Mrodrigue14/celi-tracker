@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.reglages

import dev.celitracker.app.DepotDeTest
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class ReglagesViewModelTest {

    private val fixture = DepotDeTest()
    private val depot = fixture.depot

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
    fun `enregistrerProfil persiste le profil valide`() = runTest {
        val viewModel = ReglagesViewModel(depot)
        viewModel.modifierAnneeAdmissibiliteCeli("2010")
        viewModel.modifierAnneeNaissance("1995")
        viewModel.modifierDateOuvertureCeliapp("2023-04-01")

        viewModel.enregistrerProfil()
        val etat = viewModel.uiState.first { it.message != null }

        assertEquals("Profil enregistré.", etat.message)
        val profil = depot.profil()
        assertEquals(2010, profil?.anneeAdmissibiliteCeli)
        assertEquals(1995, profil?.anneeNaissance)
    }

    @Test
    fun `enregistrerProfil ignore une saisie invalide`() = runTest {
        val viewModel = ReglagesViewModel(depot)
        viewModel.modifierAnneeAdmissibiliteCeli("pas-un-nombre")
        viewModel.modifierAnneeNaissance("1995")

        viewModel.enregistrerProfil()

        assertNull(depot.profil())
    }

    @Test
    fun `ajouterPlafond persiste et vide les champs de saisie`() = runTest {
        val viewModel = ReglagesViewModel(depot)
        viewModel.modifierNouveauPlafondAnnee("2026")
        viewModel.modifierNouveauPlafondMontant("7000.00")

        viewModel.ajouterPlafond()
        val etat = viewModel.uiState.first { it.plafonds.isNotEmpty() }

        assertEquals(BigDecimal("7000.00"), etat.plafonds.single().montant)
        assertEquals("", etat.nouveauPlafondAnnee)
        assertEquals("", etat.nouveauPlafondMontant)
    }

    @Test
    fun `un montant negatif est refuse par la validation`() {
        val etat = ReglagesUiState(nouveauPlafondAnnee = "2026", nouveauPlafondMontant = "-100")

        assertTrue(!etat.nouveauPlafondValide)
    }
}
