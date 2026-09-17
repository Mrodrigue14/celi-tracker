package dev.celitracker.app.ui.reglages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.data.Depot
import dev.celitracker.data.ResultatVerificationArc
import dev.celitracker.data.verifierPlafondsArc
import dev.celitracker.engine.Compte
import dev.celitracker.engine.PlafondAnnuel
import dev.celitracker.engine.Profil
import dev.celitracker.engine.Reglages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * [telechargerPage] est injecte plutot qu'appele en dur: la regle de lecture
 * de l'ARC se teste ainsi sans reseau.
 */
class ReglagesViewModel(
    private val depot: Depot,
    private val telechargerPage: suspend (String) -> String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReglagesUiState())
    val uiState: StateFlow<ReglagesUiState> = _uiState.asStateFlow()

    init {
        charger()
        verifierArc(demandeExplicite = false)
    }

    fun charger() {
        viewModelScope.launch {
            val profil = depot.profil()
            val plafonds = plafondsCeli()
            val reglages = depot.reglages()
            _uiState.update {
                it.copy(
                    anneeNaissance = profil?.anneeNaissance?.toString() ?: "",
                    dateOuvertureCeliapp = profil?.dateOuvertureCeliapp?.toString() ?: "",
                    plafonds = plafonds,
                    urlPageArc = reglages.urlPageArc,
                    derniereVerificationArc = reglages.dateDerniereVerification,
                )
            }
        }
    }

    fun modifierAnneeNaissance(valeur: String) = _uiState.update { it.copy(anneeNaissance = valeur) }
    fun modifierDateOuvertureCeliapp(valeur: String) = _uiState.update { it.copy(dateOuvertureCeliapp = valeur) }
    fun modifierNouveauPlafondAnnee(valeur: String) = _uiState.update { it.copy(nouveauPlafondAnnee = valeur) }
    fun modifierNouveauPlafondMontant(valeur: String) = _uiState.update { it.copy(nouveauPlafondMontant = valeur) }

    fun modifierUrlPageArc(valeur: String) = _uiState.update { it.copy(urlPageArc = valeur) }

    fun messageAffiche() = _uiState.update { it.copy(message = null) }

    fun enregistrerProfil() {
        val etat = _uiState.value
        val naissance = etat.anneeNaissanceValide ?: return
        if (etat.dateOuvertureInvalide) return
        viewModelScope.launch {
            depot.enregistrerProfil(
                Profil(anneeNaissance = naissance, dateOuvertureCeliapp = etat.dateOuvertureValide),
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

    /**
     * Va lire le plafond annonce par l'ARC. Une demande explicite ignore la
     * limite d'une lecture par mois; l'ouverture de l'ecran, non.
     */
    fun verifierArc(demandeExplicite: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(verificationEnCours = true, erreurArc = null) }
            val resultat = depot.verifierPlafondsArc(
                telecharger = telechargerPage,
                aujourdhui = LocalDate.now(),
                ignorerFrequence = demandeExplicite,
            )
            val plafonds = plafondsCeli()
            val reglages = depot.reglages()
            _uiState.update {
                it.copy(
                    plafonds = plafonds,
                    derniereVerificationArc = reglages.dateDerniereVerification,
                    verificationEnCours = false,
                    erreurArc = (resultat as? ResultatVerificationArc.Echec)?.raison,
                    message = when {
                        resultat is ResultatVerificationArc.Propose ->
                            "Plafond ${resultat.plafond.annee} proposé par l'ARC, à confirmer."

                        demandeExplicite && resultat is ResultatVerificationArc.Inutile ->
                            "Rien de nouveau sur le site de l'ARC."

                        else -> it.message
                    },
                )
            }
        }
    }

    fun confirmerProposition(plafond: PlafondAnnuel) {
        viewModelScope.launch {
            depot.enregistrerPlafond(plafond.copy(confirme = true))
            _uiState.update { it.copy(plafonds = plafondsCeli(), message = "Plafond ${plafond.annee} confirmé.") }
        }
    }

    fun rejeterProposition(plafond: PlafondAnnuel) {
        viewModelScope.launch {
            depot.supprimerPlafond(plafond.compte, plafond.annee)
            _uiState.update { it.copy(plafonds = plafondsCeli(), message = "Proposition ${plafond.annee} rejetée.") }
        }
    }

    fun enregistrerUrlPageArc() {
        val url = _uiState.value.urlPageArc
        viewModelScope.launch {
            depot.enregistrerReglages(Reglages(urlPageArc = url, dateDerniereVerification = depot.reglages().dateDerniereVerification))
            _uiState.update { it.copy(message = "Adresse enregistrée.") }
        }
    }

    private suspend fun plafondsCeli(): List<PlafondAnnuel> = depot.plafonds().filter { it.compte == Compte.CELI }.sortedBy { it.annee }
}
