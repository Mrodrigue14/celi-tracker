package dev.celitracker.engine

import java.math.BigDecimal

/** A [limitMissing] year counts as zero: room is underestimated rather than overestimated. */
data class TfsaYear(
    val year: Int,
    val limit: BigDecimal,
    val startRoom: BigDecimal,
    val deposits: BigDecimal,
    val withdrawals: BigDecimal,
    val endRoom: BigDecimal,
    val limitMissing: Boolean,
)

/** Never merged with [FhsaEngine]: only a TFSA withdrawal restores room. */
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

            // Last year's withdrawals come back on January 1; this year's do not count yet.
            val startRoom = previousEndRoom + limit + previousWithdrawals

            // No coerceAtLeast(ZERO): a negative balance is the over-contribution and must carry over
            // (the original spreadsheet's MAX(..., 0) erased it).
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
