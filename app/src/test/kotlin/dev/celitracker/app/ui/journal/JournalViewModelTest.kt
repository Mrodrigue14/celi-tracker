@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.journal

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JournalViewModelTest {

    private val fixture = DepotDeTest()
    private val depot = fixture.depot

    /** Naissance en 2002: admissible au CELI en 2020. */
    private val profil = Profil(anneeNaissance = 2002, dateOuvertureCeliapp = LocalDate.of(2023, 6, 1))

    companion object {
        @JvmStatic
        @BeforeAll
        fun avant() = MainDeTest.installer()
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

    /** 2020 est la premiere annee d'admissibilite du profil: ses droits valent son plafond. */
    private suspend fun droits2020(plafond: String = "6000.00") {
        depot.enregistrerProfil(profil)
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELI, 2020, BigDecimal(plafond)))
    }

    private fun JournalViewModel.saisirDepot(date: String, montant: String) {
        ouvrirNouvelle()
        modifierDate(date)
        modifierMontant(montant)
        enregistrer()
    }

    @Test
    fun `un depot qui porte l'utilisation a 95 pour cent ou plus demande une confirmation`() = runTest {
        droits2020()
        val viewModel = JournalViewModel(depot, Compte.CELI)

        viewModel.saisirDepot("2020-06-01", "5820")
        val avertissement = assertNotNull(viewModel.uiState.first { it.formulaire?.avertissement != null }.formulaire?.avertissement)

        assertTrue(avertissement.contains("97 %"))
        assertEquals(emptyList(), depot.transactions())
    }

    @Test
    fun `confirmer l'avertissement enregistre le depot`() = runTest {
        droits2020()
        val viewModel = JournalViewModel(depot, Compte.CELI)
        viewModel.saisirDepot("2020-06-01", "5820")
        viewModel.uiState.first { it.formulaire?.avertissement != null }

        viewModel.enregistrer()
        viewModel.uiState.first { it.formulaire == null }

        assertEquals(1, depot.transactions().size)
    }

    @Test
    fun `un depot au-dela des droits annonce l'excedent`() = runTest {
        droits2020()
        val viewModel = JournalViewModel(depot, Compte.CELI)

        viewModel.saisirDepot("2020-06-01", "6100")
        val avertissement = assertNotNull(viewModel.uiState.first { it.formulaire?.avertissement != null }.formulaire?.avertissement)

        assertTrue(avertissement.contains("dépasse"))
        assertTrue(avertissement.contains("100,00"))
    }

    @Test
    fun `entre 80 et 95 pour cent, le depot passe et le message donne l'utilisation`() = runTest {
        droits2020()
        val viewModel = JournalViewModel(depot, Compte.CELI)

        viewModel.saisirDepot("2020-06-01", "5100")
        val etat = viewModel.uiState.first { it.formulaire == null && it.message != null }

        assertEquals(1, depot.transactions().size)
        assertTrue(assertNotNull(etat.message).contains("85 %"))
    }

    @Test
    fun `un retrait ne demande jamais de confirmation`() = runTest {
        droits2020()
        depot.ajouterTransaction(Transaction(Compte.CELI, LocalDate.of(2020, 3, 1), TypeTx.DEPOT, BigDecimal("5900.00")))
        val viewModel = JournalViewModel(depot, Compte.CELI)

        viewModel.ouvrirNouvelle()
        viewModel.modifierDate("2020-06-01")
        viewModel.modifierType(TypeTx.RETRAIT)
        viewModel.modifierMontant("100")
        viewModel.enregistrer()
        viewModel.uiState.first { it.formulaire == null }

        assertEquals(2, depot.transactions().size)
    }

    @Test
    fun `venir du bouton Ajouter ouvre directement la feuille de saisie`() = runTest {
        depot.enregistrerProfil(profil)

        val viewModel = JournalViewModel(depot, Compte.CELI, ouvrirAjout = true)

        assertNotNull(viewModel.uiState.value.formulaire)
    }

    @Test
    fun `ouvrir le journal normalement n'ouvre pas de feuille`() = runTest {
        val viewModel = JournalViewModel(depot, Compte.CELI)

        assertNull(viewModel.uiState.value.formulaire)
    }

    @Test
    fun `changer de compte affiche le journal de l'autre compte sans quitter l'ecran`() = runTest {
        depot.enregistrerProfil(profil)
        depot.ajouterTransaction(depotCeli(LocalDate.of(2024, 1, 10), "100.00"))
        depot.ajouterTransaction(Transaction(Compte.CELIAPP, LocalDate.of(2024, 5, 1), TypeTx.DEPOT, BigDecimal("300.00")))
        val viewModel = JournalViewModel(depot, Compte.CELI)
        viewModel.uiState.first { it.transactions.isNotEmpty() }

        viewModel.changerCompte(Compte.CELIAPP)
        val etat = viewModel.uiState.first { it.compte == Compte.CELIAPP && it.transactions.isNotEmpty() }

        assertEquals(listOf(Compte.CELIAPP), etat.transactions.map { it.compte })
    }

    @Test
    fun `l'annee demandee depuis le detail n'est visee qu'une fois`() = runTest {
        val viewModel = JournalViewModel(depot, Compte.CELI, anneeCiblee = 2024)
        assertEquals(2024, viewModel.uiState.value.anneeCiblee)

        viewModel.anneeCibleeAtteinte()

        assertNull(viewModel.uiState.value.anneeCiblee)
    }
}
