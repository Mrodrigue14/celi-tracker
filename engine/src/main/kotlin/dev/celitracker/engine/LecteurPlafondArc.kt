package dev.celitracker.engine

import java.math.BigDecimal

/**
 * La page de l'ARC enonce le plafond de l'annee en cours dans une phrase :
 * « Le plafond de cotisation a un compte d'epargne libre d'impot (CELI) pour
 * 2026 est de 7 000 $. » L'annee et le montant y sont enveloppes chacun dans
 * un `<span class="nowrap">`, donc la lecture se fait sur le texte, jamais sur
 * le HTML brut.
 */
private val MOTIF_PLAFOND = Regex(
    """plafond de cotisation.{0,200}?pour (\d{4}).{0,40}?est de ([\d ]+) ?\$""",
    RegexOption.IGNORE_CASE,
)

private val BALISE = Regex("<[^>]*>")

private val ESPACES = Regex("\\s+")

/**
 * Extrait le plafond CELI annonce par la page de l'ARC, ou `null` si la page
 * ne le dit pas sous la forme attendue.
 *
 * Un demi-resultat n'existe pas : sans annee ET montant positif lisibles, la
 * fonction rend `null` et l'appelant retombe sur la saisie manuelle. Un
 * montant devine serait faux et plausible, le pire des deux mondes pour un
 * suivi de droits de cotisation.
 *
 * Le plafond rendu est `confirme = false` : c'est une proposition, que
 * l'utilisateur valide avant qu'elle entre dans le calcul.
 */
fun lirePlafondCeliArc(html: String): PlafondAnnuel? {
    val texte = html
        .replace(BALISE, " ")
        .replace("&nbsp;", " ")
        // Espaces insecables, fine ou non: l'ARC separe les milliers avec.
        .replace(' ', ' ')
        .replace(' ', ' ')
        .replace(ESPACES, " ")

    val trouve = MOTIF_PLAFOND.find(texte) ?: return null
    val annee = trouve.groupValues[1].toIntOrNull() ?: return null
    val montant = trouve.groupValues[2].replace(" ", "").toBigDecimalOrNull() ?: return null
    if (montant <= BigDecimal.ZERO) return null

    return PlafondAnnuel(compte = Compte.CELI, annee = annee, montant = montant.argent(), confirme = false)
}
