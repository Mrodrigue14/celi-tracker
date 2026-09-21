package dev.celitracker.app.ui.settings

import dev.celitracker.app.ui.format.toEnteredAmount
import dev.celitracker.app.ui.text.UiText
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.Profile
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private const val MIN_BIRTH_YEAR = 1900

/**
 * Input fields as String (the TextFields' source of truth) rather than
 * already-parsed Int/BigDecimal: without this, partial input ("202")
 * would be lost on every keystroke until it became a valid integer.
 * Validity is a computed property, never a separate field.
 */
data class SettingsUiState(
    val birthYear: String = "",
    val fhsaOpeningDate: String = "",
    val limits: List<AnnualLimit> = emptyList(),
    val newLimitYear: String = "",
    val newLimitAmount: String = "",
    val craPageUrl: String = "",
    val lastCraCheck: Instant? = null,
    val checkInProgress: Boolean = false,
    val craError: UiText? = null,
    val message: UiText? = null,
) {
    /** Read from the CRA website, awaiting confirmation. */
    val proposals: List<AnnualLimit> get() = limits.filter { !it.confirmed }

    val confirmedLimits: List<AnnualLimit> get() = limits.filter { it.confirmed }

    /**
     * Limits from before eligibility don't count toward room: they stay
     * in the database, but collapsed on screen. Without a birth year,
     * everything is relevant.
     */
    val relevantLimits: List<AnnualLimit> get() =
        confirmedLimits.filter { limit -> tfsaEligibilityYear?.let { limit.year >= it } ?: true }

    val earlierLimits: List<AnnualLimit> get() = confirmedLimits - relevantLimits.toSet()

    val validBirthYear: Int? get() =
        birthYear.toIntOrNull()?.takeIf { it in MIN_BIRTH_YEAR..LocalDate.now().year }

    /**
     * Derived from the birth year, never entered directly. The
     * calculation comes from [Profile] so it exists in only one place.
     */
    val tfsaEligibilityYear: Int? get() =
        validBirthYear?.let { Profile(birthYear = it, fhsaOpeningDate = null).tfsaEligibilityYear }

    val validOpeningDate: LocalDate? get() =
        if (fhsaOpeningDate.isBlank()) null else runCatching { LocalDate.parse(fhsaOpeningDate) }.getOrNull()

    /** Empty = no FHSA, valid. Non-empty and unparsable = input error. */
    val invalidOpeningDate: Boolean get() =
        fhsaOpeningDate.isNotBlank() && validOpeningDate == null

    val isProfileValid: Boolean get() = validBirthYear != null && !invalidOpeningDate

    val validNewLimitYear: Int? get() = newLimitYear.toIntOrNull()
    val validNewLimitAmount: BigDecimal? get() = newLimitAmount.toEnteredAmount()

    val isNewLimitValid: Boolean get() =
        validNewLimitYear != null && validNewLimitAmount != null
}
