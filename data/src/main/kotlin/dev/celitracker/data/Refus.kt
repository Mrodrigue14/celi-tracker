package dev.celitracker.data

/*
 * Les refus de ce module sont des valeurs, jamais des phrases: `:data` n'a pas
 * acces aux ressources Android, et une phrase ecrite ici resterait en francais
 * sur un appareil en anglais. C'est `:app` qui traduit chaque raison.
 */

enum class RaisonSaisie {
    MONTANT_NON_POSITIF,
    PROFIL_ABSENT,
    CELI_AVANT_ADMISSIBILITE,
    CELIAPP_NON_OUVERT,
    CELIAPP_AVANT_OUVERTURE,
    TRANSACTION_INTROUVABLE,
}

/** Sous-classe d'IllegalArgumentException: un `require` echoue reste attrape pareil. */
class SaisieInvalide(val raison: RaisonSaisie) : IllegalArgumentException(raison.name)

enum class RaisonImport { VERSION_INCONNUE, JSON_MALFORME }

class ImportInvalide(val raison: RaisonImport, cause: Throwable? = null) : IllegalArgumentException(raison.name, cause)

enum class RaisonEchecArc { ADRESSE_ABSENTE, PAGE_INJOIGNABLE, FORME_INATTENDUE }

enum class RaisonRefusAdresse { HORS_CANADA, PAGE_INJOIGNABLE, SANS_PLAFOND }

internal fun refuserSaisie(raison: RaisonSaisie): Nothing = throw SaisieInvalide(raison)
