package dev.celitracker.data

import dev.celitracker.engine.Compte
import dev.celitracker.engine.PlafondAnnuel
import dev.celitracker.engine.lirePlafondCeliArc
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Une verification par mois au plus : la page ne change qu'une fois par an. */
private const val JOURS_ENTRE_VERIFICATIONS = 30L

sealed interface ResultatVerificationArc {
    /** Plafond lu et enregistre comme proposition, en attente de confirmation. */
    data class Propose(val plafond: PlafondAnnuel) : ResultatVerificationArc

    /** Les plafonds utiles sont deja connus, ou la derniere lecture est trop recente. */
    data object Inutile : ResultatVerificationArc

    /** Page injoignable ou illisible : a l'appelant d'afficher le repli manuel. */
    data class Echec(val raison: String) : ResultatVerificationArc
}

/**
 * Lit la page de l'ARC et propose le plafond CELI qui manque, sans jamais
 * modifier les droits : la proposition est enregistree `confirme = false` et
 * reste inerte tant que l'utilisateur ne l'a pas validee.
 *
 * [telecharger] est injecte pour que la regle et l'ecriture se testent sans
 * reseau ; c'est l'application Android qui fournit le vrai telechargement.
 */
suspend fun Depot.verifierPlafondsArc(
    telecharger: suspend (String) -> String,
    aujourdhui: LocalDate,
    /** Une demande explicite de l'utilisateur ignore la limite d'une par mois. */
    ignorerFrequence: Boolean = false,
): ResultatVerificationArc {
    val plafondsCeli = plafonds().filter { it.compte == Compte.CELI }
    val anneesUtiles = listOf(aujourdhui.year, aujourdhui.year + 1)
    if (anneesUtiles.all { annee -> plafondsCeli.any { it.annee == annee } }) return ResultatVerificationArc.Inutile

    val reglages = reglages()
    val maintenant = aujourdhui.atStartOfDay(ZoneOffset.UTC).toInstant()
    if (!ignorerFrequence && verificationTropRecente(reglages.dateDerniereVerification, maintenant)) {
        return ResultatVerificationArc.Inutile
    }
    if (reglages.urlPageArc.isBlank()) return ResultatVerificationArc.Echec("Aucune adresse de page de l'ARC enregistrée.")

    // La date est notee meme quand la lecture echoue: sans ca, une page en
    // panne serait retelechargee a chaque ouverture de l'application.
    val page = try {
        telecharger(reglages.urlPageArc)
    } catch (e: Exception) {
        enregistrerReglages(reglages.copy(dateDerniereVerification = maintenant))
        return ResultatVerificationArc.Echec(e.message ?: "Page de l'ARC injoignable.")
    }
    enregistrerReglages(reglages.copy(dateDerniereVerification = maintenant))

    val lu = lirePlafondCeliArc(page)
        ?: return ResultatVerificationArc.Echec("La page de l'ARC ne donne plus le plafond sous la forme attendue.")
    if (plafondsCeli.any { it.annee == lu.annee }) return ResultatVerificationArc.Inutile

    enregistrerPlafond(lu)
    return ResultatVerificationArc.Propose(lu)
}

private fun verificationTropRecente(derniere: Instant?, maintenant: Instant): Boolean = derniere != null && Duration.between(derniere, maintenant).toDays() < JOURS_ENTRE_VERIFICATIONS
