package dev.celitracker.engine

import java.math.BigDecimal
import java.time.YearMonth

/**
 * A month's excess and its corresponding penalty.
 *
 * [maxExcess] is the HIGHEST excess reached during the month, not the
 * one at month end: this is the figure the CRA bases the penalty on.
 */
data class MonthlyExcess(
    val year: Int,
    val month: Int,
    val maxExcess: BigDecimal,
    val penalty: BigDecimal,
)

object Overcontribution {

    /** 1% per month of the month's highest excess. */
    private val MONTHLY_PENALTY_RATE = BigDecimal("0.01")

    fun tfsaExcesses(
        profile: Profile,
        limits: List<AnnualLimit>,
        transactions: List<Transaction>,
        upTo: YearMonth,
    ): List<MonthlyExcess> {
        // Same bound as TfsaEngine, which iterates from the eligibility
        // year: an earlier transaction is invisible to it. Without this
        // filter, such a transaction would have no effect on room but
        // would become a fully billed excess here -- two semantics for
        // the same input.
        //
        // Rejecting such input belongs to the input layer, not the
        // engine: here we just avoid inventing an excess.
        val tfsaTransactions = transactions
            .filter { it.account == Account.TFSA && it.date.year >= profile.tfsaEligibilityYear }
            .sortedBy { it.date }
        val earliest = tfsaTransactions.firstOrNull() ?: return emptyList()

        val startRoomByYear = TfsaEngine
            .roomByYear(profile, limits, transactions, upTo.year)
            .associate { it.year to it.startRoom }

        // A single pass instead of re-filtering per month (O(N) instead
        // of O(month x N)). Using YearMonth as the key goes through
        // hashCode/equals and avoids the `==` operator on a value-based
        // type, which triggers a compiler warning.
        val transactionsByMonth = tfsaTransactions.groupBy { YearMonth.from(it.date) }

        val result = mutableListOf<MonthlyExcess>()
        var month = YearMonth.from(earliest.date)

        // TWO variables, not a net running total. A net total would make
        // re-contributing free, when that is exactly the regime's trap:
        //   - a DEPOSIT first consumes the remaining room, the rest
        //     becomes excess;
        //   - a WITHDRAWAL cancels the existing excess, but restores NO
        //     room -- that only comes back the following January 1st,
        //     via the next year's startRoom.
        var currentYear = Int.MIN_VALUE
        var remainingRoom = BigDecimal.ZERO
        var excess = BigDecimal.ZERO

        while (!month.isAfter(upTo)) {
            if (month.year != currentYear) {
                currentYear = month.year
                // startRoom already includes the carry-forward, the
                // year's limit and the previous year's withdrawals. If
                // it is negative, the over-contribution has not been
                // absorbed and continues.
                val yearStart = startRoomByYear[currentYear] ?: BigDecimal.ZERO
                if (yearStart.signum() < 0) {
                    excess = yearStart.negate()
                    remainingRoom = BigDecimal.ZERO
                } else {
                    excess = BigDecimal.ZERO
                    remainingRoom = yearStart
                }
            }

            // The excess carried over from the previous month is already billable.
            var maxExcess = excess

            for (tx in transactionsByMonth[month].orEmpty()) {
                if (tx.type == TransactionType.DEPOSIT) {
                    val absorbed = minOf(remainingRoom, tx.amount)
                    remainingRoom -= absorbed
                    excess += tx.amount - absorbed
                } else {
                    excess = (excess - tx.amount).coerceAtLeast(BigDecimal.ZERO)
                    // remainingRoom is deliberately left unchanged.
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
