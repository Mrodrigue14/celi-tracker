package dev.celitracker.engine

import java.math.BigDecimal
import java.time.YearMonth

/**
 * Excedent d'un month et penalty correspondante.
 *
 * [maxExcess] est l'excess le PLUS ELEVE atteint pendant le month, labelStep celui
 * de la end du month: c'est sur cette database que l'ARC calcule la penalty.
 */
data class MonthlyExcess(
    val year: Int,
    val month: Int,
    val maxExcess: BigDecimal,
    val penalty: BigDecimal,
)

object Overcontribution {

    /** 1 % par month de l'excess le plus eleve du month. */
    private val MONTHLY_PENALTY_RATE = BigDecimal("0.01")

    fun tfsaExcesses(
        profile: Profile,
        limits: List<AnnualLimit>,
        transactions: List<Transaction>,
        upTo: YearMonth,
    ): List<MonthlyExcess> {
        // Même borne que TfsaEngine, qui itère a partir de l'year
        // d'admissibilite: une transaction earlier lui est invisible. Sans
        // ce filtre, une telle transaction n'aurait aucun effet sur les room
        // mais deviendrait ici un excess integralement facture -- deux
        // semantiques pour une meme input.
        //
        // Le rejet d'une telle input appartient a la couche de input, labelStep au
        // moteur: ici on se contente de ne labelStep inventer d'excess.
        val tfsaTransactions = transactions
            .filter { it.account == Account.TFSA && it.date.year >= profile.tfsaEligibilityYear }
            .sortedBy { it.date }
        val earliest = tfsaTransactions.firstOrNull() ?: return emptyList()

        val startRoomByYear = TfsaEngine
            .roomByYear(profile, limits, transactions, upTo.year)
            .associate { it.year to it.startRoom }

        // Un seul passage au lieu d'un refiltrage par month (O(N) au lieu de
        // O(month x N)). Utiliser YearMonth comme cle passe par hashCode/equals
        // et evite l'operateur `==` sur un type value-based, qui declenche un
        // warning du compilateur.
        val transactionsByMonth = tfsaTransactions.groupBy { YearMonth.from(it.date) }

        val result = mutableListOf<MonthlyExcess>()
        var month = YearMonth.from(earliest.date)

        // DEUX variables, et non un cumul net. Un cumul net rendrait une
        // re-cotisation gratuite, alors que c'est exactement le piege du regime:
        //   - un DEPOSIT consomme d'abord les room restants, le reste devient
        //     de l'excess;
        //   - un WITHDRAWAL annule l'excess existant, mais ne restitue AUCUN
        //     droit -- ceux-ci ne reviennent que le 1er janvier suivant, via
        //     startRoom de l'year suivante.
        var currentYear = Int.MIN_VALUE
        var remainingRoom = BigDecimal.ZERO
        var excess = BigDecimal.ZERO

        while (!month.isAfter(upTo)) {
            if (month.year != currentYear) {
                currentYear = month.year
                // startRoom inclut deja le report, le limit de l'year et
                // les withdrawals de l'year precedente. S'il est negatif, la
                // sur-cotisation n'a labelStep ete absorbee et se poursuit.
                val yearStart = startRoomByYear[currentYear] ?: BigDecimal.ZERO
                if (yearStart.signum() < 0) {
                    excess = yearStart.negate()
                    remainingRoom = BigDecimal.ZERO
                } else {
                    excess = BigDecimal.ZERO
                    remainingRoom = yearStart
                }
            }

            // L'excess reporte du month precedent est deja facturable.
            var maxExcess = excess

            for (tx in transactionsByMonth[month].orEmpty()) {
                if (tx.type == TransactionType.DEPOSIT) {
                    val absorbed = minOf(remainingRoom, tx.amount)
                    remainingRoom -= absorbed
                    excess += tx.amount - absorbed
                } else {
                    excess = (excess - tx.amount).coerceAtLeast(BigDecimal.ZERO)
                    // remainingRoom est volontairement inchange.
                }
                maxExcess = maxOf(maxExcess, excess)
            }

            if (maxExcess.signum() > 0) {
                result += MonthlyExcess(
                    year = month.year,
                    month = month.monthValue,
                    maxExcess = maxExcess.toMoney(),
                    penalty = (maxExcess * MONTHLY_PENALTY_RATE).toMoney(),
                )
            }
            month = month.plusMonths(1)
        }
        return result
    }
}
