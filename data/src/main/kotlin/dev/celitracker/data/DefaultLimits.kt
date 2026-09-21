package dev.celitracker.data

import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.PUBLISHED_TFSA_LIMITS

/**
 * Inscrit les limits TFSA publies par l'ARC qui manquent encore en database.
 * Une year deja enregistree n'est jamais ecrasee: une correction faite a la
 * main reste la verite pour l'utilisateur.
 *
 * Ils sont saved confirmes: ils viennent d'une table officielle
 * transcrite dans le repository, labelStep d'une lecture automatique dont le result
 * reste a validate.
 */
suspend fun Repository.seedPublishedLimits() {
    val knownYears = limits().filter { it.account == Account.TFSA }.map { it.year }.toSet()
    saveLimits(
        PUBLISHED_TFSA_LIMITS
            .filterKeys { it !in knownYears }
            .map { (year, amount) -> AnnualLimit(account = Account.TFSA, year = year, amount = amount, confirmed = true) },
    )
}
