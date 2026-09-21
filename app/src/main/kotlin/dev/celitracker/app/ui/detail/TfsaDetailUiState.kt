package dev.celitracker.app.ui.detail

import dev.celitracker.engine.TfsaYear

data class TfsaDetailUiState(
    val rows: List<TfsaYear> = emptyList(),
    /** Seules ces years offrent un lien vers le journal: les autres n'y montreraient rien. */
    val yearsWithTransactions: Set<Int> = emptySet(),
)
