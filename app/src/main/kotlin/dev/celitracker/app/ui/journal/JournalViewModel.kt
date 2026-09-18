package dev.celitracker.app.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.app.R
import dev.celitracker.app.ui.texte.TexteUi
import dev.celitracker.app.ui.texte.texte
import dev.celitracker.app.ui.texte.texteRes
import dev.celitracker.data.Depot
import dev.celitracker.data.SaisieInvalide
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

/**
 * [ouvrirAjout] vient du bouton « Ajouter » de l'accueil: l'ecran s'ouvre
 * directement sur la feuille de saisie au lieu de demander un second geste.
 */
class JournalViewModel(
    private val depot: Depot,
    compteInitial: Compte,
    ouvrirAjout: Boolean = false,
    anneeCiblee: Int? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JournalUiState(compte = compteInitial, anneeCiblee = anneeCiblee))

    private val compte: Compte get() = _uiState.value.compte
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    init {
        charger()
        if (ouvrirAjout) ouvrirNouvelle()
    }

    fun charger() {
        viewModelScope.launch {
            val transactions = transactionsDuCompte()
            _uiState.update { it.copy(transactions = transactions) }
        }
    }

    /** Passer du CELI au CELIAPP reste sur le meme ecran: c'est un filtre, pas une destination. */
    fun changerCompte(nouveau: Compte) {
        if (nouveau == compte) return
        _uiState.update { it.copy(compte = nouveau, transactions = emptyList(), formulaire = null) }
        charger()
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

    /** L'ecran a defile jusqu'a l'annee demandee: ne pas y revenir a chaque rechargement. */
    fun anneeCibleeAtteinte() = _uiState.update { it.copy(anneeCiblee = null) }

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
            } catch (e: SaisieInvalide) {
                _uiState.update { it.copy(formulaire = it.formulaire?.copy(erreur = texte(e.raison.texteRes()))) }
                return@launch
            }
            recharger(message = messageApres(date.year))
        }
    }

    /**
     * Texte a confirmer si ce depot porterait l'utilisation de l'annee a 95 % ou
     * au-dela. Un retrait n'en demande jamais: il ne consomme pas de droits.
     */
    private suspend fun avertissementAvant(transaction: Transaction): TexteUi? {
        if (transaction.type != TypeTx.DEPOT) return null
        val autres = depot.transactions().filter { it.id != transaction.id }
        val apres = utilisation(autres, transaction.date.year)?.avecDepot(transaction.montant) ?: return null
        val annee = transaction.date.year
        return when (apres.niveau) {
            NiveauUtilisation.DEPASSE -> texte(R.string.journal_avertissement_depassement, compte, annee, apres.excedent)

            NiveauUtilisation.CRITIQUE ->
                texte(R.string.journal_avertissement_critique, apres.pourcentage ?: 0, compte, annee, apres.restant)

            else -> null
        }
    }

    private suspend fun messageApres(annee: Int): TexteUi {
        val courante = utilisation(depot.transactions(), annee)
        if (courante == null || courante.niveau == NiveauUtilisation.NORMAL) return texte(R.string.journal_enregistree)
        return texte(R.string.journal_enregistree_utilisation, courante.pourcentage ?: 0, compte, annee)
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
            recharger(message = texte(R.string.journal_supprimee))
        }
    }

    private suspend fun recharger(message: TexteUi) {
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
