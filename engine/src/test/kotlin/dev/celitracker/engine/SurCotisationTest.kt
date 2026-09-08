package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun argent(valeur: String): BigDecimal = BigDecimal(valeur).argent()

class SurCotisationTest {

    private val profil = Profil(2019, 2001, null)
    private val plafonds = listOf(
        PlafondAnnuel(Compte.CELI, 2019, argent("6000.00")),
        PlafondAnnuel(Compte.CELI, 2020, argent("6000.00")),
    )

    private fun tx(date: String, type: TypeTx, montant: String) =
        Transaction(Compte.CELI, LocalDate.parse(date), type, argent(montant))

    @Test
    fun `aucun excedent quand les cotisations respectent les droits`() {
        val transactions = listOf(tx("2019-03-15", TypeTx.DEPOT, "6000.00"))

        val excedents = SurCotisation.excedentsCeli(
            profil, plafonds, transactions, jusqua = YearMonth.of(2019, 12),
        )

        assertTrue(excedents.isEmpty())
    }

    @Test
    fun `un excedent persiste chaque mois jusqu'a la fin de l'annee`() {
        // Droits 2019 = 6000, depot de 10000 -> excedent de 4000.
        val transactions = listOf(tx("2019-03-15", TypeTx.DEPOT, "10000.00"))

        val excedents = SurCotisation.excedentsCeli(
            profil, plafonds, transactions, jusqua = YearMonth.of(2019, 12),
        )

        // Mars a decembre inclus = 10 mois.
        assertEquals(10, excedents.size)
        assertEquals(3, excedents.first().mois)
        assertEquals(argent("4000.00"), excedents.first().excedentMax)
        assertEquals(argent("40.00"), excedents.first().penalite)
        assertEquals(12, excedents.last().mois)
        assertEquals(argent("4000.00"), excedents.last().excedentMax)
    }

    @Test
    fun `re-cotiser un montant retire la meme annee recree l'excedent`() {
        // LE piege du regime. Un retrait annule l'excedent existant, mais ne
        // redonne AUCUN droit avant le 1er janvier suivant. Re-cotiser le meme
        // montant la meme annee cree donc un excedent plein.
        val transactions = listOf(
            tx("2019-02-01", TypeTx.DEPOT, "6000.00"),   // droits epuises, 0 excedent
            tx("2019-04-01", TypeTx.RETRAIT, "6000.00"), // aucun droit restitue
            tx("2019-06-01", TypeTx.DEPOT, "6000.00"),   // re-cotisation -> excedent
        )

        val excedents = SurCotisation.excedentsCeli(
            profil, plafonds, transactions, jusqua = YearMonth.of(2019, 12),
        ).associateBy { it.mois }

        // Fevrier a mai: les droits couvrent les cotisations, aucun excedent.
        assertTrue(excedents[2] == null)
        assertTrue(excedents[5] == null)
        // Juin: les droits etaient deja epuises, le retrait n'en a pas rendu.
        assertEquals(argent("6000.00"), excedents.getValue(6).excedentMax)
        assertEquals(argent("60.00"), excedents.getValue(6).penalite)
        // L'excedent persiste jusqu'a la fin de l'annee: juin a decembre.
        assertEquals(7, excedents.size)
        assertEquals(argent("6000.00"), excedents.getValue(12).excedentMax)
    }

    @Test
    fun `un retrait annule l'excedent mais le mois reste facture`() {
        val transactions = listOf(
            tx("2019-02-01", TypeTx.DEPOT, "6000.00"),
            tx("2019-03-01", TypeTx.DEPOT, "1000.00"),   // depassement de 1000
            tx("2019-04-15", TypeTx.RETRAIT, "1000.00"), // corrige en avril
        )

        val excedents = SurCotisation.excedentsCeli(
            profil, plafonds, transactions, jusqua = YearMonth.of(2019, 12),
        ).associateBy { it.mois }

        assertEquals(argent("1000.00"), excedents.getValue(3).excedentMax)
        assertEquals(argent("10.00"), excedents.getValue(3).penalite)
        // Avril reste facture: la penalite porte sur l'excedent le PLUS ELEVE
        // du mois, et il valait 1000 jusqu'au 15.
        assertEquals(argent("1000.00"), excedents.getValue(4).excedentMax)
        // Mai est propre: l'excedent a ete annule par le retrait.
        assertTrue(excedents[5] == null)
    }

    @Test
    fun `l'excedent est absorbe par les droits de l'annee suivante`() {
        // Droits 2019 = 6000, depot de 10000 -> excedent de 4000 jusqu'en
        // decembre. Au 1er janvier 2020, le plafond de 6000 absorbe l'excedent
        // (droitsDebut 2020 = -4000 + 6000 = 2000 > 0).
        val transactions = listOf(tx("2019-03-15", TypeTx.DEPOT, "10000.00"))

        val excedents = SurCotisation.excedentsCeli(
            profil, plafonds, transactions, jusqua = YearMonth.of(2020, 12),
        )

        assertEquals(2019, excedents.last().annee)
        assertEquals(12, excedents.last().mois)
        assertTrue(excedents.none { it.annee == 2020 })
    }

    @Test
    fun `aucun excedent sans transaction`() {
        val excedents = SurCotisation.excedentsCeli(
            profil, plafonds, emptyList(), jusqua = YearMonth.of(2019, 12),
        )

        assertTrue(excedents.isEmpty())
    }

    @Test
    fun `une transaction anterieure a l'admissibilite ne cree aucun excedent`() {
        // CeliMoteur ignore cette transaction (sa boucle demarre en 2019).
        // SurCotisation doit l'ignorer aussi, sans quoi les 9000 deviendraient
        // un excedent facture a 1 % par mois.
        val transactions = listOf(tx("2018-05-01", TypeTx.DEPOT, "9000.00"))

        val excedents = SurCotisation.excedentsCeli(
            profil, plafonds, transactions, jusqua = YearMonth.of(2019, 12),
        )

        assertTrue(excedents.isEmpty())
    }
}
