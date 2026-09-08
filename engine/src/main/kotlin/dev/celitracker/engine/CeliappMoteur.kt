package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate

data class DroitsAnneeCeliapp(
    val annee: Int,
    val reportEntrant: BigDecimal,
    val droitsAnnee: BigDecimal,
    val depots: BigDecimal,
    val retraits: BigDecimal,
    val reportSortant: BigDecimal,
    val plafondVieRestant: BigDecimal,
)

/**
 * Moteur CELIAPP. Deliberement separe de [CeliMoteur]: les deux regimes
 * divergent sur chaque axe, et reutiliser le chemin de restitution du CELI
 * pour le CELIAPP est le bug de correctness le plus probable de ce projet.
 *
 * Les trois plafonds sont fixes par la loi et ne sont PAS indexes: contrairement
 * au CELI, il n'y a rien a recuperer sur le site de l'ARC.
 */
object CeliappMoteur {

    val PLAFOND_ANNUEL: BigDecimal = BigDecimal("8000").argent()

    /**
     * Plafond du report, PAR ANNEE D'ARRIVEE. Le report ne se cumule pas:
     * une personne qui ne cotise jamais voit son plafond annuel se stabiliser
     * a 16000 (8000 + 8000), pas croitre de 8000 chaque annee.
     */
    val REPORT_MAX: BigDecimal = BigDecimal("8000").argent()

    val PLAFOND_VIE: BigDecimal = BigDecimal("40000").argent()

    fun droitsParAnnee(
        profil: Profil,
        transactions: List<Transaction>,
        jusqua: Int,
    ): List<DroitsAnneeCeliapp> {
        // L'accumulation demarre a l'OUVERTURE du compte, pas aux 18 ans.
        val ouverture = profil.dateOuvertureCeliapp ?: return emptyList()
        val txFhsa = transactions.filter { it.compte == Compte.CELIAPP }

        val resultat = mutableListOf<DroitsAnneeCeliapp>()
        var reportEntrant = BigDecimal.ZERO
        var cotisationsCumulees = BigDecimal.ZERO

        for (annee in ouverture.year..jusqua) {
            val depots = somme(txFhsa, annee, TypeTx.DEPOT)
            val retraits = somme(txFhsa, annee, TypeTx.RETRAIT)

            val vieRestantAvant = (PLAFOND_VIE - cotisationsCumulees)
                .coerceAtLeast(BigDecimal.ZERO)
            // Le min() interne est redondant tant que reportEntrant est borne
            // en amont, mais il rend l'invariant explicite plutot qu'implicite:
            // le report ne se cumule pas, et c'est le piege du regime.
            val reportUtilisable = minOf(reportEntrant, REPORT_MAX)
            val droitsAnnee = minOf(PLAFOND_ANNUEL + reportUtilisable, vieRestantAvant)

            // min(..., REPORT_MAX) et NON une accumulation: c'est toute la
            // difference avec le CELI et le REER.
            val reportSortant = minOf(
                (droitsAnnee - depots).coerceAtLeast(BigDecimal.ZERO),
                REPORT_MAX,
            )

            cotisationsCumulees += depots

            resultat += DroitsAnneeCeliapp(
                annee = annee,
                reportEntrant = reportEntrant.argent(),
                droitsAnnee = droitsAnnee.argent(),
                depots = depots.argent(),
                // Enregistre pour l'affichage du solde, mais n'entre dans AUCUN
                // calcul de droits: un retrait CELIAPP ne redonne jamais rien.
                retraits = retraits.argent(),
                reportSortant = reportSortant.argent(),
                plafondVieRestant = (PLAFOND_VIE - cotisationsCumulees)
                    .coerceAtLeast(BigDecimal.ZERO).argent(),
            )

            reportEntrant = reportSortant
        }
        return resultat
    }

    /**
     * Fin de la periode de participation maximale: le 31 decembre de l'annee ou
     * survient le PREMIER des trois evenements suivants.
     *
     *   1. le 15e anniversaire de l'ouverture du premier CELIAPP
     *   2. les 71 ans du titulaire
     *   3. l'annee suivant le premier retrait admissible
     *
     * La branche 3 n'est PAS implementee: elle exige de distinguer un retrait
     * admissible (achat d'une premiere propriete) d'un retrait ordinaire, ce que
     * le modele ne suit pas. L'echeance reelle peut donc etre plus rapprochee
     * que celle retournee ici. Exclusion assumee, documentee dans la spec.
     */
    fun finPeriodeParticipation(profil: Profil): LocalDate? {
        val ouverture = profil.dateOuvertureCeliapp ?: return null
        val anneeQuinzeAns = ouverture.year + 15
        val anneeSoixanteEtOnzeAns = profil.anneeNaissance + 71
        return LocalDate.of(minOf(anneeQuinzeAns, anneeSoixanteEtOnzeAns), 12, 31)
    }

    private fun somme(transactions: List<Transaction>, annee: Int, type: TypeTx): BigDecimal =
        transactions
            .filter { it.date.year == annee && it.type == type }
            .fold(BigDecimal.ZERO) { total, tx -> total + tx.montant }
}
