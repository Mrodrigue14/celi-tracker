package dev.celitracker.app.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.data.Depot
import dev.celitracker.engine.Compte
import dev.celitracker.engine.NiveauUtilisation
import dev.celitracker.engine.Transaction
import dev.celitracker.engine.TypeTx
import dev.celitracker.engine.Utilisation
import dev.celitracker.engine.utilisationCeli
import dev.celitracker.engine.utilisationCeliapp
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
            if (formulaire.avertissement == null) {
                avertissementAvant(transaction)?.let { texte ->
                    _uiState.update { it.copy(formulaire = it.formulaire?.copy(avertissement = texte)) }
                    return@launch
                }
            }
            try {
                if (formulaire.estNouvelle) depot.ajouterTransaction(transaction) else depot.modifierTransaction(transaction)
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(formulaire = it.formulaire?.copy(erreur = e.message)) }
                return@launch
            }
            recharger(message = messageApres(date.year))
        }
    }

    /**
     * Texte a confirmer si ce depot porterait l'utilisation de l'annee a 95 % ou
     * au-dela. Un retrait n'en demande jamais: il ne consomme pas de droits.
     */
    private suspend fun avertissementAvant(transaction: Transaction): String? {
        if (transaction.type != TypeTx.DEPOT) return null
        val autres = depot.transactions().filter { it.id != transaction.id }
        val apres = utilisation(autres, transaction.date.year)?.avecDepot(transaction.montant) ?: return null
        val annee = transaction.date.year
        return when (apres.niveau) {
            NiveauUtilisation.DEPASSE ->
                "Ce dépôt dépasse tes droits $compte de $annee de ${apres.excedent.formatMontant()}. " +
                    "L'ARC impose 1 % par mois sur l'excédent tant qu'il reste dans le compte."

            NiveauUtilisation.CRITIQUE ->
                "Ce dépôt porterait ton utilisation à ${apres.pourcentage} % de tes droits $compte de $annee. " +
                    "Il te resterait ${apres.restant.formatMontant()}."

            else -> null
        }
    }

    private suspend fun messageApres(annee: Int): String {
        val courante = utilisation(depot.transactions(), annee)
        if (courante == null || courante.niveau == NiveauUtilisation.NORMAL) return "Transaction enregistrée."
        return "Transaction enregistrée. Tu as utilisé ${courante.pourcentage} % de tes droits $compte de $annee."
    }

    private suspend fun utilisation(transactions: List<Transaction>, annee: Int): Utilisation? {
        val profil = depot.profil() ?: return null
        return when (compte) {
            Compte.CELI -> utilisationCeli(profil, depot.plafonds(), transactions, annee)
            Compte.CELIAPP -> utilisationCeliapp(profil, transactions, annee)
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

    // Toute saisie efface l'erreur et l'avertissement precedents: ils portaient
    // sur l'ancienne valeur.
    private fun modifierFormulaire(modification: FormulaireTransaction.() -> FormulaireTransaction) = _uiState.update { etat ->
        etat.copy(formulaire = etat.formulaire?.modification()?.copy(erreur = null, avertissement = null))
    }

    private suspend fun transactionsDuCompte(): List<Transaction> = depot.transactions().filter { it.compte == compte }.sortedByDescending { it.date }
}
