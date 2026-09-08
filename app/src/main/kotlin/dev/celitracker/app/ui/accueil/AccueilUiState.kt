package dev.celitracker.app.ui.accueil

import dev.celitracker.engine.CeliappMoteur
import dev.celitracker.engine.DroitsAnnee
import dev.celitracker.engine.DroitsAnneeCeliapp
import dev.celitracker.engine.ExcedentMensuel
import dev.celitracker.engine.Profil
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
}
