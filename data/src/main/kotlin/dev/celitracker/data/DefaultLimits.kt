package dev.celitracker.data

import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.PUBLISHED_TFSA_LIMITS

/**
 * Writes the TFSA limits published by the CRA that are still missing from
 * the database. A year already saved is never overwritten: a manual
 * correction remains the source of truth for the user.
 *
 * They are saved confirmed: they come from an official table transcribed
 * into the repository, not from an automatic read whose result still needs
 * validation.
 */
suspend fun Repository.seedPublishedLimits() {
    val knownYears = limits().filter { it.account == Account.TFSA }.map { it.year }.toSet()
    saveLimits(
        PUBLISHED_TFSA_LIMITS
            .filterKeys { it !in knownYears }
            .map { (year, amount) -> AnnualLimit(account = Account.TFSA, year = year, amount = amount, confirmed = true) },
    )
}
