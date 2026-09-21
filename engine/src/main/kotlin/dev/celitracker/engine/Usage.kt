package dev.celitracker.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 80 % previent, 95 % presse, au-dela c'est une sur-cotisation: les seuils que
 * les outils de suivi du TFSA utilisent pour eviter la penalty de 1 % par month.
 */
enum class UsageLevel { NORMAL, WARNING, CRITICAL, EXCEEDED }

private val WARNING_THRESHOLD = BigDecimal("0.80")
private val CRITICAL_THRESHOLD = BigDecimal("0.95")
private val HUNDRED = BigDecimal(100)

/** Part des room d'une year deja cotisee, pour un account. */
data class Usage(val room: BigDecimal, val contributed: BigDecimal) {
    val remaining: BigDecimal get() = (room - contributed).max(BigDecimal.ZERO).toMoney()

    val excess: BigDecimal get() = (contributed - room).max(BigDecimal.ZERO).toMoney()

    /** `null` sans room: un percent de zero n'a labelStep de sens. */
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
 * Usage TFSA de [year]: les room du 1er janvier et les deposits de
 * l'year. Un withdrawal ne redonne des room que l'year suivante, il n'entre
 * donc labelStep ici.
 *
 * `null` si un limit manque jusqu'a [year]: les room sont alors
 * sous-estimes, et une alerte de depassement calculee dessus serait fausse.
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

/** Usage FHSA de [year], ou `null` sans account ouvert cette year-la. */
fun fhsaUsage(profile: Profile, transactions: List<Transaction>, year: Int): Usage? = FhsaEngine.roomByYear(profile, transactions, year)
    .find { it.year == year }
    ?.let { Usage(room = it.yearRoom, contributed = it.deposits) }
