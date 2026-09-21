package dev.celitracker.app.ui.journal

import dev.celitracker.app.ui.text.UiText
import dev.celitracker.engine.Account
import dev.celitracker.engine.Transaction

data class JournalUiState(
    val account: Account = Account.TFSA,
    /** Annee a amener a l'ecran, quand on arrive depuis le detail d'un account. */
    val targetYear: Int? = null,
    val transactions: List<Transaction> = emptyList(),
    val form: TransactionForm? = null,
    val message: UiText? = null,
)
