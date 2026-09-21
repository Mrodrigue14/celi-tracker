package dev.celitracker.app.ui.detail

import dev.celitracker.engine.FhsaYear

data class FhsaDetailUiState(
    val rows: List<FhsaYear> = emptyList(),
    /** Only these years offer a link to the journal: the others would show nothing there. */
    val yearsWithTransactions: Set<Int> = emptySet(),
)
