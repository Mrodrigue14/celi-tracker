package dev.celitracker.app.ui.detail

import dev.celitracker.engine.TfsaYear

data class TfsaDetailUiState(
    val rows: List<TfsaYear> = emptyList(),
    /** Only these years offer a link to the journal: the others would show nothing there. */
    val yearsWithTransactions: Set<Int> = emptySet(),
)
