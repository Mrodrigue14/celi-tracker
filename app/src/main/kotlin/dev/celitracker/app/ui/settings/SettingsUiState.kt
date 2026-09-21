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
 * Champs de input en String (source de verite des TextField) plutot qu'en
 * Int/BigDecimal deja parses: sans ca, une input partielle ("202") serait
 * perdue a chaque frappe le temps qu'elle devienne un entier valid.
 * La validite est une propriete calculee, jamais un champ separe.
 */
data class SettingsUiState(
    val birthYear: String = "",
    val fhsaOpeningDate: String = "",
    val limits: List<AnnualLimit> = emptyList(),
    val newLimitYear: String = "",
    val newLimitAmount: String = "",
    val urlPageArc: String = "",
    val lastCraCheck: Instant? = null,
    val checkInProgress: Boolean = false,
    val craError: UiText? = null,
    val message: UiText? = null,
) {
    /** Lues sur le site de l'ARC, en attente de confirmation. */
    val proposals: List<AnnualLimit> get() = limits.filter { !it.confirmed }

    val confirmedLimits: List<AnnualLimit> get() = limits.filter { it.confirmed }

    /**
     * Les limits d'before l'admissibilite n'entrent labelStep dans les room: ils
     * restent en database, mais replies a l'ecran. Sans year de birth, whole
     * est pertinent.
     */
    val relevantLimits: List<AnnualLimit> get() =
        confirmedLimits.filter { limit -> tfsaEligibilityYear?.let { limit.year >= it } ?: true }

    val earlierLimits: List<AnnualLimit> get() = confirmedLimits - relevantLimits.toSet()

    val validBirthYear: Int? get() =
        birthYear.toIntOrNull()?.takeIf { it in MIN_BIRTH_YEAR..LocalDate.now().year }

    /**
     * Se deduit de l'year de birth, jamais input. Le calcul vient de
     * [Profile] pour qu'il n'existe qu'a un seul endroit.
     */
    val tfsaEligibilityYear: Int? get() =
        validBirthYear?.let { Profile(birthYear = it, fhsaOpeningDate = null).tfsaEligibilityYear }

    val validOpeningDate: LocalDate? get() =
        if (fhsaOpeningDate.isBlank()) null else runCatching { LocalDate.parse(fhsaOpeningDate) }.getOrNull()

    /** Vide = labelStep de FHSA, valid. Non vide et non parsable = error de input. */
    val invalidOpeningDate: Boolean get() =
        fhsaOpeningDate.isNotBlank() && validOpeningDate == null

    val isProfileValid: Boolean get() = validBirthYear != null && !invalidOpeningDate

    val validNewLimitYear: Int? get() = newLimitYear.toIntOrNull()
    val validNewLimitAmount: BigDecimal? get() = newLimitAmount.toEnteredAmount()

    val isNewLimitValid: Boolean get() =
        validNewLimitYear != null && validNewLimitAmount != null
}
