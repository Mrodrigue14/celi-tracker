package dev.celitracker.app.ui.reglages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.app.R
import dev.celitracker.app.ui.texte.texte
import dev.celitracker.app.ui.texte.texteRes
import dev.celitracker.data.Depot
import dev.celitracker.data.ImportInvalide
import dev.celitracker.data.ResultatAdresseArc
import dev.celitracker.data.ResultatVerificationArc
import dev.celitracker.data.URL_PAGE_ARC_PAR_DEFAUT
import dev.celitracker.data.changerAdressePageArc
import dev.celitracker.data.exporterJson
import dev.celitracker.data.importerJson
import dev.celitracker.data.verifierPlafondsArc
import dev.celitracker.engine.Compte
import dev.celitracker.engine.PlafondAnnuel
import dev.celitracker.engine.Profil
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

    /**
     * [remplacerSaisies] a false, le chargement ne remplit que les champs
     * encore vides: la lecture de la base est asynchrone et ecraserait sinon ce
     * que l'utilisateur vient de taper. Un import, lui, remplace tout le
     * contenu, donc les champs affiches aussi.
     */
    fun charger(remplacerSaisies: Boolean = false) {
        viewModelScope.launch {
            val profil = depot.profil()
            val plafonds = plafondsCeli()
            val reglages = depot.reglages()
            val naissance = profil?.anneeNaissance?.toString() ?: ""
            val ouverture = profil?.dateOuvertureCeliapp?.toString() ?: ""
            _uiState.update {
                it.copy(
                    anneeNaissance = if (remplacerSaisies) naissance else it.anneeNaissance.ifBlank { naissance },
                    dateOuvertureCeliapp = if (remplacerSaisies) ouverture else it.dateOuvertureCeliapp.ifBlank { ouverture },
                    plafonds = plafonds,
                    urlPageArc = it.urlPageArc.ifBlank { reglages.urlPageArc },
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
            _uiState.update { it.copy(message = texte(R.string.message_profil_enregistre)) }
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
                    message = texte(R.string.message_plafond_enregistre),
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
                    erreurArc = (resultat as? ResultatVerificationArc.Echec)?.let { echec -> texte(echec.raison.texteRes()) },
                    message = when {
                        resultat is ResultatVerificationArc.Propose ->
                            texte(R.string.message_plafond_propose, resultat.plafond.annee)

                        demandeExplicite && resultat is ResultatVerificationArc.Inutile ->
                            texte(R.string.message_arc_rien_de_nouveau)

                        else -> it.message
                    },
                )
            }
        }
    }

    fun confirmerProposition(plafond: PlafondAnnuel) {
        viewModelScope.launch {
            depot.enregistrerPlafond(plafond.copy(confirme = true))
            _uiState.update { it.copy(plafonds = plafondsCeli(), message = texte(R.string.message_plafond_confirme, plafond.annee)) }
        }
    }

    fun rejeterProposition(plafond: PlafondAnnuel) {
        viewModelScope.launch {
            depot.supprimerPlafond(plafond.compte, plafond.annee)
            _uiState.update { it.copy(plafonds = plafondsCeli(), message = texte(R.string.message_proposition_rejetee, plafond.annee)) }
        }
    }

    /**
     * L'adresse est essayee avant d'etre enregistree. Refusee, le champ revient
     * a l'adresse en place, la derniere qui a fonctionne.
     */
    fun enregistrerUrlPageArc() = changerAdresse(_uiState.value.urlPageArc.trim())

    /** L'adresse d'origine passe par le meme essai: elle aussi peut avoir change. */
    fun retablirUrlPageArc() = changerAdresse(URL_PAGE_ARC_PAR_DEFAUT)

    private fun changerAdresse(saisie: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(verificationEnCours = true) }
            val resultat = depot.changerAdressePageArc(saisie, telechargerPage)
            val enPlace = depot.reglages().urlPageArc
            _uiState.update {
                it.copy(
                    urlPageArc = enPlace,
                    verificationEnCours = false,
                    message = when (resultat) {
                        ResultatAdresseArc.Enregistree -> texte(R.string.message_adresse_enregistree)
                        is ResultatAdresseArc.Refusee -> texte(R.string.message_adresse_refusee, texte(resultat.raison.texteRes()))
                    },
                )
            }
        }
    }

    /**
     * L'ecran fournit l'ecriture et la lecture du fichier: les API Android de
     * stockage restent hors du ViewModel.
     */
    fun exporter(ecrire: suspend (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                ecrire(depot.exporterJson())
                texte(R.string.message_donnees_exportees)
            } catch (e: Exception) {
                texte(R.string.message_export_impossible)
            }
            _uiState.update { it.copy(message = message) }
        }
    }

    /** L'import remplace tout le contenu; l'ecran confirme avant d'appeler. */
    fun importer(lire: suspend () -> String) {
        viewModelScope.launch {
            val message = try {
                depot.importerJson(lire())
                texte(R.string.message_donnees_importees)
            } catch (e: ImportInvalide) {
                texte(R.string.message_import_refuse, texte(e.raison.texteRes()))
            } catch (e: Exception) {
                texte(R.string.message_import_refuse, texte(R.string.import_fichier_illisible))
            }
            charger(remplacerSaisies = true)
            _uiState.update { it.copy(message = message) }
        }
    }

    private suspend fun plafondsCeli(): List<PlafondAnnuel> = depot.plafonds().filter { it.compte == Compte.CELI }.sortedBy { it.annee }
}
