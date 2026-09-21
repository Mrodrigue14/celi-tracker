package dev.celitracker.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 80% warns, 95% is urgent, beyond that it's an over-contribution: the
 * thresholds TFSA tracking tools use to avoid the 1% per month penalty.
 */
enum class UsageLevel { NORMAL, WARNING, CRITICAL, EXCEEDED }

private val WARNING_THRESHOLD = BigDecimal("0.80")
private val CRITICAL_THRESHOLD = BigDecimal("0.95")
private val HUNDRED = BigDecimal(100)

/** Share of a year's room already contributed, for one account. */
data class Usage(val room: BigDecimal, val contributed: BigDecimal) {
    val remaining: BigDecimal get() = (room - contributed).max(BigDecimal.ZERO).toMoney()

    val excess: BigDecimal get() = (contributed - room).max(BigDecimal.ZERO).toMoney()

    /** `null` with no room: a percentage of zero would not make sense. */
    val percent: Int? get() =
        if (room.signum() <= 0) null else contributed.multiply(HUNDRED).divide(room, 0, RoundingMode.HALF_UP).toInt()

    val level: UsageLevel get() = when {
        contributed > room -> UsageLevel.EXCEEDED
        room.signum() <= 0 -> UsageLevel.NORMAL
        contributed >= room * CRITICAL_THRESHOLD -> UsageLevel.CRITICAL
        contributed >= room * WARNING_THRESHOLD -> UsageLevel.WARNING
        else -> UsageLevel.NORMAL
    }

    fun withDeposit(amount: BigDecimal): Usage = copy(contributed = contributed + amount)
}

/**
 * TFSA usage for [year]: the January 1st room and the year's deposits.
 * A withdrawal only restores room the following year, so it does not
 * factor in here.
 *
 * `null` if a limit is missing up to [year]: room would then be
 * underestimated, and an over-contribution alert based on it would be
 * wrong.
 */
fun tfsaUsage(
    profile: Profile,
    limits: List<AnnualLimit>,
    transactions: List<Transaction>,
    year: Int,
): Usage? {
    val rows = TfsaEngine.roomByYear(profile, limits, transactions, year)
    if (rows.any { it.limitMissing }) return null
    return rows.find { it.year == year }?.let { Usage(room = it.startRoom, contributed = it.deposits) }
}

/** FHSA usage for [year], or `null` if no account was open that year. */
fun fhsaUsage(profile: Profile, transactions: List<Transaction>, year: Int): Usage? = FhsaEngine.roomByYear(profile, transactions, year)
    .find { it.year == year }
    ?.let { Usage(room = it.yearRoom, contributed = it.deposits) }
