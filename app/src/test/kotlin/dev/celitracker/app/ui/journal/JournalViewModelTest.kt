@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.journal

import dev.celitracker.app.DepotDeTest
import dev.celitracker.engine.Compte
import dev.celitracker.engine.Profil
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TypeTx
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JournalViewModelTest {

    private val fixture = DepotDeTest()
    private val depot = fixture.depot

    /** Naissance en 2002: admissible au CELI en 2020. */
    private val profil = Profil(anneeNaissance = 2002, dateOuvertureCeliapp = LocalDate.of(2023, 6, 1))

    @AfterTest
    fun apres() = fixture.fermer()

    companion object {
        // Meme raison que DetailCeliViewModelTest: Main fixe pour toute la classe.
        @JvmStatic
        @BeforeAll
        fun avant() = Dispatchers.setMain(UnconfinedTestDispatcher())

        @JvmStatic
        @AfterAll
        fun apresTout() = Dispatchers.resetMain()
    }

    private fun depotCeli(date: LocalDate, montant: String) = Transaction(Compte.CELI, date, TypeTx.DEPOT, BigDecimal(montant))

    @Test
    fun `n'affiche que les transactions du compte, la plus recente d'abord`() = runTest {
        depot.enregistrerProfil(profil)
        depot.ajouterTransaction(depotCeli(LocalDate.of(2024, 1, 10), "100.00"))
        depot.ajouterTransaction(depotCeli(LocalDate.of(2025, 1, 10), "200.00"))
        depot.ajouterTransaction(Transaction(Compte.CELIAPP, LocalDate.of(2024, 5, 1), TypeTx.DEPOT, BigDecimal("300.00")))

        val viewModel = JournalViewModel(depot, Compte.CELI)
        val etat = viewModel.uiState.first { it.transactions.isNotEmpty() }

        assertEquals(listOf(LocalDate.of(2025, 1, 10), LocalDate.of(2024, 1, 10)), etat.transactions.map { it.date })
    }

    @Test
    fun `une nouvelle transaction valide est enregistree et le formulaire se ferme`() = runTest {
        depot.enregistrerProfil(profil)
        val viewModel = JournalViewModel(depot, Compte.CELI)

        viewModel.ouvrirNouvelle()
        viewModel.modifierDate("2025-02-03")
        viewModel.modifierMontant("150,25")
        viewModel.enregistrer()
        viewModel.uiState.first { it.formulaire == null }

        assertEquals(listOf(depotCeli(LocalDate.of(2025, 2, 3), "150.25")), depot.transactions().map { it.copy(id = 0) })
    }

    @Test
    fun `le message est efface une fois affiche`() = runTest {
        depot.enregistrerProfil(profil)
        val viewModel = JournalViewModel(depot, Compte.CELI)

        viewModel.ouvrirNouvelle()
        viewModel.modifierDate("2025-02-03")
        viewModel.modifierMontant("150,25")
        viewModel.enregistrer()
        viewModel.uiState.first { it.message != null }
        viewModel.messageAffiche()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `un refus du depot garde le formulaire ouvert avec l'erreur`() = runTest {
        depot.enregistrerProfil(profil)
        val viewModel = JournalViewModel(depot, Compte.CELI)

        viewModel.ouvrirNouvelle()
        viewModel.modifierDate("2019-12-31")
        viewModel.modifierMontant("100")
        viewModel.enregistrer()
        val formulaire = viewModel.uiState.first { it.formulaire?.erreur != null }.formulaire

        assertNotNull(formulaire)
        assertEquals(emptyList(), depot.transactions())
    }

    @Test
    fun `une modification remplace la transaction existante`() = runTest {
        depot.enregistrerProfil(profil)
        depot.ajouterTransaction(depotCeli(LocalDate.of(2024, 1, 10), "100.00"))
        val viewModel = JournalViewModel(depot, Compte.CELI)
        val existante = viewModel.uiState.first { it.transactions.isNotEmpty() }.transactions.single()

        viewModel.ouvrirModification(existante)
        viewModel.modifierType(TypeTx.RETRAIT)
        viewModel.enregistrer()
        viewModel.uiState.first { it.formulaire == null }

        assertEquals(listOf(existante.copy(type = TypeTx.RETRAIT)), depot.transactions())
    }

    @Test
    fun `la suppression retire la transaction`() = runTest {
        depot.enregistrerProfil(profil)
        depot.ajouterTransaction(depotCeli(LocalDate.of(2024, 1, 10), "100.00"))
        val viewModel = JournalViewModel(depot, Compte.CELI)
        val existante = viewModel.uiState.first { it.transactions.isNotEmpty() }.transactions.single()

        viewModel.ouvrirModification(existante)
        viewModel.supprimer()
        val etat = viewModel.uiState.first { it.formulaire == null }

        assertEquals(emptyList(), etat.transactions)
    }
}
