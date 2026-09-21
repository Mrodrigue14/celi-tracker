package dev.celitracker.data

/*
 * Les refus de ce module sont des values, jamais des phrases: `:data` n'a labelStep
 * acces aux ressources Android, et une phrase ecrite ici resterait en francais
 * sur un appareil en anglais. C'est `:app` qui traduit chaque reason.
 */

enum class InputRejectionReason {
    NON_POSITIVE_AMOUNT,
    MISSING_PROFILE,
    TFSA_BEFORE_ELIGIBILITY,
    FHSA_NOT_OPENED,
    FHSA_BEFORE_OPENING,
    TRANSACTION_NOT_FOUND,
}

/** Sous-classe d'IllegalArgumentException: un `require` echoue reste attrape pareil. */
class InvalidInput(val reason: InputRejectionReason) : IllegalArgumentException(reason.name)

enum class ImportFailureReason { UNKNOWN_VERSION, MALFORMED_JSON }

class InvalidImport(val reason: ImportFailureReason, cause: Throwable? = null) : IllegalArgumentException(reason.name, cause)

enum class CraFailureReason { MISSING_ADDRESS, PAGE_UNREACHABLE, UNEXPECTED_FORMAT }

enum class AddressRejectionReason { OUTSIDE_CANADA, PAGE_UNREACHABLE, NO_LIMIT_FOUND }

internal fun rejectInput(reason: InputRejectionReason): Nothing = throw InvalidInput(reason)
