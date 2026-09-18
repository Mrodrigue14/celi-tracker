@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.celitracker.app.ui.reglages

import dev.celitracker.app.DepotDeTest
import dev.celitracker.app.MainDeTest
import dev.celitracker.data.URL_PAGE_ARC_PAR_DEFAUT
import dev.celitracker.engine.Profil
import dev.celitracker.engine.Reglages
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReglagesViewModelTest {

    private val fixture = DepotDeTest()
    private val depot = fixture.depot

    /** Telechargement bouchonne: aucun test de cette classe ne touche au reseau. */
    private val pageArc: suspend (String) -> String = {
        """<p>Le plafond de cotisation à un CELI <span class="nowrap">pour 2027</span> est de 7 500 $.</p>"""
    }

    /**
     * Pour les tests qui ne portent pas sur l'ARC. Sans ca, la lecture lancee a
     * la construction du ViewModel ecrit elle aussi dans `message`, et un
     * StateFlow ne garde que la derniere valeur: le message attendu par le test
     * peut disparaitre avant d'etre vu.
     */
    private val horsLigne: suspend (String) -> String = { throw java.io.IOException("hors ligne") }

    companion object {
        @JvmStatic
        @BeforeAll
        fun avant() = MainDeTest.installer()
    }

    @Test
    fun `enregistrerProfil persiste le profil valide`() = runTest {
        val viewModel = ReglagesViewModel(depot, horsLigne)
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
        val viewModel = ReglagesViewModel(depot, horsLigne)
        viewModel.modifierAnneeNaissance("pas-un-nombre")

        viewModel.enregistrerProfil()

        assertNull(depot.profil())
    }

    @Test
    fun `l'annee d'admissibilite affichee suit l'annee de naissance`() = runTest {
        val viewModel = ReglagesViewModel(depot, horsLigne)

        viewModel.modifierAnneeNaissance("1995")

        assertEquals(2013, viewModel.uiState.value.anneeAdmissibiliteCeli)
    }

    @Test
    fun `une naissance dans le futur n'est pas une saisie valide`() = runTest {
        val viewModel = ReglagesViewModel(depot, horsLigne)

        viewModel.modifierAnneeNaissance((LocalDate.now().year + 1).toString())

        assertNull(viewModel.uiState.value.anneeNaissanceValide)
    }

    @Test
    fun `le plafond lu sur le site de l'ARC attend une confirmation`() = runTest {
        val viewModel = ReglagesViewModel(depot, pageArc)

        val etat = viewModel.uiState.first { it.propositions.isNotEmpty() }

        val propose = etat.propositions.single()
        assertEquals(2027, propose.annee)
        assertEquals(BigDecimal("7500.00"), propose.montant)
        // La proposition ne compte pas comme un plafond de la table.
        assertTrue(etat.plafondsConfirmes.isEmpty())
    }

    @Test
    fun `confirmer une proposition la fait entrer dans la table`() = runTest {
        val viewModel = ReglagesViewModel(depot, pageArc)
        val propose = viewModel.uiState.first { it.propositions.isNotEmpty() }.propositions.single()

        viewModel.confirmerProposition(propose)
        val etat = viewModel.uiState.first { it.plafondsConfirmes.isNotEmpty() }

        assertEquals(listOf(2027), etat.plafondsConfirmes.map { it.annee })
        assertTrue(etat.propositions.isEmpty())
    }

    @Test
    fun `rejeter une proposition l'efface`() = runTest {
        val viewModel = ReglagesViewModel(depot, pageArc)
        val propose = viewModel.uiState.first { it.propositions.isNotEmpty() }.propositions.single()

        viewModel.rejeterProposition(propose)
        val etat = viewModel.uiState.first { it.propositions.isEmpty() }

        assertTrue(etat.plafonds.isEmpty())
    }

    @Test
    fun `une lecture impossible est dite, pas tue`() = runTest {
        val viewModel = ReglagesViewModel(depot) { throw java.io.IOException("réseau indisponible") }

        val etat = viewModel.uiState.first { it.erreurArc != null }

        assertEquals("réseau indisponible", etat.erreurArc)
    }

    @Test
    fun `une adresse invalide ne remplace pas celle qui marche`() = runTest {
        val viewModel = ReglagesViewModel(depot, horsLigne)

        viewModel.modifierUrlPageArc("https://exemple.com/plafonds")
        viewModel.enregistrerUrlPageArc()
        val etat = viewModel.uiState.first { it.message != null }

        assertEquals(URL_PAGE_ARC_PAR_DEFAUT, etat.urlPageArc)
        assertEquals(URL_PAGE_ARC_PAR_DEFAUT, depot.reglages().urlPageArc)
    }

    @Test
    fun `une adresse valide de l'ARC est enregistree telle quelle`() = runTest {
        val autrePage = "https://www.canada.ca/fr/agence-revenu/autre-page.html"
        val viewModel = ReglagesViewModel(depot, pageArc)

        viewModel.modifierUrlPageArc(autrePage)
        viewModel.enregistrerUrlPageArc()
        viewModel.uiState.first { it.message?.startsWith("Adresse") == true }

        assertEquals(autrePage, depot.reglages().urlPageArc)
    }

    @Test
    fun `l'export rend le contenu de la base et l'import le relit`() = runTest {
        val viewModel = ReglagesViewModel(depot, horsLigne)
        viewModel.modifierAnneeNaissance("1995")
        viewModel.enregistrerProfil()
        viewModel.uiState.first { it.message == "Profil enregistré." }

        var exporte = ""
        viewModel.exporter { exporte = it }
        viewModel.uiState.first { it.message == "Données exportées." }

        fixture.depot.enregistrerProfil(Profil(anneeNaissance = 1980, dateOuvertureCeliapp = null))
        viewModel.importer { exporte }
        viewModel.uiState.first { it.message == "Données importées." }

        assertEquals(1995, depot.profil()?.anneeNaissance)
    }

    @Test
    fun `un fichier illisible est refuse sans toucher aux donnees`() = runTest {
        val viewModel = ReglagesViewModel(depot, horsLigne)
        viewModel.modifierAnneeNaissance("1995")
        viewModel.enregistrerProfil()
        viewModel.uiState.first { it.message == "Profil enregistré." }

        viewModel.importer { "ceci n'est pas du JSON" }
        val etat = viewModel.uiState.first { it.message?.startsWith("Import refusé") == true }

        assertTrue(etat.message!!.startsWith("Import refusé"))
        assertEquals(1995, depot.profil()?.anneeNaissance)
    }

    @Test
    fun `ajouterPlafond persiste et vide les champs de saisie`() = runTest {
        val viewModel = ReglagesViewModel(depot, horsLigne)
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

    @Test
    fun `retablir l'adresse d'origine repare une adresse cassee enregistree autrefois`() = runTest {
        depot.enregistrerReglages(Reglages(urlPageArc = "https://www.canada.ca/fr/agence-renu/page.html", dateDerniereVerification = null))
        val viewModel = ReglagesViewModel(depot, pageArc)

        viewModel.retablirUrlPageArc()
        viewModel.uiState.first { it.message?.startsWith("Adresse") == true }

        assertEquals(URL_PAGE_ARC_PAR_DEFAUT, depot.reglages().urlPageArc)
    }
}
