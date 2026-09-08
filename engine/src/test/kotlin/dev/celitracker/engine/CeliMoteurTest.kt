package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Raccourci: tout litteral monetaire d'un test s'ecrit a 2 decimales. */
private fun argent(valeur: String): BigDecimal = BigDecimal(valeur).argent()

private fun plafondsCeli(vararg paires: Pair<Int, String>): List<PlafondAnnuel> =
    paires.map { (annee, montant) -> PlafondAnnuel(Compte.CELI, annee, argent(montant)) }

private fun depot(date: String, montant: String) =
    Transaction(Compte.CELI, LocalDate.parse(date), TypeTx.DEPOT, argent(montant))

private fun retrait(date: String, montant: String) =
    Transaction(Compte.CELI, LocalDate.parse(date), TypeTx.RETRAIT, argent(montant))

class CeliMoteurTest {

    /**
     * scenario_2019_eligible_three_deposits
     *
     * Personne devenue admissible au CELI en 2019, aucun retrait, trois depots.
     * Scenario synthetique, derive des plafonds annuels publies par l'ARC.
     * Controle: cumul des plafonds 51 500 - depots 9 700 = 41 800.
     */
    @Test
    fun `scenario 2019 eligible three deposits`() {
        val profil = Profil(
            anneeAdmissibiliteCeli = 2019,
            anneeNaissance = 2001,
            dateOuvertureCeliapp = null,
        )
        val plafonds = plafondsCeli(
            2019 to "6000.00",
            2020 to "6000.00",
            2021 to "6000.00",
            2022 to "6000.00",
            2023 to "6500.00",
            2024 to "7000.00",
            2025 to "7000.00",
            2026 to "7000.00",
        )
        val transactions = listOf(
            depot("2021-03-10", "5000.00"),
            depot("2023-06-15", "3500.00"),
            depot("2024-11-02", "1200.00"),
        )

        val droits = CeliMoteur.droitsParAnnee(profil, plafonds, transactions, jusqua = 2026)
            .associateBy { it.annee }

        assertEquals(argent("6000.00"), droits.getValue(2019).droitsDebut)
        assertEquals(argent("6000.00"), droits.getValue(2019).droitsFin)

        assertEquals(argent("12000.00"), droits.getValue(2020).droitsDebut)
        assertEquals(argent("12000.00"), droits.getValue(2020).droitsFin)

        assertEquals(argent("18000.00"), droits.getValue(2021).droitsDebut)
        assertEquals(argent("5000.00"), droits.getValue(2021).depots)
        assertEquals(argent("13000.00"), droits.getValue(2021).droitsFin)

        assertEquals(argent("19000.00"), droits.getValue(2022).droitsDebut)
        assertEquals(argent("19000.00"), droits.getValue(2022).droitsFin)

        assertEquals(argent("25500.00"), droits.getValue(2023).droitsDebut)
        assertEquals(argent("3500.00"), droits.getValue(2023).depots)
        assertEquals(argent("22000.00"), droits.getValue(2023).droitsFin)

        assertEquals(argent("29000.00"), droits.getValue(2024).droitsDebut)
        assertEquals(argent("1200.00"), droits.getValue(2024).depots)
        assertEquals(argent("27800.00"), droits.getValue(2024).droitsFin)

        assertEquals(argent("34800.00"), droits.getValue(2025).droitsDebut)
        assertEquals(argent("34800.00"), droits.getValue(2025).droitsFin)

        assertEquals(argent("41800.00"), droits.getValue(2026).droitsDebut)
        assertEquals(argent("41800.00"), droits.getValue(2026).droitsFin)
    }

    @Test
    fun `les annees vont de l'admissibilite a l'annee demandee`() {
        val profil = Profil(2019, 2001, null)
        val droits = CeliMoteur.droitsParAnnee(
            profil,
            plafondsCeli(2019 to "6000.00", 2020 to "6000.00"),
            emptyList(),
            jusqua = 2020,
        )

        assertEquals(listOf(2019, 2020), droits.map { it.annee })
    }

    @Test
    fun `les transactions CELIAPP sont ignorees par le moteur CELI`() {
        val profil = Profil(2019, 2001, LocalDate.of(2023, 4, 1))
        val transactions = listOf(
            Transaction(Compte.CELIAPP, LocalDate.of(2019, 5, 1), TypeTx.DEPOT, argent("5000.00")),
        )

        val droits = CeliMoteur.droitsParAnnee(
            profil, plafondsCeli(2019 to "6000.00"), transactions, jusqua = 2019,
        )

        assertEquals(argent("0.00"), droits.single().depots)
        assertEquals(argent("6000.00"), droits.single().droitsFin)
    }

