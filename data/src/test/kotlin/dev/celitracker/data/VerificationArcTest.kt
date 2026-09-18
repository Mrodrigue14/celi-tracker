package dev.celitracker.data

import androidx.room.Room
import dev.celitracker.engine.Compte
import dev.celitracker.engine.PlafondAnnuel
import dev.celitracker.engine.Reglages
import kotlinx.coroutines.test.runTest
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VerificationArcTest {

    private val fichier = File.createTempFile("celi-tracker-arc", ".db")
    private val base = configurerBase(Room.databaseBuilder<CeliTrackerBase>(name = fichier.absolutePath))
    private val depot = Depot(base)
    private val aujourdhui = LocalDate.of(2026, 9, 17)

    @AfterTest
    fun fermer() {
        base.close()
        fichier.delete()
    }

    private val pageArc = """
        <p>Le plafond de cotisation à un compte d'épargne libre <span class="nowrap">d'impôt (CELI)</span>
        <span class="nowrap">pour 2027</span> est <span class="nowrap">de 7 500 $</span>.</p>
    """.trimIndent()

    @Test
    fun `le plafond lu est enregistre comme proposition`() = runTest {
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELI, 2026, BigDecimal("7000.00"), confirme = true))

        val resultat = depot.verifierPlafondsArc({ pageArc }, aujourdhui)

        assertIs<ResultatVerificationArc.Propose>(resultat)
        val propose = depot.plafonds().single { it.annee == 2027 }
        assertEquals(BigDecimal("7500.00"), propose.montant)
        assertEquals(false, propose.confirme)
    }

    @Test
    fun `aucune lecture quand l'annee courante et la suivante sont connues`() = runTest {
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELI, 2026, BigDecimal("7000.00"), confirme = true))
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELI, 2027, BigDecimal("7500.00"), confirme = true))

        val resultat = depot.verifierPlafondsArc({ error("la page ne doit pas etre telechargee") }, aujourdhui)

        assertEquals(ResultatVerificationArc.Inutile, resultat)
    }

    @Test
    fun `une verification de moins d'un mois n'est pas refaite`() = runTest {
        depot.enregistrerReglages(
            Reglages(
                urlPageArc = URL_PAGE_ARC_PAR_DEFAUT,
                dateDerniereVerification = aujourdhui.minusDays(5).atStartOfDay(ZoneOffset.UTC).toInstant(),
            ),
        )

        val resultat = depot.verifierPlafondsArc({ error("la page ne doit pas etre telechargee") }, aujourdhui)

        assertEquals(ResultatVerificationArc.Inutile, resultat)
    }

    @Test
    fun `une page injoignable est un echec, et la date est quand meme notee`() = runTest {
        val resultat = depot.verifierPlafondsArc({ throw java.io.IOException("réseau indisponible") }, aujourdhui)

        assertIs<ResultatVerificationArc.Echec>(resultat)
        assertEquals(
            aujourdhui.atStartOfDay(ZoneOffset.UTC).toInstant(),
            depot.reglages().dateDerniereVerification,
        )
    }

    @Test
    fun `une page illisible est un echec et n'ecrit aucun plafond`() = runTest {
        val resultat = depot.verifierPlafondsArc({ "<h1>Page non trouvée</h1>" }, aujourdhui)

        assertIs<ResultatVerificationArc.Echec>(resultat)
        assertTrue(depot.plafonds().isEmpty())
    }

    @Test
    fun `un plafond deja saisi pour l'annee lue n'est pas remplace`() = runTest {
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELI, 2027, BigDecimal("7000.00"), confirme = true))

        val resultat = depot.verifierPlafondsArc({ pageArc }, aujourdhui)

        assertEquals(ResultatVerificationArc.Inutile, resultat)
        assertEquals(BigDecimal("7000.00"), depot.plafonds().single { it.annee == 2027 }.montant)
    }

    @Test
    fun `une adresse dont la page donne le plafond est enregistree`() = runTest {
        val autre = "https://www.canada.ca/fr/agence-revenu/autre-page.html"

        val resultat = depot.changerAdressePageArc(autre) { pageArc }

        assertEquals(ResultatAdresseArc.Enregistree, resultat)
        assertEquals(autre, depot.reglages().urlPageArc)
    }

    @Test
    fun `une page de canada point ca sans le plafond garde l'adresse precedente`() = runTest {
        val resultat = depot.changerAdressePageArc("https://www.canada.ca/fr/autre.html") { "<h1>Page non trouvée</h1>" }

        assertIs<ResultatAdresseArc.Refusee>(resultat)
        assertEquals(URL_PAGE_ARC_PAR_DEFAUT, depot.reglages().urlPageArc)
    }

    @Test
    fun `une page injoignable garde l'adresse precedente`() = runTest {
        val resultat = depot.changerAdressePageArc("https://www.canada.ca/fr/autre.html") { throw java.io.IOException("hors ligne") }

        assertIs<ResultatAdresseArc.Refusee>(resultat)
        assertEquals(URL_PAGE_ARC_PAR_DEFAUT, depot.reglages().urlPageArc)
    }

    @Test
    fun `une adresse hors canada point ca n'est meme pas telechargee`() = runTest {
        val resultat = depot.changerAdressePageArc("https://exemple.com/plafonds") { error("ne doit pas etre telechargee") }

        assertIs<ResultatAdresseArc.Refusee>(resultat)
        assertEquals(URL_PAGE_ARC_PAR_DEFAUT, depot.reglages().urlPageArc)
    }
}
