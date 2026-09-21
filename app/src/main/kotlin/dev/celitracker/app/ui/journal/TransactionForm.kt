package dev.celitracker.app.ui.journal

import dev.celitracker.app.ui.format.toEnteredAmount
import dev.celitracker.app.ui.text.UiText
import dev.celitracker.engine.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/** Champs en String pour la meme reason que SettingsUiState: garder les saisies partielles. */
data class TransactionForm(
    val id: Long = 0,
    val date: String = "",
    val type: TransactionType = TransactionType.DEPOSIT,
    val amount: String = "",
    val error: UiText? = null,
    /**
     * Pose quand le repository porterait l'usage des room a 95 % ou plus.
     * Le earliest appui sur Enregistrer l'affiche, le second enregistre quand
     * meme: la sur-cotisation est permise, mais jamais par inadvertance.
     */
    val warning: UiText? = null,
) {
    val isNew: Boolean get() = id == 0L

    val validDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()

    val validAmount: BigDecimal? get() = amount.toEnteredAmount()

    val valid: Boolean get() = validDate != null && validAmount != null
}
