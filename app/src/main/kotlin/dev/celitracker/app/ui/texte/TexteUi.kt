package dev.celitracker.app.ui.texte

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.celitracker.app.R
import dev.celitracker.app.ui.format.formatDate
import dev.celitracker.app.ui.format.formatMontant
import dev.celitracker.data.RaisonEchecArc
import dev.celitracker.data.RaisonImport
import dev.celitracker.data.RaisonRefusAdresse
import dev.celitracker.data.RaisonSaisie
import dev.celitracker.engine.Compte
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Un texte sans langue: une ressource et ses arguments. Les ViewModels en
 * produisent, l'ecran le met dans la langue de l'appareil au moment de
 * l'afficher. Un montant, un compte ou une date passe tel quel en argument et
 * se formate a la resolution, dans la meme langue que la phrase.
 */
data class TexteUi(@StringRes val id: Int, val arguments: List<Any> = emptyList())

fun texte(@StringRes id: Int, vararg arguments: Any) = TexteUi(id, arguments.toList())

fun TexteUi.resoudre(contexte: Context): String {
    val locale = contexte.resources.configuration.locales[0]
    val formates = arguments.map { argument ->
        when (argument) {
            is BigDecimal -> argument.formatMontant(locale)
            is LocalDate -> argument.formatDate(locale)
            is Compte -> contexte.getString(argument.libelleRes())
            is TexteUi -> argument.resoudre(contexte)
            else -> argument
        }
    }
    return contexte.getString(id, *formates.toTypedArray())
}

@Composable
fun TexteUi.resoudre(): String {
    // Lire la configuration abonne l'appel a un changement de langue.
    LocalConfiguration.current
    return resoudre(LocalContext.current)
}

@StringRes
fun Compte.libelleRes(): Int = when (this) {
    Compte.CELI -> R.string.compte_celi
    Compte.CELIAPP -> R.string.compte_celiapp
}

@Composable
fun Compte.libelle(): String = stringResource(libelleRes())

@StringRes
fun RaisonSaisie.texteRes(): Int = when (this) {
    RaisonSaisie.MONTANT_NON_POSITIF -> R.string.saisie_montant_non_positif
    RaisonSaisie.PROFIL_ABSENT -> R.string.saisie_profil_absent
    RaisonSaisie.CELI_AVANT_ADMISSIBILITE -> R.string.saisie_celi_avant_admissibilite
    RaisonSaisie.CELIAPP_NON_OUVERT -> R.string.saisie_celiapp_non_ouvert
    RaisonSaisie.CELIAPP_AVANT_OUVERTURE -> R.string.saisie_celiapp_avant_ouverture
    RaisonSaisie.TRANSACTION_INTROUVABLE -> R.string.saisie_transaction_introuvable
}

@StringRes
fun RaisonEchecArc.texteRes(): Int = when (this) {
    RaisonEchecArc.ADRESSE_ABSENTE -> R.string.arc_echec_adresse_absente
    RaisonEchecArc.PAGE_INJOIGNABLE -> R.string.arc_echec_injoignable
    RaisonEchecArc.FORME_INATTENDUE -> R.string.arc_echec_forme
}

@StringRes
fun RaisonRefusAdresse.texteRes(): Int = when (this) {
    RaisonRefusAdresse.HORS_CANADA -> R.string.adresse_refus_hors_canada
    RaisonRefusAdresse.PAGE_INJOIGNABLE -> R.string.adresse_refus_injoignable
    RaisonRefusAdresse.SANS_PLAFOND -> R.string.adresse_refus_sans_plafond
}

@StringRes
fun RaisonImport.texteRes(): Int = when (this) {
    RaisonImport.VERSION_INCONNUE -> R.string.import_version_inconnue
    RaisonImport.JSON_MALFORME -> R.string.import_json_malforme
}
