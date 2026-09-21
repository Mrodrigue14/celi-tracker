package dev.celitracker.data

import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.PUBLISHED_TFSA_LIMITS

/** Never overwrites a saved year (a manual correction wins). Seeded limits are confirmed: the table is official. */
suspend fun Repository.seedPublishedLimits() {
    val knownYears = limits().filter { it.account == Account.TFSA }.map { it.year }.toSet()
    saveLimits(
        PUBLISHED_TFSA_LIMITS
            .filterKeys { it !in knownYears }
            .map { (year, amount) -> AnnualLimit(account = Account.TFSA, year = year, amount = amount, confirmed = true) },
    )
}
