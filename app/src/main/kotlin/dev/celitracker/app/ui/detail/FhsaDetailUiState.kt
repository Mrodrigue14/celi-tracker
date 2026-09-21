package dev.celitracker.app.ui.detail

import dev.celitracker.engine.FhsaYear

data class FhsaDetailUiState(
    val rows: List<FhsaYear> = emptyList(),
    val yearsWithTransactions: Set<Int> = emptySet(),
)
