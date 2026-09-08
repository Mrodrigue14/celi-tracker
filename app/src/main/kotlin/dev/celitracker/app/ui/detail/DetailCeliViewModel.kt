package dev.celitracker.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.data.Depot
import dev.celitracker.engine.CeliMoteur
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class DetailCeliViewModel(private val depot: Depot) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailCeliUiState())
    val uiState: StateFlow<DetailCeliUiState> = _uiState.asStateFlow()

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            val profil = depot.profil()
            if (profil == null) {
                _uiState.value = DetailCeliUiState()
                return@launch
            }
            val plafonds = depot.plafonds()
            val transactions = depot.transactions()
            _uiState.value = DetailCeliUiState(
                lignes = CeliMoteur.droitsParAnnee(profil, plafonds, transactions, LocalDate.now().year)
            )
        }
    }
}
