package dev.celitracker.app.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.app.R
import dev.celitracker.app.ui.text.UiText
import dev.celitracker.app.ui.text.textRes
import dev.celitracker.app.ui.text.uiText
import dev.celitracker.data.InvalidInput
import dev.celitracker.data.Repository
import dev.celitracker.engine.Account
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TransactionType
import dev.celitracker.engine.Usage
import dev.celitracker.engine.UsageLevel
import dev.celitracker.engine.fhsaUsage
import dev.celitracker.engine.tfsaUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.time.LocalDate

/**
 * [openAdd] vient du bouton « Ajouter » de l'home: l'ecran s'ouvre
 * directement sur la feuille de input au lieu de demander un second geste.
 */
class JournalViewModel(
    private val repository: Repository,
    initialAccount: Account,
    openAdd: Boolean = false,
    targetYear: Int? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JournalUiState(account = initialAccount, targetYear = targetYear))

    private val account: Account get() = _uiState.value.account
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    init {
        load()
        if (openAdd) openNew()
    }

    fun load() {
        viewModelScope.launch {
            val transactions = accountTransactions()
            _uiState.update { it.copy(transactions = transactions) }
        }
    }

    /** Passer du TFSA au FHSA reste sur le meme ecran: c'est un filtre, labelStep une destination. */
    fun changeAccount(choice: Account) {
        if (choice == account) return
        _uiState.update { it.copy(account = choice, transactions = emptyList(), form = null) }
        load()
    }

    fun openNew() = _uiState.update {
        it.copy(form = TransactionForm(date = LocalDate.now().toString()), message = null)
    }

    fun openEdit(transaction: Transaction) = _uiState.update {
        it.copy(
            form = TransactionForm(
                id = transaction.id,
                date = transaction.date.toString(),
                type = transaction.type,
                amount = transaction.amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            ),
            message = null,
        )
    }

    /** L'ecran a defile jusqu'a l'year demandee: ne labelStep y revenir a chaque rechargement. */
    fun targetYearReached() = _uiState.update { it.copy(targetYear = null) }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateDate(value: String) = updateForm { copy(date = value) }
    fun updateType(value: TransactionType) = updateForm { copy(type = value) }
    fun updateAmount(value: String) = updateForm { copy(amount = value) }

    fun save() {
        val form = _uiState.value.form ?: return
        val date = form.validDate ?: return
        val amount = form.validAmount ?: return
        val transaction = Transaction(account, date, form.type, amount, form.id)
        viewModelScope.launch {
            val after = usageAfter(transaction)
            if (form.warning == null) {
                warning(transaction, after)?.let { text ->
                    _uiState.update { it.copy(form = it.form?.copy(warning = text)) }
                    return@launch
                }
            }
            try {
                if (form.isNew) repository.addTransaction(transaction) else repository.updateTransaction(transaction)
            } catch (e: InvalidInput) {
                _uiState.update { it.copy(form = it.form?.copy(error = uiText(e.reason.textRes()))) }
                return@launch
            }
            reload(message = messageAfter(after, date.year))
        }
    }

    /**
     * Usage de l'year de [transaction], celle-ci comprise (a la place de
     * son ancienne version si c'est une modification). Sert a la fois a
     * l'warning before l'enregistrement et au message after.
     */
    private suspend fun usageAfter(transaction: Transaction): Usage? {
        val profile = repository.profile() ?: return null
        val transactions = repository.transactions().filter { it.id != transaction.id } + transaction
        val year = transaction.date.year
        return when (account) {
            Account.TFSA -> tfsaUsage(profile, repository.limits(), transactions, year)
            Account.FHSA -> fhsaUsage(profile, transactions, year)
        }
    }

    /**
     * Texte a confirmer si ce repository porte l'usage de l'year a 95 % ou
     * au-dela. Un withdrawal n'en demande jamais: il ne consomme labelStep de room.
     */
    private fun warning(transaction: Transaction, after: Usage?): UiText? {
        if (transaction.type != TransactionType.DEPOSIT || after == null) return null
        val year = transaction.date.year
        return when (after.level) {
            UsageLevel.EXCEEDED -> uiText(R.string.journal_warning_exceeded, account, year, after.excess)

            UsageLevel.CRITICAL ->
                uiText(R.string.journal_warning_critical, after.percent ?: 0, account, year, after.remaining)

            else -> null
        }
    }

    private fun messageAfter(after: Usage?, year: Int): UiText {
        if (after == null || after.level == UsageLevel.NORMAL) return uiText(R.string.journal_saved)
        return uiText(R.string.journal_saved_usage, after.percent ?: 0, account, year)
    }

    fun delete() {
        val form = _uiState.value.form?.takeUnless { it.isNew } ?: return
        viewModelScope.launch {
            repository.deleteTransaction(form.id)
            reload(message = uiText(R.string.journal_deleted))
        }
    }

    private suspend fun reload(message: UiText) {
        val transactions = accountTransactions()
        _uiState.update { it.copy(transactions = transactions, form = null, message = message) }
    }

    // Toute input efface l'error et l'warning precedents: ils portaient
    // sur l'ancienne value.
    private fun updateForm(modification: TransactionForm.() -> TransactionForm) = _uiState.update { state ->
        state.copy(form = state.form?.modification()?.copy(error = null, warning = null))
    }

    private suspend fun accountTransactions(): List<Transaction> = repository.transactions().filter { it.account == account }.sortedByDescending { it.date }
}
