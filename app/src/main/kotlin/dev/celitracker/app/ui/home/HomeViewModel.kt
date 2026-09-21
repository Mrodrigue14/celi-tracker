package dev.celitracker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.data.Repository
import dev.celitracker.engine.FhsaEngine
import dev.celitracker.engine.Overcontribution
import dev.celitracker.engine.TfsaEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class HomeViewModel(private val repository: Repository) : ViewModel() {

    private val _uiState = MutableStateFlow(emptyState().copy(loaded = false))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Called on every backStackEntry on the screen (not only when the
     * ViewModel is created): the profile or limits may have changed in
     * settings since the last visit, and nothing is cached here.
     */
    fun load() {
        viewModelScope.launch {
            val profile = repository.profile()
            if (profile == null) {
                _uiState.value = emptyState()
                return@launch
            }
            val limits = repository.limits()
            val transactions = repository.transactions()
            val currentYear = LocalDate.now().year
            _uiState.value = HomeUiState(
                profile = profile,
                currentYear = currentYear,
                currentMonth = YearMonth.now().monthValue,
                tfsaRoom = TfsaEngine.roomByYear(profile, limits, transactions, currentYear),
                fhsaRoom = FhsaEngine.roomByYear(profile, transactions, currentYear),
                tfsaExcesses = Overcontribution.tfsaExcesses(profile, limits, transactions, YearMonth.now()),
            )
        }
    }

    private fun emptyState() = HomeUiState(
        profile = null,
        currentYear = LocalDate.now().year,
        currentMonth = YearMonth.now().monthValue,
    )
}
