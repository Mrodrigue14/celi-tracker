package dev.celitracker.app.ui.journal

import dev.celitracker.app.ui.format.toEnteredAmount
import dev.celitracker.app.ui.text.UiText
import dev.celitracker.engine.TransactionType
import dev.celitracker.engine.UNSAVED_ID
import java.math.BigDecimal
import java.time.LocalDate

/** Fields are Strings so partial input survives each keystroke. */
data class TransactionForm(
    val id: Long = UNSAVED_ID,
    val date: String = "",
    val type: TransactionType = TransactionType.DEPOSIT,
    val amount: String = "",
    val error: UiText? = null,
    /** Shown on the first Save tap when usage reaches 95%; a second tap saves anyway. */
    val warning: UiText? = null,
) {
    val isNew: Boolean get() = id == UNSAVED_ID

    val validDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()

    val validAmount: BigDecimal? get() = amount.toEnteredAmount()

    val valid: Boolean get() = validDate != null && validAmount != null
}
