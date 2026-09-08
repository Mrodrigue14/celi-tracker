package dev.celitracker.app.ui.reglages

import dev.celitracker.engine.PlafondAnnuel
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Champs de saisie en String (source de verite des TextField) plutot qu'en
 * Int/BigDecimal deja parses: sans ca, une saisie partielle ("202") serait
 * perdue a chaque frappe le temps qu'elle devienne un entier valide.
 * La validite est une propriete calculee, jamais un champ separe.
 */
data class ReglagesUiState(
    val anneeAdmissibiliteCeli: String = "",
    val anneeNaissance: String = "",
    val dateOuvertureCeliapp: String = "",
    val plafonds: List<PlafondAnnuel> = emptyList(),
    val nouveauPlafondAnnee: String = "",
    val nouveauPlafondMontant: String = "",
    val message: String? = null,
) {
    val anneeAdmissibiliteValide: Int? get() = anneeAdmissibiliteCeli.toIntOrNull()
    val anneeNaissanceValide: Int? get() = anneeNaissance.toIntOrNull()

    val dateOuvertureValide: LocalDate? get() =
        if (dateOuvertureCeliapp.isBlank()) null
        else runCatching { LocalDate.parse(dateOuvertureCeliapp) }.getOrNull()

    /** Vide = pas de CELIAPP, valide. Non vide et non parsable = erreur de saisie. */
    val dateOuvertureInvalide: Boolean get() =
        dateOuvertureCeliapp.isNotBlank() && dateOuvertureValide == null

    val profilValide: Boolean get() =
        anneeAdmissibiliteValide != null && anneeNaissanceValide != null && !dateOuvertureInvalide

    val nouveauPlafondAnneeValide: Int? get() = nouveauPlafondAnnee.toIntOrNull()
    val nouveauPlafondMontantValide: BigDecimal? get() =
        nouveauPlafondMontant.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }

    val nouveauPlafondValide: Boolean get() =
        nouveauPlafondAnneeValide != null && nouveauPlafondMontantValide != null
}
