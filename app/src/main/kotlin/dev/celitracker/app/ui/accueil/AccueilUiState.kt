package dev.celitracker.app.ui.accueil

import dev.celitracker.engine.CeliappMoteur
import dev.celitracker.engine.DroitsAnnee
import dev.celitracker.engine.DroitsAnneeCeliapp
import dev.celitracker.engine.ExcedentMensuel
import dev.celitracker.engine.Profil
import dev.celitracker.engine.Utilisation
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * [anneeCourante] et [moisCourant] sont des ENTREES (l'instant de lecture),
 * pas des valeurs derivees: comme le `jusqua` des moteurs, ils viennent du
 * ViewModel pour que cet etat reste une fonction pure de ses champs, testable
 * sans horloge.
 *
 * Toute valeur affichee ailleurs est une propriete calculee, jamais un champ
 * du constructeur: un `copy()` ne doit jamais pouvoir produire un etat
 * incoherent entre les listes et un total qui en derivait.
 */
data class AccueilUiState(
    val profil: Profil?,
    val anneeCourante: Int,
    val moisCourant: Int,
    val droitsCeli: List<DroitsAnnee> = emptyList(),
    val droitsCeliapp: List<DroitsAnneeCeliapp> = emptyList(),
    val excedentsCeli: List<ExcedentMensuel> = emptyList(),
    /**
     * Faux tant que la base n'a pas repondu. Sans lui, « aucun profil » et
     * « pas encore lu » se confondaient, et l'ecran d'accueil vide clignotait
     * a chaque ouverture.
     */
    val chargementTermine: Boolean = true,
) {
    val profilEnregistre: Boolean get() = profil != null

    val celiAnneeCourante: DroitsAnnee? get() = droitsCeli.find { it.annee == anneeCourante }

    val celiappAnneeCourante: DroitsAnneeCeliapp? get() = droitsCeliapp.find { it.annee == anneeCourante }

    val excedentCeliCourant: ExcedentMensuel? get() =
        excedentsCeli.find { it.annee == anneeCourante && it.mois == moisCourant }

    /**
     * null si aucun CELIAPP ouvert. Le moteur CELIAPP est explicitement
     * separe du CELI: aucune penalite de sur-cotisation n'y est exposee ici,
     * `SurCotisation.excedentsCeli` ne couvre que le CELI.
     */
    val echeanceParticipationCeliapp: LocalDate? get() = profil?.let(CeliappMoteur::finPeriodeParticipation)

    /** Inconnue si un plafond manque: les droits sont alors sous-estimes, l'alerte serait fausse. */
    val utilisationCeli: Utilisation? get() =
        if (droitsCeli.any { it.plafondManquant }) {
            null
        } else {
            celiAnneeCourante?.let { Utilisation(droits = it.droitsDebut, cotise = it.depots) }
        }

    val utilisationCeliapp: Utilisation? get() = celiappAnneeCourante?.let { Utilisation(droits = it.droitsAnnee, cotise = it.depots) }

    val droitsRestantsCeliapp: BigDecimal? get() = celiappAnneeCourante?.let { it.droitsAnnee - it.depots }

    /** Part des droits de l'annee deja cotisee, pour l'anneau de l'accueil. */
    val fractionUtiliseeCeli: Float? get() = celiAnneeCourante?.let { fraction(it.depots, it.droitsDebut) }

    val fractionUtiliseeCeliapp: Float? get() = celiappAnneeCourante?.let { fraction(it.depots, it.droitsAnnee) }
}

/**
 * Seul endroit ou un montant devient un Float: pour dessiner un arc, jamais
 * pour un calcul de droits. Superieure a 1 en cas de sur-cotisation.
 */
private fun fraction(partie: BigDecimal, tout: BigDecimal): Float? = if (tout.signum() <= 0) null else partie.divide(tout, 4, RoundingMode.HALF_UP).toFloat()
