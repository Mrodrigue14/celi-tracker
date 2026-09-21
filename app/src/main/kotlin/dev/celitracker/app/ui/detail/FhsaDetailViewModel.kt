package dev.celitracker.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.data.Repository
import dev.celitracker.engine.Account
import dev.celitracker.engine.FhsaEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class FhsaDetailViewModel(private val repository: Repository) : ViewModel() {

    private val _uiState = MutableStateFlow(FhsaDetailUiState())
    val uiState: StateFlow<FhsaDetailUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val profile = repository.profile()
            if (profile == null) {
                _uiState.value = FhsaDetailUiState()
                return@launch
            }
            val transactions = repository.transactions()
            _uiState.value = FhsaDetailUiState(
                yearsWithTransactions = transactions.filter { it.account == Account.FHSA }.map { it.date.year }.toSet(),
                rows = FhsaEngine.roomByYear(profile, transactions, LocalDate.now().year),
            )
        }
    }
}
