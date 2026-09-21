package dev.celitracker.engine

import java.math.BigDecimal

// The CRA wraps the year and the amount in separate nowrap spans, so this matches the text, not the HTML.
private val LIMIT_PATTERN = Regex(
    """plafond de cotisation.{0,200}?pour (\d{4}).{0,40}?est de ([\d ]+) ?\$""",
    RegexOption.IGNORE_CASE,
)

fun isValidCraPageUrl(url: String): Boolean {
    val address = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    val host = address.host ?: return false
    return address.scheme == "https" && (host == "canada.ca" || host.endsWith(".canada.ca"))
}

private const val NO_BREAK_SPACE = '\u00A0'

private const val NARROW_NO_BREAK_SPACE = '\u202F'

private val HTML_TAG = Regex("<[^>]*>")

private val WHITESPACE = Regex("\\s+")

/** Null unless both a year and a positive amount are readable: a guessed amount would be plausible and wrong. */
fun readTfsaLimitFromCraPage(html: String): AnnualLimit? {
    val text = html
        .replace(HTML_TAG, " ")
        .replace("&nbsp;", " ")
        .replace(NO_BREAK_SPACE, ' ')
        .replace(NARROW_NO_BREAK_SPACE, ' ')
        .replace(WHITESPACE, " ")

    val match = LIMIT_PATTERN.find(text) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val amount = match.groupValues[2].replace(" ", "").toBigDecimalOrNull() ?: return null
    if (amount <= BigDecimal.ZERO) return null

    return AnnualLimit(account = Account.TFSA, year = year, amount = amount.toMoney(), confirmed = false)
}
