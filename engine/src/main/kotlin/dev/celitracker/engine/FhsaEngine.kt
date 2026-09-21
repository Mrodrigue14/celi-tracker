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

/** Never shares room-restoration logic with [TfsaEngine]: an FHSA withdrawal restores nothing. */
object FhsaEngine {

    val ANNUAL_LIMIT: BigDecimal = BigDecimal("8000").toMoney()

    /** Caps each year's carry-forward instead of accumulating: never contributing levels annual room off at 16000. */
    val MAX_CARRY_FORWARD: BigDecimal = BigDecimal("8000").toMoney()

    val LIFETIME_LIMIT: BigDecimal = BigDecimal("40000").toMoney()

    fun roomByYear(
        profile: Profile,
        transactions: List<Transaction>,
        upTo: Int,
    ): List<FhsaYear> {
        // Room accrues from the opening date, not from age 18.
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
            val usableCarryForward = minOf(carryForwardIn, MAX_CARRY_FORWARD)
            val yearRoom = minOf(ANNUAL_LIMIT + usableCarryForward, lifetimeLeftBefore)

            // Capped, not accumulated, unlike the TFSA.
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
                // Display only: an FHSA withdrawal never restores room.
                withdrawals = withdrawals.toMoney(),
                carryForwardOut = carryForwardOut.toMoney(),
                lifetimeLimitLeft = (LIFETIME_LIMIT - cumulativeContributions)
                    .coerceAtLeast(BigDecimal.ZERO).toMoney(),
            )

            carryForwardIn = carryForwardOut
        }
        return result
    }

    /** Ignores the "year after a qualifying withdrawal" end condition, which the model cannot detect: the real deadline may be earlier. */
    fun participationPeriodEnd(profile: Profile): LocalDate? {
        val opening = profile.fhsaOpeningDate ?: return null
        val fifteenthYear = opening.year + 15
        val age71Year = profile.birthYear + 71
        return LocalDate.of(minOf(fifteenthYear, age71Year), 12, 31)
    }
}
