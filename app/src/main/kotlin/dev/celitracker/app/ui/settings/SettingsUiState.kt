package dev.celitracker.app.ui.settings

import dev.celitracker.app.ui.format.toEnteredAmount
import dev.celitracker.app.ui.format.toEnteredRoom
import dev.celitracker.app.ui.text.UiText
import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.CraSnapshot
import dev.celitracker.engine.Profile
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private const val MIN_BIRTH_YEAR = 1900

/** Fields are Strings so partial input ("202") survives each keystroke. */
data class SettingsUiState(
    val birthYear: String = "",
    val fhsaOpeningDate: String = "",
    val limits: List<AnnualLimit> = emptyList(),
    val newLimitYear: String = "",
    val newLimitAmount: String = "",
    val snapshots: List<CraSnapshot> = emptyList(),
    val snapshotAccount: Account = Account.TFSA,
    val snapshotDate: String = "",
    val snapshotAmount: String = "",
    val craPageUrl: String = "",
    val lastCraCheck: Instant? = null,
    val checkInProgress: Boolean = false,
    val craError: UiText? = null,
    val message: UiText? = null,
) {
    val unconfirmedLimits: List<AnnualLimit> get() = limits.filter { !it.confirmed }

    val confirmedLimits: List<AnnualLimit> get() = limits.filter { it.confirmed }

    /** Limits before eligibility count for nothing: kept in the database, collapsed on screen. */
    val relevantLimits: List<AnnualLimit> get() =
        confirmedLimits.filter { limit -> tfsaEligibilityYear?.let { limit.year >= it } ?: true }

    val earlierLimits: List<AnnualLimit> get() = confirmedLimits - relevantLimits.toSet()

    val validBirthYear: Int? get() =
        birthYear.toIntOrNull()?.takeIf { it in MIN_BIRTH_YEAR..LocalDate.now().year }

    val tfsaEligibilityYear: Int? get() =
        validBirthYear?.let { Profile(birthYear = it, fhsaOpeningDate = null).tfsaEligibilityYear }

    val validOpeningDate: LocalDate? get() =
        if (fhsaOpeningDate.isBlank()) null else runCatching { LocalDate.parse(fhsaOpeningDate) }.getOrNull()

    val invalidOpeningDate: Boolean get() =
        fhsaOpeningDate.isNotBlank() && validOpeningDate == null

    val isProfileValid: Boolean get() = validBirthYear != null && !invalidOpeningDate

    val validNewLimitYear: Int? get() = newLimitYear.toIntOrNull()
    val validNewLimitAmount: BigDecimal? get() = newLimitAmount.toEnteredAmount()

    val isNewLimitValid: Boolean get() =
        validNewLimitYear != null && validNewLimitAmount != null

    /** A future date would stay the latest snapshot until a later one is entered. */
    val validSnapshotDate: LocalDate? get() =
        runCatching { LocalDate.parse(snapshotDate) }.getOrNull()?.takeIf { !it.isAfter(LocalDate.now()) }

    val invalidSnapshotDate: Boolean get() = snapshotDate.isNotBlank() && validSnapshotDate == null

    val validSnapshotAmount: BigDecimal? get() = snapshotAmount.toEnteredRoom()

    val isNewSnapshotValid: Boolean get() = validSnapshotDate != null && validSnapshotAmount != null
}
