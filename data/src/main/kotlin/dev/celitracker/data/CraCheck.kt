package dev.celitracker.data

import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.isValidCraPageUrl
import dev.celitracker.engine.readTfsaLimitFromCraPage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** At most one check per month: the page only changes once a year. */
private const val DAYS_BETWEEN_CHECKS = 30L

sealed interface CraCheckResult {
    /** Limit parsed and saved as a proposal, awaiting confirmation. */
    data class Proposed(val limit: AnnualLimit) : CraCheckResult

    /** The needed limits are already known, or the last check is too recent. */
    data object NotNeeded : CraCheckResult

    /** Page unreachable or unreadable: the caller must show the manual fallback. */
    data class Failed(val reason: CraFailureReason) : CraCheckResult
}

/**
 * Reads the CRA page and proposes the missing TFSA limit, without ever
 * modifying contribution room: the proposal is saved with `confirmed = false`
 * and stays inert until the user has validated it.
 *
 * [download] is injected so the rule and the write path can be tested
 * without network access; the Android app supplies the real download.
 */
suspend fun Repository.checkCraLimits(
    download: suspend (String) -> String,
    today: LocalDate,
    /** An explicit request from the user bypasses the once-a-month limit. */
    ignoreFrequency: Boolean = false,
): CraCheckResult {
    val tfsaLimits = limits().filter { it.account == Account.TFSA }
    val relevantYears = listOf(today.year, today.year + 1)
    if (relevantYears.all { year -> tfsaLimits.any { it.year == year } }) return CraCheckResult.NotNeeded

    val settings = settings()
    val now = today.atStartOfDay(ZoneOffset.UTC).toInstant()
    if (!ignoreFrequency && checkedRecently(settings.lastCheckDate, now)) {
        return CraCheckResult.NotNeeded
    }
    if (settings.craPageUrl.isBlank()) return CraCheckResult.Failed(CraFailureReason.MISSING_ADDRESS)

    // The date is recorded even when the read fails: otherwise a broken page
    // would be re-downloaded every time the app opens.
    val page = try {
        download(settings.craPageUrl)
    } catch (e: Exception) {
        recordCraCheck(now)
        return CraCheckResult.Failed(CraFailureReason.PAGE_UNREACHABLE)
    }
    recordCraCheck(now)

    val parsed = readTfsaLimitFromCraPage(page)
        ?: return CraCheckResult.Failed(CraFailureReason.UNEXPECTED_FORMAT)
    if (tfsaLimits.any { it.year == parsed.year }) return CraCheckResult.NotNeeded

    saveLimit(parsed)
    return CraCheckResult.Proposed(parsed)
}

private fun checkedRecently(latest: Instant?, now: Instant): Boolean = latest != null && Duration.between(latest, now).toDays() < DAYS_BETWEEN_CHECKS

sealed interface CraAddressResult {
    data object Saved : CraAddressResult

    data class Rejected(val reason: AddressRejectionReason) : CraAddressResult
}

/**
 * Only saves a new address after trying it: its page must yield the TFSA
 * limit. Otherwise the address already in place, the last one that worked,
 * stays as is, and the link to the CRA is never lost.
 */
suspend fun Repository.changeCraPageUrl(url: String, download: suspend (String) -> String): CraAddressResult {
    if (!isValidCraPageUrl(url)) {
        return CraAddressResult.Rejected(AddressRejectionReason.OUTSIDE_CANADA)
    }
    val page = try {
        download(url)
    } catch (e: Exception) {
        return CraAddressResult.Rejected(AddressRejectionReason.PAGE_UNREACHABLE)
    }
    if (readTfsaLimitFromCraPage(page) == null) {
        return CraAddressResult.Rejected(AddressRejectionReason.NO_LIMIT_FOUND)
    }
    saveSettings(settings().copy(craPageUrl = url))
    return CraAddressResult.Saved
}
