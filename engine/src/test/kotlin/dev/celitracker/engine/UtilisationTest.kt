package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UtilisationTest {

    private fun utilisation(cotise: String) = Utilisation(droits = BigDecimal("10000.00"), cotise = BigDecimal(cotise))

    @Test
    fun `sous 80 pour cent, rien a signaler`() {
        assertEquals(NiveauUtilisation.NORMAL, utilisation("7999.99").niveau)
    }

    @Test
    fun `80 pour cent tout juste declenche l'attention`() {
        assertEquals(NiveauUtilisation.ATTENTION, utilisation("8000.00").niveau)
    }

    @Test
    fun `95 pour cent tout juste est critique`() {
        assertEquals(NiveauUtilisation.CRITIQUE, utilisation("9500.00").niveau)
    }

    @Test
    fun `utiliser exactement tous ses droits n'est pas une sur-cotisation`() {
        assertEquals(NiveauUtilisation.CRITIQUE, utilisation("10000.00").niveau)
    }

    @Test
    fun `un cent de trop est une sur-cotisation`() {
        val depasse = utilisation("10000.01")

        assertEquals(NiveauUtilisation.DEPASSE, depasse.niveau)
        assertEquals(BigDecimal("0.01"), depasse.excedent)
        assertEquals(BigDecimal("0.00"), depasse.restant)
    }

    @Test
    fun `le pourcentage est arrondi a l'entier`() {
        assertEquals(85, utilisation("8450.00").pourcentage)
    }

    @Test
    fun `sans droits, pas de pourcentage, et tout depot est un depassement`() {
        val sansDroits = Utilisation(droits = BigDecimal.ZERO, cotise = BigDecimal.ZERO)

        assertNull(sansDroits.pourcentage)
        assertEquals(NiveauUtilisation.NORMAL, sansDroits.niveau)
        assertEquals(NiveauUtilisation.DEPASSE, sansDroits.avecDepot(BigDecimal("1.00")).niveau)
    }

    @Test
    fun `l'utilisation CELI vient des droits du 1er janvier et des depots de l'annee`() {
        // Naissance en 2008: admissible au CELI en 2026.
        val profil = Profil(anneeNaissance = 2008, dateOuvertureCeliapp = null)
        val plafonds = listOf(PlafondAnnuel(Compte.CELI, 2026, BigDecimal("7000.00")))
        val depot = Transaction(Compte.CELI, LocalDate.of(2026, 3, 1), TypeTx.DEPOT, BigDecimal("6000.00"))

        val utilisation = utilisationCeli(profil, plafonds, listOf(depot), 2026)

        assertEquals(Utilisation(BigDecimal("7000.00"), BigDecimal("6000.00")), utilisation)
    }

    @Test
    fun `sans CELIAPP ouvert, pas d'utilisation`() {
        val profil = Profil(anneeNaissance = 2000, dateOuvertureCeliapp = null)

        assertNull(utilisationCeliapp(profil, emptyList(), 2026))
    }

    @Test
    fun `sans plafond connu, l'utilisation CELI est inconnue plutot que fausse`() {
        val profil = Profil(anneeNaissance = 2008, dateOuvertureCeliapp = null)
        val depot = Transaction(Compte.CELI, LocalDate.of(2026, 3, 1), TypeTx.DEPOT, BigDecimal("100.00"))

        assertNull(utilisationCeli(profil, emptyList(), listOf(depot), 2026))
    }
}
