package dev.celitracker.app.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.data.Depot
import dev.celitracker.engine.Compte
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TypeTx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class JournalViewModel(private val depot: Depot, val compte: Compte) : ViewModel() {

    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            val transactions = transactionsDuCompte()
            _uiState.update { it.copy(transactions = transactions) }
        }
    }

    fun ouvrirNouvelle() = _uiState.update {
        it.copy(formulaire = FormulaireTransaction(date = LocalDate.now().toString()), message = null)
    }

    fun ouvrirModification(transaction: Transaction) = _uiState.update {
        it.copy(
            formulaire = FormulaireTransaction(
                id = transaction.id,
                date = transaction.date.toString(),
                type = transaction.type,
                montant = transaction.montant.toPlainString(),
            ),
            message = null,
        )
    }

    fun messageAffiche() = _uiState.update { it.copy(message = null) }

    fun fermerFormulaire() = _uiState.update { it.copy(formulaire = null) }

    fun modifierDate(valeur: String) = modifierFormulaire { copy(date = valeur) }
    fun modifierType(valeur: TypeTx) = modifierFormulaire { copy(type = valeur) }
    fun modifierMontant(valeur: String) = modifierFormulaire { copy(montant = valeur) }

    fun enregistrer() {
        val formulaire = _uiState.value.formulaire ?: return
        val date = formulaire.dateValide ?: return
        val montant = formulaire.montantValide ?: return
        val transaction = Transaction(compte, date, formulaire.type, montant, formulaire.id)
        viewModelScope.launch {
            try {
                if (formulaire.estNouvelle) depot.ajouterTransaction(transaction) else depot.modifierTransaction(transaction)
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(formulaire = it.formulaire?.copy(erreur = e.message)) }
                return@launch
            }
            recharger(message = "Transaction enregistrée.")
        }
    }

    fun supprimer() {
        val formulaire = _uiState.value.formulaire?.takeUnless { it.estNouvelle } ?: return
        viewModelScope.launch {
            depot.supprimerTransaction(formulaire.id)
            recharger(message = "Transaction supprimée.")
        }
    }

    private suspend fun recharger(message: String) {
        val transactions = transactionsDuCompte()
        _uiState.update { it.copy(transactions = transactions, formulaire = null, message = message) }
    }

    // Toute saisie efface l'erreur precedente: elle portait sur l'ancienne valeur.
    private fun modifierFormulaire(modification: FormulaireTransaction.() -> FormulaireTransaction) = _uiState.update { etat -> etat.copy(formulaire = etat.formulaire?.modification()?.copy(erreur = null)) }

    private suspend fun transactionsDuCompte(): List<Transaction> = depot.transactions().filter { it.compte == compte }.sortedByDescending { it.date }
}
