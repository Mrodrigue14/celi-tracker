package dev.celitracker.app.ui.detail

import dev.celitracker.engine.DroitsAnnee

data class DetailCeliUiState(
    val lignes: List<DroitsAnnee> = emptyList(),
    /** Seules ces annees offrent un lien vers le journal: les autres n'y montreraient rien. */
    val anneesAvecTransactions: Set<Int> = emptySet(),
)
