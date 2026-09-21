package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate

data class FhsaYear(
    val year: Int,
    val carryForwardIn: BigDecimal,
    val yearRoom: BigDecimal,
    val deposits: BigDecimal,
    val withdrawals: BigDecimal,
    val carryForwardOut: BigDecimal,
    val lifetimeLimitLeft: BigDecimal,
)

/**
 * FHSA engine. Deliberately kept separate from [TfsaEngine]: the two
 * regimes diverge on every axis, and reusing the TFSA's room-restoration
 * path for the FHSA is the most likely correctness bug in this project.
 *
 * The three limits are fixed by law and are NOT indexed: unlike the
 * TFSA, there is nothing to fetch from the CRA site.
 */
object FhsaEngine {

    val ANNUAL_LIMIT: BigDecimal = BigDecimal("8000").toMoney()

    /**
     * Carry-forward limit, PER YEAR OF ARRIVAL. The carry-forward does
     * not accumulate: someone who never contributes sees their annual
     * limit stabilize at 16000 (8000 + 8000), not grow by 8000 every
     * year.
     */
    val MAX_CARRY_FORWARD: BigDecimal = BigDecimal("8000").toMoney()

    val LIFETIME_LIMIT: BigDecimal = BigDecimal("40000").toMoney()

    fun roomByYear(
        profile: Profile,
        transactions: List<Transaction>,
        upTo: Int,
    ): List<FhsaYear> {
        // Accumulation starts when the account is OPENED, not at age 18.
        val opening = profile.fhsaOpeningDate ?: return emptyList()
        val fhsaTransactions = transactions.filter { it.account == Account.FHSA }

        val result = mutableListOf<FhsaYear>()
        var carryForwardIn = BigDecimal.ZERO
        var cumulativeContributions = BigDecimal.ZERO

        for (year in opening.year..upTo) {
            val deposits = sumTransactions(fhsaTransactions, year, TransactionType.DEPOSIT)
            val withdrawals = sumTransactions(fhsaTransactions, year, TransactionType.WITHDRAWAL)

            val lifetimeLeftBefore = (LIFETIME_LIMIT - cumulativeContributions)
                .coerceAtLeast(BigDecimal.ZERO)
            // The inner min() is redundant as long as carryForwardIn is
            // already bounded upstream, but it makes the invariant explicit
            // rather than implicit: the carry-forward does not accumulate,
            // and that is the regime's trap.
            val usableCarryForward = minOf(carryForwardIn, MAX_CARRY_FORWARD)
            val yearRoom = minOf(ANNUAL_LIMIT + usableCarryForward, lifetimeLeftBefore)

            // min(..., MAX_CARRY_FORWARD), NOT an accumulation: that is the
            // whole difference with the TFSA and the RRSP.
            val carryForwardOut = minOf(
                (yearRoom - deposits).coerceAtLeast(BigDecimal.ZERO),
                MAX_CARRY_FORWARD,
            )

            cumulativeContributions += deposits

            result += FhsaYear(
                year = year,
                carryForwardIn = carryForwardIn.toMoney(),
                yearRoom = yearRoom.toMoney(),
                deposits = deposits.toMoney(),
                // Recorded for balance display, but never enters any room
                // calculation: an FHSA withdrawal never restores anything.
                withdrawals = withdrawals.toMoney(),
                carryForwardOut = carryForwardOut.toMoney(),
                lifetimeLimitLeft = (LIFETIME_LIMIT - cumulativeContributions)
                    .coerceAtLeast(BigDecimal.ZERO).toMoney(),
            )

            carryForwardIn = carryForwardOut
        }
        return result
    }

    /**
     * End of the maximum participation period: December 31 of the year
     * in which the FIRST of the following three events occurs.
     *
     *   1. the 15th anniversary of the earliest FHSA's opening
     *   2. the holder's 71st birthday
     *   3. the year following the earliest qualifying withdrawal
     *
     * Branch 3 is NOT implemented: it requires distinguishing a
     * qualifying withdrawal (a first home purchase) from an ordinary
     * one, which the model does not track. The real deadline can
     * therefore be earlier than the one returned here. This exclusion
     * is deliberate and documented in the spec.
     */
    fun participationPeriodEnd(profile: Profile): LocalDate? {
        val opening = profile.fhsaOpeningDate ?: return null
        val fifteenthYear = opening.year + 15
        val age71Year = profile.birthYear + 71
        return LocalDate.of(minOf(fifteenthYear, age71Year), 12, 31)
    }
}
