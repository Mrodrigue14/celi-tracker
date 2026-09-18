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
    /**
     * Pose quand le depot porterait l'utilisation des droits a 95 % ou plus.
     * Le premier appui sur Enregistrer l'affiche, le second enregistre quand
     * meme: la sur-cotisation est permise, mais jamais par inadvertance.
     */
    val avertissement: String? = null,
) {
    val estNouvelle: Boolean get() = id == 0L

    val dateValide: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()

    // Un clavier en francais propose la virgule comme separateur decimal.
    val montantValide: BigDecimal? get() =
        montant.replace(',', '.').toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }

    val valide: Boolean get() = dateValide != null && montantValide != null
}
