package dev.celitracker.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.data.Depot
import dev.celitracker.engine.CeliappMoteur
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class DetailCeliappViewModel(private val depot: Depot) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailCeliappUiState())
    val uiState: StateFlow<DetailCeliappUiState> = _uiState.asStateFlow()

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            val profil = depot.profil()
            if (profil == null) {
                _uiState.value = DetailCeliappUiState()
                return@launch
            }
            val transactions = depot.transactions()
            _uiState.value = DetailCeliappUiState(
                lignes = CeliappMoteur.droitsParAnnee(profil, transactions, LocalDate.now().year)
            )
        }
    }
}
