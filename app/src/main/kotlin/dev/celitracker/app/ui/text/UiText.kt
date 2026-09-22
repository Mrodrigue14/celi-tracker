package dev.celitracker.app.ui.text

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.celitracker.app.R
import dev.celitracker.app.ui.format.formatAmount
import dev.celitracker.app.ui.format.formatDate
import dev.celitracker.data.AddressRejectionReason
import dev.celitracker.data.CraFailureReason
import dev.celitracker.data.ImportFailureReason
import dev.celitracker.data.InputRejectionReason
import dev.celitracker.engine.Account
import dev.celitracker.engine.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/** Resource plus arguments, resolved at display time so amounts and dates follow the sentence's language. */
data class UiText(@StringRes val id: Int, val arguments: List<Any> = emptyList())

fun uiText(@StringRes id: Int, vararg arguments: Any) = UiText(id, arguments.toList())

fun UiText.resolve(context: Context): String {
    val locale = context.resources.configuration.locales[0]
    val formatted = arguments.map { argument ->
        when (argument) {
            is BigDecimal -> argument.formatAmount(locale)
            is LocalDate -> argument.formatDate(locale)
            is Account -> context.getString(argument.labelRes())
            is UiText -> argument.resolve(context)
            else -> argument
        }
    }
    return context.getString(id, *formatted.toTypedArray())
}

@Composable
fun UiText.resolve(): String {
    // Reading the configuration subscribes the call to a language change.
    LocalConfiguration.current
    return resolve(LocalContext.current)
}

@StringRes
fun Account.labelRes(): Int = when (this) {
    Account.TFSA -> R.string.account_tfsa
    Account.FHSA -> R.string.account_fhsa
}

@Composable
fun Account.label(): String = stringResource(labelRes())

@Composable
fun TransactionType.label(): String = stringResource(
    when (this) {
        TransactionType.DEPOSIT -> R.string.type_deposit
        TransactionType.WITHDRAWAL -> R.string.type_withdrawal
    },
)

@StringRes
fun InputRejectionReason.textRes(): Int = when (this) {
    InputRejectionReason.NON_POSITIVE_AMOUNT -> R.string.input_non_positive_amount
    InputRejectionReason.MISSING_PROFILE -> R.string.input_missing_profile
    InputRejectionReason.TFSA_BEFORE_ELIGIBILITY -> R.string.input_tfsa_before_eligibility
    InputRejectionReason.FHSA_NOT_OPENED -> R.string.input_fhsa_not_opened
    InputRejectionReason.FHSA_BEFORE_OPENING -> R.string.input_fhsa_before_opening
    InputRejectionReason.TRANSACTION_NOT_FOUND -> R.string.input_transaction_not_found
}

@StringRes
fun CraFailureReason.textRes(): Int = when (this) {
    CraFailureReason.MISSING_ADDRESS -> R.string.cra_failure_missing_address
    CraFailureReason.PAGE_UNREACHABLE -> R.string.cra_failure_unreachable
    CraFailureReason.UNEXPECTED_FORMAT -> R.string.cra_failure_format
}

@StringRes
fun AddressRejectionReason.textRes(): Int = when (this) {
    AddressRejectionReason.OUTSIDE_CANADA -> R.string.address_rejected_outside_canada
    AddressRejectionReason.PAGE_UNREACHABLE -> R.string.address_rejected_unreachable
    AddressRejectionReason.NO_LIMIT_FOUND -> R.string.address_rejected_no_limit
}

@StringRes
fun ImportFailureReason.textRes(): Int = when (this) {
    ImportFailureReason.UNKNOWN_VERSION -> R.string.import_unknown_version
    ImportFailureReason.MALFORMED_JSON -> R.string.import_malformed_json
    ImportFailureReason.INVALID_TRANSACTION -> R.string.import_invalid_transaction
}
