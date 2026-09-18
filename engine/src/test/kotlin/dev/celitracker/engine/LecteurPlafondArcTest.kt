package dev.celitracker.engine

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LecteurPlafondArcTest {

    /** Structure relevee sur la page de l'ARC le 2026-09-17, spans compris. */
    private val pageArc = """
        <h2 id="toc3">Plafond de cotisation à un CELI pour 2026</h2>
        <p>Le plafond de cotisation à un compte d'épargne libre <span class="nowrap">d'impôt (CELI)</span>
        <span class="nowrap">pour 2026</span> est <span class="nowrap">de 7 000 $</span>.
        Il sera ajouté à vos droits de cotisation <span class="nowrap">le 1er janvier 2026</span>.</p>
    """.trimIndent()

    @Test
    fun `lit l'annee et le montant a travers les balises`() {
        val plafond = lirePlafondCeliArc(pageArc)

        assertEquals(PlafondAnnuel(Compte.CELI, 2026, BigDecimal("7000.00"), confirme = false), plafond)
    }

    @Test
    fun `l'espace insecable des milliers est accepte`() {
        val avecInsecable = pageArc.replace("7 000", "7 000")

        assertEquals(BigDecimal("7000.00"), lirePlafondCeliArc(avecInsecable)?.montant)
    }

    @Test
    fun `le plafond lu n'est jamais confirme`() {
        assertEquals(false, lirePlafondCeliArc(pageArc)?.confirme)
    }

    @Test
    fun `une page sans la phrase attendue ne donne rien`() {
        assertNull(lirePlafondCeliArc("<h1>Page non trouvée</h1><p>Erreur 404</p>"))
    }

    @Test
    fun `une phrase sans montant lisible ne donne rien`() {
        assertNull(lirePlafondCeliArc("<p>Le plafond de cotisation pour 2027 est de bientôt $.</p>"))
    }

    @Test
    fun `un montant nul est refuse`() {
        assertNull(lirePlafondCeliArc("<p>Le plafond de cotisation pour 2027 est de 0 $.</p>"))
    }

    @Test
    fun `une adresse https de canada point ca est valide`() {
        assertTrue(adressePageArcValide("https://www.canada.ca/fr/agence-revenu/services.html"))
    }

    @Test
    fun `une adresse hors canada point ca ou sans https est refusee`() {
        assertFalse(adressePageArcValide("http://www.canada.ca/fr.html"))
        assertFalse(adressePageArcValide("https://exemple.com/plafonds"))
        assertFalse(adressePageArcValide("pas une adresse"))
        assertFalse(adressePageArcValide(""))
    }
}
