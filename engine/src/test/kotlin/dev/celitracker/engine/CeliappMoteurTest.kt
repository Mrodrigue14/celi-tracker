package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun argent(valeur: String): BigDecimal = BigDecimal(valeur).argent()

private fun depotFhsa(date: String, montant: String) =
    Transaction(Compte.CELIAPP, LocalDate.parse(date), TypeTx.DEPOT, argent(montant))

private fun retraitFhsa(date: String, montant: String) =
    Transaction(Compte.CELIAPP, LocalDate.parse(date), TypeTx.RETRAIT, argent(montant))

class CeliappMoteurTest {

    private val profilOuvert2023 = Profil(
        anneeAdmissibiliteCeli = 2019,
        anneeNaissance = 2001,
        dateOuvertureCeliapp = LocalDate.of(2023, 4, 1),
    )

    /**
     * scenario_fhsa_opened_2023_no_contributions
     *
     * Garde-fou contre l'erreur la plus tentante du regime: croire que trois
     * annees sans cotiser accumulent 32000$. Le report est plafonne a 8000$
     * PAR ANNEE D'ARRIVEE, donc le plafond annuel se stabilise a 16000$.
     */
    @Test
    fun `scenario fhsa opened 2023 no contributions`() {
        val droits = CeliappMoteur
            .droitsParAnnee(profilOuvert2023, emptyList(), jusqua = 2026)
            .associateBy { it.annee }

        assertEquals(argent("0.00"), droits.getValue(2023).reportEntrant)
        assertEquals(argent("8000.00"), droits.getValue(2023).droitsAnnee)
        assertEquals(argent("8000.00"), droits.getValue(2023).reportSortant)

        assertEquals(argent("8000.00"), droits.getValue(2024).reportEntrant)
        assertEquals(argent("16000.00"), droits.getValue(2024).droitsAnnee)
        // Le point critique: 8000, PAS 16000. Le report ne se cumule pas.
        assertEquals(argent("8000.00"), droits.getValue(2024).reportSortant)

        assertEquals(argent("16000.00"), droits.getValue(2025).droitsAnnee)
        assertEquals(argent("8000.00"), droits.getValue(2025).reportSortant)

        assertEquals(argent("16000.00"), droits.getValue(2026).droitsAnnee)
        assertEquals(argent("40000.00"), droits.getValue(2026).plafondVieRestant)
    }

    @Test
    fun `l'accumulation demarre a l'annee d'ouverture du compte`() {
        val droits = CeliappMoteur.droitsParAnnee(profilOuvert2023, emptyList(), jusqua = 2026)

        assertEquals(2023, droits.first().annee)
    }

    @Test
    fun `aucun droit sans compte ouvert`() {
        val profilSansCompte = Profil(2019, 2001, dateOuvertureCeliapp = null)

        assertTrue(CeliappMoteur.droitsParAnnee(profilSansCompte, emptyList(), 2026).isEmpty())
    }

    @Test
    fun `un retrait ne redonne jamais de droits`() {
        val transactions = listOf(
            depotFhsa("2023-10-01", "8000.00"),
            retraitFhsa("2023-11-01", "8000.00"),
        )

        val droits = CeliappMoteur
            .droitsParAnnee(profilOuvert2023, transactions, jusqua = 2024)
            .associateBy { it.annee }

        // Le retrait est enregistre...
        assertEquals(argent("8000.00"), droits.getValue(2023).retraits)
        // ...mais les 8000 cotises restent consommes a vie: 40000 - 8000.
        assertEquals(argent("32000.00"), droits.getValue(2023).plafondVieRestant)
        // 2023 entierement utilise -> aucun report vers 2024.
        assertEquals(argent("0.00"), droits.getValue(2024).reportEntrant)
        assertEquals(argent("8000.00"), droits.getValue(2024).droitsAnnee)
    }

    @Test
    fun `une cotisation partielle reporte le solde inutilise`() {
        val transactions = listOf(depotFhsa("2023-10-01", "3000.00"))

        val droits = CeliappMoteur
            .droitsParAnnee(profilOuvert2023, transactions, jusqua = 2024)
            .associateBy { it.annee }

        // 8000 - 3000 = 5000 inutilises, sous le plafond de report.
        assertEquals(argent("5000.00"), droits.getValue(2023).reportSortant)
        assertEquals(argent("13000.00"), droits.getValue(2024).droitsAnnee)
    }

    @Test
    fun `le plafond a vie de 40000 borne les droits annuels`() {
        val transactions = listOf(
            depotFhsa("2023-10-01", "8000.00"),
            depotFhsa("2024-10-01", "16000.00"),
            depotFhsa("2025-10-01", "16000.00"),
        )

        val droits = CeliappMoteur
            .droitsParAnnee(profilOuvert2023, transactions, jusqua = 2026)
            .associateBy { it.annee }

        // 8000 + 16000 + 16000 = 40000 cotises: le plafond a vie est atteint.
        assertEquals(argent("0.00"), droits.getValue(2025).plafondVieRestant)
        // Meme si le report autoriserait davantage, il ne reste rien a vie.
        assertEquals(argent("0.00"), droits.getValue(2026).droitsAnnee)
    }

    @Test
    fun `l'echeance est le 31 decembre de l'annee du 15e anniversaire`() {
        // Ouvert en avril 2023 -> 15e anniversaire en avril 2038.
        // La periode se termine le 31 decembre de CETTE annee-la.
        assertEquals(
            LocalDate.of(2038, 12, 31),
            CeliappMoteur.finPeriodeParticipation(profilOuvert2023),
        )
    }

    @Test
    fun `la branche des 71 ans l'emporte quand elle est plus rapprochee`() {
        val profilAge = Profil(
            anneeAdmissibiliteCeli = 1975,
            anneeNaissance = 1960, // 71 ans en 2031
            dateOuvertureCeliapp = LocalDate.of(2023, 4, 1), // 15 ans -> 2038
        )

        assertEquals(
            LocalDate.of(2031, 12, 31),
            CeliappMoteur.finPeriodeParticipation(profilAge),
        )
    }

    @Test
    fun `aucune echeance sans compte ouvert`() {
        val profilSansCompte = Profil(2019, 2001, dateOuvertureCeliapp = null)

        assertEquals(null, CeliappMoteur.finPeriodeParticipation(profilSansCompte))
    }
}
