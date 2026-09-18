package dev.celitracker.app.ui.journal

import dev.celitracker.engine.Compte
import dev.celitracker.engine.Transaction

data class JournalUiState(
    val compte: Compte = Compte.CELI,
    val transactions: List<Transaction> = emptyList(),
    val formulaire: FormulaireTransaction? = null,
    val message: String? = null,
)
