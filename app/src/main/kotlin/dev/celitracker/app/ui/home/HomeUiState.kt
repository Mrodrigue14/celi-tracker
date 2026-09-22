package dev.celitracker.app.ui.home

import dev.celitracker.engine.CraSnapshot
import dev.celitracker.engine.FhsaEngine
import dev.celitracker.engine.FhsaYear
import dev.celitracker.engine.MonthlyExcess
import dev.celitracker.engine.Profile
import dev.celitracker.engine.SnapshotComparison
import dev.celitracker.engine.TfsaYear
import dev.celitracker.engine.Usage
import dev.celitracker.engine.fhsaSnapshotComparison
import dev.celitracker.engine.fhsaUsage
import dev.celitracker.engine.tfsaSnapshotComparison
import dev.celitracker.engine.tfsaUsage
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** [currentYear] and [currentMonth] are inputs, so the state stays testable without a clock. */
data class HomeUiState(
    val profile: Profile?,
    val currentYear: Int,
    val currentMonth: Int,
    val tfsaRoom: List<TfsaYear> = emptyList(),
    val fhsaRoom: List<FhsaYear> = emptyList(),
    val tfsaExcesses: List<MonthlyExcess> = emptyList(),
    val craSnapshots: List<CraSnapshot> = emptyList(),
    /** False until the database has answered, or the empty home screen flashes on every open. */
    val loaded: Boolean = true,
) {
    val hasProfile: Boolean get() = profile != null

    val tfsaCurrentYear: TfsaYear? get() = tfsaRoom.find { it.year == currentYear }

    val fhsaCurrentYear: FhsaYear? get() = fhsaRoom.find { it.year == currentYear }

    val currentTfsaExcess: MonthlyExcess? get() =
        tfsaExcesses.find { it.year == currentYear && it.month == currentMonth }

    val tfsaCraComparison: SnapshotComparison? get() = tfsaSnapshotComparison(craSnapshots, tfsaRoom)

    val fhsaCraComparison: SnapshotComparison? get() = fhsaSnapshotComparison(craSnapshots, fhsaRoom)

    val fhsaParticipationDeadline: LocalDate? get() = profile?.let(FhsaEngine::participationPeriodEnd)

    val tfsaUsage: Usage? get() = tfsaUsage(tfsaRoom, currentYear)

    val fhsaUsage: Usage? get() = fhsaUsage(fhsaRoom, currentYear)

    val fhsaRemainingRoom: BigDecimal? get() = fhsaCurrentYear?.remainingRoom

    val tfsaUsedFraction: Float? get() = tfsaCurrentYear?.let { fraction(it.deposits, it.startRoom) }

    val fhsaUsedFraction: Float? get() = fhsaCurrentYear?.let { fraction(it.deposits, it.yearRoom) }
}

/** Only for drawing an arc, never for room math. Above 1 on overcontribution. */
private fun fraction(part: BigDecimal, whole: BigDecimal): Float? = if (whole.signum() <= 0) null else part.divide(whole, 4, RoundingMode.HALF_UP).toFloat()
