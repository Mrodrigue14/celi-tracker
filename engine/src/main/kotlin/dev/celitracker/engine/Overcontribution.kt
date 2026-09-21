package dev.celitracker.engine

import java.math.BigDecimal
import java.time.YearMonth

/** [maxExcess] is the highest excess reached during the month, not the month-end one: the CRA bills on it. */
data class MonthlyExcess(
    val year: Int,
    val month: Int,
    val maxExcess: BigDecimal,
    val penalty: BigDecimal,
)

object Overcontribution {

    private val MONTHLY_PENALTY_RATE = BigDecimal("0.01")

    fun tfsaExcesses(
        profile: Profile,
        limits: List<AnnualLimit>,
        transactions: List<Transaction>,
        upTo: YearMonth,
    ): List<MonthlyExcess> {
        // Same lower bound as TfsaEngine, which cannot see earlier transactions: without it they would be
        // billed as an excess while having no effect on room.
        val tfsaTransactions = transactions
            .filter { it.account == Account.TFSA && it.date.year >= profile.tfsaEligibilityYear }
            .sortedBy { it.date }
        val earliest = tfsaTransactions.firstOrNull() ?: return emptyList()

        val startRoomByYear = TfsaEngine
            .roomByYear(profile, limits, transactions, upTo.year)
            .associate { it.year to it.startRoom }

        val transactionsByMonth = tfsaTransactions.groupBy { YearMonth.from(it.date) }

        val result = mutableListOf<MonthlyExcess>()
        var month = YearMonth.from(earliest.date)

        // Two variables, not a net total, which would make re-contributing free: a withdrawal cancels
        // excess but restores no room until the next January 1.
        var currentYear = Int.MIN_VALUE
        var remainingRoom = BigDecimal.ZERO
        var excess = BigDecimal.ZERO

        while (!month.isAfter(upTo)) {
            if (month.year != currentYear) {
                currentYear = month.year
                // A negative startRoom is an excess not yet absorbed by this year's limit.
                val yearStart = startRoomByYear[currentYear] ?: BigDecimal.ZERO
                if (yearStart.signum() < 0) {
                    excess = yearStart.negate()
                    remainingRoom = BigDecimal.ZERO
                } else {
                    excess = BigDecimal.ZERO
                    remainingRoom = yearStart
                }
            }

            var maxExcess = excess

            for (tx in transactionsByMonth[month].orEmpty()) {
                if (tx.type == TransactionType.DEPOSIT) {
                    val absorbed = minOf(remainingRoom, tx.amount)
                    remainingRoom -= absorbed
                    excess += tx.amount - absorbed
                } else {
                    excess = (excess - tx.amount).coerceAtLeast(BigDecimal.ZERO)
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
