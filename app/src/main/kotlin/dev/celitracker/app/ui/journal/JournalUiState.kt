package dev.celitracker.app.ui.journal

import dev.celitracker.app.ui.text.UiText
import dev.celitracker.engine.Account
import dev.celitracker.engine.Transaction

data class JournalUiState(
    val account: Account = Account.TFSA,
    val scrollToYear: Int? = null,
    val transactions: List<Transaction> = emptyList(),
    val form: TransactionForm? = null,
    val message: UiText? = null,
)
