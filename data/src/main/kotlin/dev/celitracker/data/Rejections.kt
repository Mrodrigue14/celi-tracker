package dev.celitracker.data

// Values, never sentences: `:data` has no Android resources, so `:app` translates each reason.

enum class InputRejectionReason {
    NON_POSITIVE_AMOUNT,
    MISSING_PROFILE,
    TFSA_BEFORE_ELIGIBILITY,
    FHSA_NOT_OPENED,
    FHSA_BEFORE_OPENING,
    TRANSACTION_NOT_FOUND,
}

/** An IllegalArgumentException, so it is caught like a failed `require`. */
class InvalidInput(val reason: InputRejectionReason) : IllegalArgumentException(reason.name)

enum class ImportFailureReason { UNKNOWN_VERSION, MALFORMED_JSON, INVALID_TRANSACTION }

class InvalidImport(val reason: ImportFailureReason, cause: Throwable? = null) : IllegalArgumentException(reason.name, cause)

enum class CraFailureReason { MISSING_ADDRESS, PAGE_UNREACHABLE, UNEXPECTED_FORMAT }

enum class AddressRejectionReason { OUTSIDE_CANADA, PAGE_UNREACHABLE, NO_LIMIT_FOUND }

internal fun rejectInput(reason: InputRejectionReason): Nothing = throw InvalidInput(reason)
