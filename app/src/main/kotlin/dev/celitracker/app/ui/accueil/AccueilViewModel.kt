package dev.celitracker.app.ui.accueil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.data.Depot
import dev.celitracker.engine.CeliMoteur
import dev.celitracker.engine.CeliappMoteur
import dev.celitracker.engine.SurCotisation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class AccueilViewModel(private val depot: Depot) : ViewModel() {

    private val _uiState = MutableStateFlow(etatVide())
    val uiState: StateFlow<AccueilUiState> = _uiState.asStateFlow()

    init {
        charger()
    }

    /**
     * Rappelee a chaque entree sur l'ecran (pas seulement a la creation du
     * ViewModel): le profil ou les plafonds ont pu changer dans les
     * reglages depuis la derniere visite, et rien n'est mis en cache ici.
     */
    fun charger() {
        viewModelScope.launch {
            val profil = depot.profil()
            if (profil == null) {
                _uiState.value = etatVide()
                return@launch
            }
            val plafonds = depot.plafonds()
            val transactions = depot.transactions()
            val anneeCourante = LocalDate.now().year
            _uiState.value = AccueilUiState(
                profil = profil,
                anneeCourante = anneeCourante,
                moisCourant = YearMonth.now().monthValue,
                droitsCeli = CeliMoteur.droitsParAnnee(profil, plafonds, transactions, anneeCourante),
                droitsCeliapp = CeliappMoteur.droitsParAnnee(profil, transactions, anneeCourante),
                excedentsCeli = SurCotisation.excedentsCeli(profil, plafonds, transactions, YearMonth.now()),
            )
        }
    }

    private fun etatVide() = AccueilUiState(
        profil = null,
        anneeCourante = LocalDate.now().year,
        moisCourant = YearMonth.now().monthValue,
    )
}
