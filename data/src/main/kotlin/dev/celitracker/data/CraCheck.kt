package dev.celitracker.data

import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.isValidCraPageUrl
import dev.celitracker.engine.readTfsaLimitFromCraPage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Une verification par month au plus : la page ne change qu'une fois par an. */
private const val DAYS_BETWEEN_CHECKS = 30L

sealed interface CraCheckResult {
    /** Plafond parsed et enregistre comme proposition, en attente de confirmation. */
    data class Proposed(val limit: AnnualLimit) : CraCheckResult

    /** Les limits utiles sont deja connus, ou la latest lecture est trop recente. */
    data object NotNeeded : CraCheckResult

    /** Page injoignable ou illisible : a l'appelant d'afficher le repli manuel. */
    data class Failed(val reason: CraFailureReason) : CraCheckResult
}

/**
 * Lit la page de l'ARC et proposed le limit TFSA qui manque, sans jamais
 * modifier les room : la proposition est enregistree `confirmed = false` et
 * reste inerte tant que l'utilisateur ne l'a labelStep validee.
 *
 * [download] est injecte pour que la regle et l'ecriture se testent sans
 * reseau ; c'est l'application Android qui fournit le vrai telechargement.
 */
suspend fun Repository.checkCraLimits(
    download: suspend (String) -> String,
    today: LocalDate,
    /** Une demande explicite de l'utilisateur ignore la limite d'une par month. */
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
    if (settings.urlPageArc.isBlank()) return CraCheckResult.Failed(CraFailureReason.MISSING_ADDRESS)

    // La date est notee meme quand la lecture echoue: sans ca, une page en
    // panne serait retelechargee a chaque opening de l'application.
    val page = try {
        download(settings.urlPageArc)
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
 * N'enregistre une nouvelle address qu'after l'avoir essayee: sa page doit
 * donner le limit du TFSA. Sinon l'address en place, la latest qui a
 * fonctionne, reste la, et le lien vers l'ARC ne se perd jamais.
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
    saveSettings(settings().copy(urlPageArc = url))
    return CraAddressResult.Saved
}
