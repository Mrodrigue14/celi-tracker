package dev.celitracker.app.ui.journal

import dev.celitracker.app.ui.format.toEnteredAmount
import dev.celitracker.app.ui.text.UiText
import dev.celitracker.engine.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/** Fields as String for the same reason as SettingsUiState: keep partial input. */
data class TransactionForm(
    val id: Long = 0,
    val date: String = "",
    val type: TransactionType = TransactionType.DEPOSIT,
    val amount: String = "",
    val error: UiText? = null,
    /**
     * Set when saving this transaction would push room usage to 95% or
     * more. The first tap on Save shows it, the second tap saves anyway:
     * over-contributing is allowed, but never by accident.
     */
    val warning: UiText? = null,
) {
    val isNew: Boolean get() = id == 0L

    val validDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()

    val validAmount: BigDecimal? get() = amount.toEnteredAmount()

    val valid: Boolean get() = validDate != null && validAmount != null
}
