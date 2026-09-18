package dev.celitracker.app.ui.reglages

import dev.celitracker.app.ui.format.versMontantSaisi
import dev.celitracker.app.ui.texte.TexteUi
import dev.celitracker.engine.PlafondAnnuel
import dev.celitracker.engine.Profil
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private const val ANNEE_NAISSANCE_MIN = 1900

/**
 * Champs de saisie en String (source de verite des TextField) plutot qu'en
 * Int/BigDecimal deja parses: sans ca, une saisie partielle ("202") serait
 * perdue a chaque frappe le temps qu'elle devienne un entier valide.
 * La validite est une propriete calculee, jamais un champ separe.
 */
data class ReglagesUiState(
    val anneeNaissance: String = "",
    val dateOuvertureCeliapp: String = "",
    val plafonds: List<PlafondAnnuel> = emptyList(),
    val nouveauPlafondAnnee: String = "",
    val nouveauPlafondMontant: String = "",
    val urlPageArc: String = "",
    val derniereVerificationArc: Instant? = null,
    val verificationEnCours: Boolean = false,
    val erreurArc: TexteUi? = null,
    val message: TexteUi? = null,
) {
    /** Lues sur le site de l'ARC, en attente de confirmation. */
    val propositions: List<PlafondAnnuel> get() = plafonds.filter { !it.confirme }

    val plafondsConfirmes: List<PlafondAnnuel> get() = plafonds.filter { it.confirme }

    /**
     * Les plafonds d'avant l'admissibilite n'entrent pas dans les droits: ils
     * restent en base, mais replies a l'ecran. Sans annee de naissance, tout
     * est pertinent.
     */
    val plafondsPertinents: List<PlafondAnnuel> get() =
        plafondsConfirmes.filter { plafond -> anneeAdmissibiliteCeli?.let { plafond.annee >= it } ?: true }

    val plafondsAnterieurs: List<PlafondAnnuel> get() = plafondsConfirmes - plafondsPertinents.toSet()

    val anneeNaissanceValide: Int? get() =
        anneeNaissance.toIntOrNull()?.takeIf { it in ANNEE_NAISSANCE_MIN..LocalDate.now().year }

    /**
     * Se deduit de l'annee de naissance, jamais saisie. Le calcul vient de
     * [Profil] pour qu'il n'existe qu'a un seul endroit.
     */
    val anneeAdmissibiliteCeli: Int? get() =
        anneeNaissanceValide?.let { Profil(anneeNaissance = it, dateOuvertureCeliapp = null).anneeAdmissibiliteCeli }

    val dateOuvertureValide: LocalDate? get() =
        if (dateOuvertureCeliapp.isBlank()) null else runCatching { LocalDate.parse(dateOuvertureCeliapp) }.getOrNull()

    /** Vide = pas de CELIAPP, valide. Non vide et non parsable = erreur de saisie. */
    val dateOuvertureInvalide: Boolean get() =
        dateOuvertureCeliapp.isNotBlank() && dateOuvertureValide == null

    val profilValide: Boolean get() = anneeNaissanceValide != null && !dateOuvertureInvalide

    val nouveauPlafondAnneeValide: Int? get() = nouveauPlafondAnnee.toIntOrNull()
    val nouveauPlafondMontantValide: BigDecimal? get() = nouveauPlafondMontant.versMontantSaisi()

    val nouveauPlafondValide: Boolean get() =
        nouveauPlafondAnneeValide != null && nouveauPlafondMontantValide != null
}
