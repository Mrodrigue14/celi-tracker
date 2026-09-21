package dev.celitracker.engine

import java.math.BigDecimal

/**
 * TFSA contribution room for a given year.
 *
 * [limitMissing] flags a year whose limit is absent from the table or
 * not yet confirmed. Its limit is then treated as zero: the engine
 * never invents room, and the error leans on the safe side (room is
 * underestimated rather than overestimated, so there is never an
 * incentive to over-contribute).
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
 * TFSA engine. A pure function: same inputs, same outputs, no state
 * kept between calls.
 *
 * DO NOT merge with [FhsaEngine]. The two regimes diverge on every
 * axis, starting with the fact that a TFSA withdrawal restores room
 * while an FHSA withdrawal never does.
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

            // Withdrawals from the PREVIOUS year come back on January 1st;
            // those from the current year do not count yet.
            val startRoom = previousEndRoom + limit + previousWithdrawals

            // No coerceAtLeast(ZERO) here: a negative balance IS the
            // over-contribution and must carry over to the next year.
            // The original spreadsheet used MAX(..., 0), which erased it.
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
