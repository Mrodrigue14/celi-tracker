@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.reglages

import dev.celitracker.app.DepotDeTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        viewModel.modifierAnneeNaissance("1995")
        viewModel.modifierDateOuvertureCeliapp("2023-04-01")

        viewModel.enregistrerProfil()
        val etat = viewModel.uiState.first { it.message != null }

        assertEquals("Profil enregistré.", etat.message)
        val profil = depot.profil()
        assertEquals(1995, profil?.anneeNaissance)
        // Derivee de la naissance, jamais saisie.
        assertEquals(2013, profil?.anneeAdmissibiliteCeli)
    }

    @Test
    fun `enregistrerProfil ignore une saisie invalide`() = runTest {
        val viewModel = ReglagesViewModel(depot)
        viewModel.modifierAnneeNaissance("pas-un-nombre")

        viewModel.enregistrerProfil()

        assertNull(depot.profil())
    }

    @Test
    fun `l'annee d'admissibilite affichee suit l'annee de naissance`() = runTest {
        val viewModel = ReglagesViewModel(depot)

        viewModel.modifierAnneeNaissance("1995")

        assertEquals(2013, viewModel.uiState.value.anneeAdmissibiliteCeli)
    }

    @Test
    fun `une naissance dans le futur n'est pas une saisie valide`() = runTest {
        val viewModel = ReglagesViewModel(depot)

        viewModel.modifierAnneeNaissance((LocalDate.now().year + 1).toString())

        assertNull(viewModel.uiState.value.anneeNaissanceValide)
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
