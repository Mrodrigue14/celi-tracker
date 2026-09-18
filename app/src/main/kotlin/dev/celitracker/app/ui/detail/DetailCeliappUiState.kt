package dev.celitracker.app.ui.detail

import dev.celitracker.engine.DroitsAnneeCeliapp

data class DetailCeliappUiState(
    val lignes: List<DroitsAnneeCeliapp> = emptyList(),
    /** Seules ces annees offrent un lien vers le journal: les autres n'y montreraient rien. */
    val anneesAvecTransactions: Set<Int> = emptySet(),
)
