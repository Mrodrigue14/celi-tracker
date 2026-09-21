package dev.celitracker.engine

import java.math.BigDecimal

/**
 * The CRA page states the current year's limit in one sentence:
 * "Le plafond de cotisation à un compte d'épargne libre d'impôt (CELI)
 * pour 2026 est de 7 000 $." Both the year and the amount are each
 * wrapped in a `<span class="nowrap">`, so parsing works on the text,
 * never on raw HTML.
 */
private val LIMIT_PATTERN = Regex(
    """plafond de cotisation.{0,200}?pour (\d{4}).{0,40}?est de ([\d ]+) ?\$""",
    RegexOption.IGNORE_CASE,
)

/**
 * A valid address is https and points to canada.ca. Allowing any address
 * would let a limit be read from an unknown source, when the field only
 * exists to follow a reorganization of the CRA site.
 */
fun isValidCraPageUrl(url: String): Boolean {
    val address = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    val host = address.host ?: return false
    return address.scheme == "https" && (host == "canada.ca" || host.endsWith(".canada.ca"))
}

private val HTML_TAG = Regex("<[^>]*>")

private val WHITESPACE = Regex("\\s+")

/**
 * Extracts the TFSA limit announced on the CRA page, or `null` if the
 * page does not state it in the expected form.
 *
 * There is no half result: without both a readable year AND a positive
 * amount, the function returns `null` and the caller falls back to
 * manual entry. A guessed amount would be wrong and plausible, the worst
 * of both worlds for tracking contribution room.
 *
 * The returned limit has `confirmed = false`: it is a proposal that the
 * user must validate before it enters the calculation.
 */
fun readTfsaLimitFromCraPage(html: String): AnnualLimit? {
    val text = html
        .replace(HTML_TAG, " ")
        .replace("&nbsp;", " ")
        // Non-breaking spaces, regular or narrow: the CRA uses them as thousands separators.
        .replace(' ', ' ')
        .replace(' ', ' ')
        .replace(WHITESPACE, " ")

    val match = LIMIT_PATTERN.find(text) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val amount = match.groupValues[2].replace(" ", "").toBigDecimalOrNull() ?: return null
    if (amount <= BigDecimal.ZERO) return null

    return AnnualLimit(account = Account.TFSA, year = year, amount = amount.toMoney(), confirmed = false)
}