    @Test
    fun `un retrait ne redonne pas de droits dans l'annee du retrait`() {
        val profil = Profil(2019, 2001, null)
        val plafonds = plafondsCeli(2019 to "6000.00", 2020 to "6000.00")
        val transactions = listOf(
            depot("2020-03-01", "6000.00"),
            retrait("2020-08-01", "6000.00"),
        )

        val droits = CeliMoteur.droitsParAnnee(profil, plafonds, transactions, jusqua = 2020)
            .associateBy { it.annee }

        // 6000 (fin 2019) + 6000 (plafond 2020) + 0 (retraits 2019) = 12000.
        // Le retrait de 2020 n'ajoute RIEN aux droits de 2020.
        assertEquals(argent("12000.00"), droits.getValue(2020).droitsDebut)
        assertEquals(argent("6000.00"), droits.getValue(2020).retraits)
        assertEquals(argent("6000.00"), droits.getValue(2020).droitsFin)
    }

    @Test
    fun `un retrait redonne des droits le 1er janvier suivant`() {
        val profil = Profil(2019, 2001, null)
        val plafonds = plafondsCeli(
            2019 to "6000.00", 2020 to "6000.00", 2021 to "6000.00",
        )
        val transactions = listOf(
            depot("2020-03-01", "6000.00"),
            retrait("2020-08-01", "6000.00"),
        )

        val droits = CeliMoteur.droitsParAnnee(profil, plafonds, transactions, jusqua = 2021)
            .associateBy { it.annee }

        // 6000 (fin 2020) + 6000 (plafond 2021) + 6000 (retraits 2020) = 18000.
        assertEquals(argent("18000.00"), droits.getValue(2021).droitsDebut)
        assertEquals(argent("18000.00"), droits.getValue(2021).droitsFin)
    }

    @Test
    fun `une sur-cotisation se propage a l'annee suivante sans etre effacee`() {
        val profil = Profil(2019, 2001, null)
        val plafonds = plafondsCeli(2019 to "6000.00", 2020 to "6000.00")
        val transactions = listOf(depot("2019-05-01", "10000.00"))

        val droits = CeliMoteur.droitsParAnnee(profil, plafonds, transactions, jusqua = 2020)
            .associateBy { it.annee }

        // 6000 - 10000 = -4000. Un MAX(..., 0) ici donnerait 0 et masquerait
        // la sur-cotisation, exactement le bug du classeur remplace.
        assertEquals(argent("-4000.00"), droits.getValue(2019).droitsFin)
        // -4000 + 6000 = 2000: l'excedent est absorbe par le plafond suivant.
        assertEquals(argent("2000.00"), droits.getValue(2020).droitsDebut)
    }

    @Test
    fun `une annee sans plafond est signalee et ne cree aucun droit`() {
        val profil = Profil(2019, 2001, null)
        // 2020 absent de la table.
        val plafonds = plafondsCeli(2019 to "6000.00")

        val droits = CeliMoteur.droitsParAnnee(profil, plafonds, emptyList(), jusqua = 2020)
            .associateBy { it.annee }

        assertEquals(false, droits.getValue(2019).plafondManquant)
        assertEquals(true, droits.getValue(2020).plafondManquant)
        assertEquals(argent("0.00"), droits.getValue(2020).plafond)
        // Les droits stagnent: le moteur n'invente pas de plafond.
        assertEquals(argent("6000.00"), droits.getValue(2020).droitsFin)
    }

    @Test
    fun `un plafond non confirme est traite comme absent`() {
        val profil = Profil(2019, 2001, null)
        val plafonds = listOf(
            PlafondAnnuel(Compte.CELI, 2019, argent("6000.00")),
            // Propose par la lecture automatique du site de l'ARC, pas encore
            // valide par l'utilisateur: il ne doit pas entrer dans le calcul.
            PlafondAnnuel(Compte.CELI, 2020, argent("6000.00"), confirme = false),
        )

        val droits = CeliMoteur.droitsParAnnee(profil, plafonds, emptyList(), jusqua = 2020)
            .associateBy { it.annee }

        assertEquals(true, droits.getValue(2020).plafondManquant)
        assertEquals(argent("0.00"), droits.getValue(2020).plafond)
        assertEquals(argent("6000.00"), droits.getValue(2020).droitsFin)
    }
}
