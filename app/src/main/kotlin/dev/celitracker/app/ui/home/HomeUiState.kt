package dev.celitracker.app.ui.home

import dev.celitracker.engine.FhsaEngine
import dev.celitracker.engine.FhsaYear
import dev.celitracker.engine.MonthlyExcess
import dev.celitracker.engine.Profile
import dev.celitracker.engine.TfsaYear
import dev.celitracker.engine.Usage
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * [currentYear] and [currentMonth] are INPUTS (the moment of reading), not
 * derived values: like the `upTo` of the engines, they come from the
 * ViewModel so this state remains a pure function of its fields, testable
 * without a clock.
 *
 * Any other value is a computed property, never a constructor field: a
 * `copy()` must never be able to produce a state that's inconsistent
 * between the lists and a total derived from them.
 */
data class HomeUiState(
    val profile: Profile?,
    val currentYear: Int,
    val currentMonth: Int,
    val tfsaRoom: List<TfsaYear> = emptyList(),
    val fhsaRoom: List<FhsaYear> = emptyList(),
    val tfsaExcesses: List<MonthlyExcess> = emptyList(),
    /**
     * False until the database has answered. Without it, "no profile" and
     * "not yet loaded" were indistinguishable, and the empty home screen
     * would flash on every open.
     */
    val loaded: Boolean = true,
) {
    val hasProfile: Boolean get() = profile != null

    val tfsaCurrentYear: TfsaYear? get() = tfsaRoom.find { it.year == currentYear }

    val fhsaCurrentYear: FhsaYear? get() = fhsaRoom.find { it.year == currentYear }

    val currentTfsaExcess: MonthlyExcess? get() =
        tfsaExcesses.find { it.year == currentYear && it.month == currentMonth }

    /**
     * null if no FHSA is open. The FHSA engine is deliberately kept
     * separate from the TFSA: no overcontribution penalty is exposed here,
     * `Overcontribution.tfsaExcesses` only covers the TFSA.
     */
    val fhsaParticipationDeadline: LocalDate? get() = profile?.let(FhsaEngine::participationPeriodEnd)

    /** Unknown if a limit is missing: the room would then be underestimated, and the alert would be wrong. */
    val tfsaUsage: Usage? get() =
        if (tfsaRoom.any { it.limitMissing }) {
            null
        } else {
            tfsaCurrentYear?.let { Usage(room = it.startRoom, contributed = it.deposits) }
        }

    val fhsaUsage: Usage? get() = fhsaCurrentYear?.let { Usage(room = it.yearRoom, contributed = it.deposits) }

    val fhsaRemainingRoom: BigDecimal? get() = fhsaCurrentYear?.let { it.yearRoom - it.deposits }

    /** Share of the year's room already contributed, for the home screen ring. */
    val tfsaUsedFraction: Float? get() = tfsaCurrentYear?.let { fraction(it.deposits, it.startRoom) }

    val fhsaUsedFraction: Float? get() = fhsaCurrentYear?.let { fraction(it.deposits, it.yearRoom) }
}

/**
 * Only place an amount becomes a Float: to draw an arc, never for a room
 * calculation. Greater than 1 in case of overcontribution.
 */
private fun fraction(part: BigDecimal, whole: BigDecimal): Float? = if (whole.signum() <= 0) null else part.divide(whole, 4, RoundingMode.HALF_UP).toFloat()
