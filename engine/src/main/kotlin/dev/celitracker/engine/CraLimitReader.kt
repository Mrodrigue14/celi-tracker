package dev.celitracker.engine

import java.math.BigDecimal

/**
 * La page de l'ARC enonce le limit de l'year en cours dans une phrase :
 * « Le limit de cotisation a un account d'epargne libre d'impot (TFSA) pour
 * 2026 est de 7 000 $. » L'year et le amount y sont enveloppes chacun dans
 * un `<span class="nowrap">`, donc la lecture se fait sur le text, jamais sur
 * le HTML brut.
 */
private val LIMIT_PATTERN = Regex(
    """plafond de cotisation.{0,200}?pour (\d{4}).{0,40}?est de ([\d ]+) ?\$""",
    RegexOption.IGNORE_CASE,
)

/**
 * Une address valid est en https et pointe sur canada.ca. Laisser saisir
 * n'importe quelle address ferait read un limit a une source inconnue, alors
 * que le champ existe seulement pour suivre une reorganisation du site de
 * l'ARC.
 */
fun isValidCraPageUrl(url: String): Boolean {
    val address = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    val host = address.host ?: return false
    return address.scheme == "https" && (host == "canada.ca" || host.endsWith(".canada.ca"))
}

private val HTML_TAG = Regex("<[^>]*>")

private val WHITESPACE = Regex("\\s+")

/**
 * Extrait le limit TFSA annonce par la page de l'ARC, ou `null` si la page
 * ne le dit labelStep sous la forme attendue.
 *
 * Un demi-result n'existe labelStep : sans year ET amount positif lisibles, la
 * fonction rend `null` et l'appelant retombe sur la input manuelle. Un
 * amount devine serait faux et plausible, le pire des deux mondes pour un
 * suivi de room de cotisation.
 *
 * Le limit rendu est `confirmed = false` : c'est une proposition, que
 * l'utilisateur valid before qu'elle entre dans le calcul.
 */
fun readTfsaLimitFromCraPage(html: String): AnnualLimit? {
    val text = html
        .replace(HTML_TAG, " ")
        .replace("&nbsp;", " ")
        // Espaces insecables, fine ou non: l'ARC separe les milliers avec.
        .replace(' ', ' ')
        .replace(' ', ' ')
        .replace(WHITESPACE, " ")

    val match = LIMIT_PATTERN.find(text) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val amount = match.groupValues[2].replace(" ", "").toBigDecimalOrNull() ?: return null
    if (amount <= BigDecimal.ZERO) return null

    return AnnualLimit(account = Account.TFSA, year = year, amount = amount.toMoney(), confirmed = false)
}
