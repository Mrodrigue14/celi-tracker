package dev.celitracker.app.ui.reglages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.data.Depot
import dev.celitracker.engine.Compte
import dev.celitracker.engine.PlafondAnnuel
import dev.celitracker.engine.Profil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReglagesViewModel(private val depot: Depot) : ViewModel() {

    private val _uiState = MutableStateFlow(ReglagesUiState())
    val uiState: StateFlow<ReglagesUiState> = _uiState.asStateFlow()

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            val profil = depot.profil()
            val plafonds = plafondsCeli()
            _uiState.update {
                it.copy(
                    anneeAdmissibiliteCeli = profil?.anneeAdmissibiliteCeli?.toString() ?: "",
                    anneeNaissance = profil?.anneeNaissance?.toString() ?: "",
                    dateOuvertureCeliapp = profil?.dateOuvertureCeliapp?.toString() ?: "",
                    plafonds = plafonds,
                )
            }
        }
    }

    fun modifierAnneeAdmissibiliteCeli(valeur: String) = _uiState.update { it.copy(anneeAdmissibiliteCeli = valeur) }
    fun modifierAnneeNaissance(valeur: String) = _uiState.update { it.copy(anneeNaissance = valeur) }
    fun modifierDateOuvertureCeliapp(valeur: String) = _uiState.update { it.copy(dateOuvertureCeliapp = valeur) }
    fun modifierNouveauPlafondAnnee(valeur: String) = _uiState.update { it.copy(nouveauPlafondAnnee = valeur) }
    fun modifierNouveauPlafondMontant(valeur: String) = _uiState.update { it.copy(nouveauPlafondMontant = valeur) }

    fun enregistrerProfil() {
        val etat = _uiState.value
        val annee = etat.anneeAdmissibiliteValide ?: return
        val naissance = etat.anneeNaissanceValide ?: return
        if (etat.dateOuvertureInvalide) return
        viewModelScope.launch {
            depot.enregistrerProfil(
                Profil(
                    anneeAdmissibiliteCeli = annee,
                    anneeNaissance = naissance,
                    dateOuvertureCeliapp = etat.dateOuvertureValide,
                )
            )
            _uiState.update { it.copy(message = "Profil enregistré.") }
        }
    }

    fun ajouterPlafond() {
        val etat = _uiState.value
        val annee = etat.nouveauPlafondAnneeValide ?: return
        val montant = etat.nouveauPlafondMontantValide ?: return
        viewModelScope.launch {
            // confirme = true : saisie manuelle directe, pas une lecture ARC en
            // attente de validation.
            depot.enregistrerPlafond(PlafondAnnuel(compte = Compte.CELI, annee = annee, montant = montant, confirme = true))
            val plafonds = plafondsCeli()
            _uiState.update {
                it.copy(
                    plafonds = plafonds,
                    nouveauPlafondAnnee = "",
                    nouveauPlafondMontant = "",
                    message = "Plafond enregistré.",
                )
            }
        }
    }

    private suspend fun plafondsCeli(): List<PlafondAnnuel> =
        depot.plafonds().filter { it.compte == Compte.CELI }.sortedBy { it.annee }
}
