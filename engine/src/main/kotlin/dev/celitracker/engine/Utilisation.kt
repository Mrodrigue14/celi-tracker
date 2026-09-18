package dev.celitracker.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 80 % previent, 95 % presse, au-dela c'est une sur-cotisation: les seuils que
 * les outils de suivi du CELI utilisent pour eviter la penalite de 1 % par mois.
 */
enum class NiveauUtilisation { NORMAL, ATTENTION, CRITIQUE, DEPASSE }

private val SEUIL_ATTENTION = BigDecimal("0.80")
private val SEUIL_CRITIQUE = BigDecimal("0.95")
private val CENT = BigDecimal(100)

/** Part des droits d'une annee deja cotisee, pour un compte. */
data class Utilisation(val droits: BigDecimal, val cotise: BigDecimal) {
    val restant: BigDecimal get() = (droits - cotise).max(BigDecimal.ZERO).argent()

    val excedent: BigDecimal get() = (cotise - droits).max(BigDecimal.ZERO).argent()

    /** `null` sans droits: un pourcentage de zero n'a pas de sens. */
    val pourcentage: Int? get() =
        if (droits.signum() <= 0) null else cotise.multiply(CENT).divide(droits, 0, RoundingMode.HALF_UP).toInt()

    val niveau: NiveauUtilisation get() = when {
        cotise > droits -> NiveauUtilisation.DEPASSE
        droits.signum() <= 0 -> NiveauUtilisation.NORMAL
        cotise >= droits * SEUIL_CRITIQUE -> NiveauUtilisation.CRITIQUE
        cotise >= droits * SEUIL_ATTENTION -> NiveauUtilisation.ATTENTION
        else -> NiveauUtilisation.NORMAL
    }

    fun avecDepot(montant: BigDecimal): Utilisation = copy(cotise = cotise + montant)
}

/**
 * Utilisation CELI de [annee]: les droits du 1er janvier et les depots de
 * l'annee. Un retrait ne redonne des droits que l'annee suivante, il n'entre
 * donc pas ici.
 *
 * `null` si un plafond manque jusqu'a [annee]: les droits sont alors
 * sous-estimes, et une alerte de depassement calculee dessus serait fausse.
 */
fun utilisationCeli(
    profil: Profil,
    plafonds: List<PlafondAnnuel>,
    transactions: List<Transaction>,
    annee: Int,
): Utilisation? {
    val lignes = CeliMoteur.droitsParAnnee(profil, plafonds, transactions, annee)
    if (lignes.any { it.plafondManquant }) return null
    return lignes.find { it.annee == annee }?.let { Utilisation(droits = it.droitsDebut, cotise = it.depots) }
}

/** Utilisation CELIAPP de [annee], ou `null` sans compte ouvert cette annee-la. */
fun utilisationCeliapp(profil: Profil, transactions: List<Transaction>, annee: Int): Utilisation? = CeliappMoteur.droitsParAnnee(profil, transactions, annee)
    .find { it.annee == annee }
    ?.let { Utilisation(droits = it.droitsAnnee, cotise = it.depots) }
