package dev.celitracker.engine

import java.math.BigDecimal
import java.time.YearMonth

/**
 * Excedent d'un mois et penalite correspondante.
 *
 * [excedentMax] est l'excedent le PLUS ELEVE atteint pendant le mois, pas celui
 * de la fin du mois: c'est sur cette base que l'ARC calcule la penalite.
 */
data class ExcedentMensuel(
    val annee: Int,
    val mois: Int,
    val excedentMax: BigDecimal,
    val penalite: BigDecimal,
)

object SurCotisation {

    /** 1 % par mois de l'excedent le plus eleve du mois. */
    private val TAUX_PENALITE_MENSUELLE = BigDecimal("0.01")

    fun excedentsCeli(
        profil: Profil,
        plafonds: List<PlafondAnnuel>,
        transactions: List<Transaction>,
        jusqua: YearMonth,
    ): List<ExcedentMensuel> {
        val txCeli = transactions
            .filter { it.compte == Compte.CELI }
            .sortedBy { it.date }
        val premier = txCeli.firstOrNull() ?: return emptyList()

        val droitsDebutParAnnee = CeliMoteur
            .droitsParAnnee(profil, plafonds, transactions, jusqua.year)
            .associate { it.annee to it.droitsDebut }

        val resultat = mutableListOf<ExcedentMensuel>()
        var mois = YearMonth.from(premier.date)

        // DEUX variables, et non un cumul net. Un cumul net rendrait une
        // re-cotisation gratuite, alors que c'est exactement le piege du regime:
        //   - un DEPOT consomme d'abord les droits restants, le reste devient
        //     de l'excedent;
        //   - un RETRAIT annule l'excedent existant, mais ne restitue AUCUN
        //     droit -- ceux-ci ne reviennent que le 1er janvier suivant, via
        //     droitsDebut de l'annee suivante.
        var anneeCourante = Int.MIN_VALUE
        var droitsRestants = BigDecimal.ZERO
        var excedent = BigDecimal.ZERO

        while (!mois.isAfter(jusqua)) {
            if (mois.year != anneeCourante) {
                anneeCourante = mois.year
                // droitsDebut inclut deja le report, le plafond de l'annee et
                // les retraits de l'annee precedente. S'il est negatif, la
                // sur-cotisation n'a pas ete absorbee et se poursuit.
                val debut = droitsDebutParAnnee[anneeCourante] ?: BigDecimal.ZERO
                if (debut.signum() < 0) {
                    excedent = debut.negate()
                    droitsRestants = BigDecimal.ZERO
                } else {
                    excedent = BigDecimal.ZERO
                    droitsRestants = debut
                }
            }

            // L'excedent reporte du mois precedent est deja facturable.
            var excedentMax = excedent

            for (tx in txCeli.filter { YearMonth.from(it.date) == mois }) {
                if (tx.type == TypeTx.DEPOT) {
                    val absorbe = minOf(droitsRestants, tx.montant)
                    droitsRestants -= absorbe
                    excedent += tx.montant - absorbe
                } else {
                    excedent = (excedent - tx.montant).coerceAtLeast(BigDecimal.ZERO)
                    // droitsRestants est volontairement inchange.
                }
                excedentMax = maxOf(excedentMax, excedent)
            }

            if (excedentMax.signum() > 0) {
                resultat += ExcedentMensuel(
                    annee = mois.year,
                    mois = mois.monthValue,
                    excedentMax = excedentMax.argent(),
                    penalite = (excedentMax * TAUX_PENALITE_MENSUELLE).argent(),
                )
            }
            mois = mois.plusMonths(1)
        }
        return resultat
    }
}
