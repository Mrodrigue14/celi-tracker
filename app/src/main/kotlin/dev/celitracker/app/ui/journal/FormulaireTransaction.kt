package dev.celitracker.app.ui.journal

import dev.celitracker.engine.TypeTx
import java.math.BigDecimal
import java.time.LocalDate

/** Champs en String pour la meme raison que ReglagesUiState: garder les saisies partielles. */
data class FormulaireTransaction(
    val id: Long = 0,
    val date: String = "",
    val type: TypeTx = TypeTx.DEPOT,
    val montant: String = "",
    val erreur: String? = null,
) {
    val estNouvelle: Boolean get() = id == 0L

    val dateValide: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()

    // Un clavier en francais propose la virgule comme separateur decimal.
    val montantValide: BigDecimal? get() =
        montant.replace(',', '.').toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }

    val valide: Boolean get() = dateValide != null && montantValide != null
}
