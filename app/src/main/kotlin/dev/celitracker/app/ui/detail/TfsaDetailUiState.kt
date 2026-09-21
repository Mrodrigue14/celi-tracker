package dev.celitracker.app.ui.detail

import dev.celitracker.engine.TfsaYear

data class TfsaDetailUiState(
    val rows: List<TfsaYear> = emptyList(),
    val yearsWithTransactions: Set<Int> = emptySet(),
)
