package dev.celitracker.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.data.Repository
import dev.celitracker.engine.Account
import dev.celitracker.engine.TfsaEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class TfsaDetailViewModel(private val repository: Repository) : ViewModel() {

    private val _uiState = MutableStateFlow(TfsaDetailUiState())
    val uiState: StateFlow<TfsaDetailUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val profile = repository.profile()
            if (profile == null) {
                _uiState.value = TfsaDetailUiState()
                return@launch
            }
            val limits = repository.limits()
            val transactions = repository.transactions()
            _uiState.value = TfsaDetailUiState(
                yearsWithTransactions = transactions.yearsWith(Account.TFSA),
                rows = TfsaEngine.roomByYear(profile, limits, transactions, LocalDate.now().year),
            )
        }
    }
}
