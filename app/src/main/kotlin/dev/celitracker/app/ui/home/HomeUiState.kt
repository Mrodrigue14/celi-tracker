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
 * [currentYear] et [currentMonth] sont des ENTREES (l'instant de lecture),
 * labelStep des values derivees: comme le `upTo` des moteurs, ils viennent du
 * ViewModel pour que cet state reste une fonction pure de ses champs, testable
 * sans horloge.
 *
 * Toute value labelled ailleurs est une propriete calculee, jamais un champ
 * du constructeur: un `copy()` ne doit jamais pouvoir produire un state
 * incoherent entre les listes et un total qui en derivait.
 */
data class HomeUiState(
    val profile: Profile?,
    val currentYear: Int,
    val currentMonth: Int,
    val tfsaRoom: List<TfsaYear> = emptyList(),
    val fhsaRoom: List<FhsaYear> = emptyList(),
    val tfsaExcesses: List<MonthlyExcess> = emptyList(),
    /**
     * Faux tant que la database n'a labelStep repondu. Sans lui, « aucun profile » et
     * « labelStep encore parsed » se confondaient, et l'ecran d'home vide clignotait
     * a chaque opening.
     */
    val loaded: Boolean = true,
) {
    val hasProfile: Boolean get() = profile != null

    val tfsaCurrentYear: TfsaYear? get() = tfsaRoom.find { it.year == currentYear }

    val fhsaCurrentYear: FhsaYear? get() = fhsaRoom.find { it.year == currentYear }

    val currentTfsaExcess: MonthlyExcess? get() =
        tfsaExcesses.find { it.year == currentYear && it.month == currentMonth }

    /**
     * null si aucun FHSA ouvert. Le moteur FHSA est explicitement
     * separe du TFSA: aucune penalty de sur-cotisation n'y est exposee ici,
     * `Overcontribution.tfsaExcesses` ne couvre que le TFSA.
     */
    val fhsaParticipationDeadline: LocalDate? get() = profile?.let(FhsaEngine::participationPeriodEnd)

    /** Inconnue si un limit manque: les room sont alors sous-estimes, l'alerte serait fausse. */
    val tfsaUsage: Usage? get() =
        if (tfsaRoom.any { it.limitMissing }) {
            null
        } else {
            tfsaCurrentYear?.let { Usage(room = it.startRoom, contributed = it.deposits) }
        }

    val fhsaUsage: Usage? get() = fhsaCurrentYear?.let { Usage(room = it.yearRoom, contributed = it.deposits) }

    val fhsaRemainingRoom: BigDecimal? get() = fhsaCurrentYear?.let { it.yearRoom - it.deposits }

    /** Part des room de l'year deja cotisee, pour l'anneau de l'home. */
    val tfsaUsedFraction: Float? get() = tfsaCurrentYear?.let { fraction(it.deposits, it.startRoom) }

    val fhsaUsedFraction: Float? get() = fhsaCurrentYear?.let { fraction(it.deposits, it.yearRoom) }
}

/**
 * Seul endroit ou un amount devient un Float: pour dessiner un cra, jamais
 * pour un calcul de room. Superieure a 1 en cas de sur-cotisation.
 */
private fun fraction(part: BigDecimal, whole: BigDecimal): Float? = if (whole.signum() <= 0) null else part.divide(whole, 4, RoundingMode.HALF_UP).toFloat()
