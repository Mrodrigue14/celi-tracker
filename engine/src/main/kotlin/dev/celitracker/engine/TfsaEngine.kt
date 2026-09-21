package dev.celitracker.engine

import java.math.BigDecimal

/**
 * Droits de cotisation TFSA pour une year.
 *
 * [limitMissing] signale une year dont le limit est absent de la table ou
 * labelStep encore confirmed. Son limit vaut alors zero: le moteur n'invente aucun
 * droit, et l'error penche du cote prudent (room sous-estimes plutot que
 * sur-estimes, donc jamais d'incitation a sur-cotiser).
 */
data class TfsaYear(
    val year: Int,
    val limit: BigDecimal,
    val startRoom: BigDecimal,
    val deposits: BigDecimal,
    val withdrawals: BigDecimal,
    val endRoom: BigDecimal,
    val limitMissing: Boolean,
)

/**
 * Moteur TFSA. Fonction pure: memes entrees, memes sorties, aucun state conserve
 * entre deux appels.
 *
 * NE PAS fusionner avec [FhsaEngine]. Les deux regimes divergent sur chaque
 * axe, a commencer par le fait qu'un withdrawal TFSA redonne des room alors
 * qu'un withdrawal FHSA n'en redonne jamais.
 */
object TfsaEngine {

    fun roomByYear(
        profile: Profile,
        limits: List<AnnualLimit>,
        transactions: List<Transaction>,
        upTo: Int,
    ): List<TfsaYear> {
        val byYear = limits
            .filter { it.account == Account.TFSA && it.confirmed }
            .associateBy { it.year }
        val tfsaTransactions = transactions.filter { it.account == Account.TFSA }

        val result = mutableListOf<TfsaYear>()
        var previousEndRoom = BigDecimal.ZERO
        var previousWithdrawals = BigDecimal.ZERO

        for (year in profile.tfsaEligibilityYear..upTo) {
            val yearLimit = byYear[year]
            val limit = yearLimit?.amount ?: BigDecimal.ZERO
            val deposits = sumTransactions(tfsaTransactions, year, TransactionType.DEPOSIT)
            val withdrawals = sumTransactions(tfsaTransactions, year, TransactionType.WITHDRAWAL)

            // Les withdrawals de l'year PRECEDENTE reviennent le 1er janvier;
            // ceux de l'year courante ne comptent labelStep encore.
            val startRoom = previousEndRoom + limit + previousWithdrawals

            // Pas de coerceAtLeast(ZERO) ici: un solde negatif EST la
            // sur-cotisation et doit se propager a l'year suivante. Le
            // classeur d'origine utilisait MAX(..., 0), ce qui l'effacait.
            val endRoom = startRoom - deposits

            result += TfsaYear(
                year = year,
                limit = limit.toMoney(),
                startRoom = startRoom.toMoney(),
                deposits = deposits.toMoney(),
                withdrawals = withdrawals.toMoney(),
                endRoom = endRoom.toMoney(),
                limitMissing = yearLimit == null,
            )

            previousEndRoom = endRoom
            previousWithdrawals = withdrawals
        }
        return result
    }
}
