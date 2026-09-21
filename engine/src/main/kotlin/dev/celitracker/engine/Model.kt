package dev.celitracker.engine

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

/**
 * Input types for the engine. Nothing here is computed: the profile,
 * the limits table and the transaction log are the only three inputs
 * from which contribution room is derived.
 */

enum class Account { TFSA, FHSA }

enum class TransactionType { DEPOSIT, WITHDRAWAL }

/** The amount is ALWAYS positive; [type] is what carries the sign. */
data class Transaction(
    val account: Account,
    val date: LocalDate,
    val type: TransactionType,
    val amount: BigDecimal,
    /** 0 = not yet persisted. Enables deletion via [Repository]. */
    val id: Long = 0,
)

/**
 * [confirmed] false means the limit was proposed by automatically
 * reading the CRA site, not yet validated by the user. An unconfirmed
 * limit never enters the room calculation.
 */
data class AnnualLimit(
    val account: Account,
    val year: Int,
    val amount: BigDecimal,
    val confirmed: Boolean = true,
)

/** The TFSA did not exist before 2009: nobody accumulates room earlier than that. */
const val FIRST_TFSA_YEAR = 2009

const val TFSA_ELIGIBILITY_AGE = 18

data class Profile(
    /** Also the age-71 branch of the FHSA participation period. */
    val birthYear: Int,
    /** Starts both the FHSA room accumulation and the 15-year clock. */
    val fhsaOpeningDate: LocalDate?,
) {
    /**
     * Derived, never entered directly: the year of turning 18, no
     * earlier than 2009. Assumes Canadian residency since that age,
     * which is true for the app's single user. A later arrival in the
     * country would push this year back and require a separate input.
     */
    val tfsaEligibilityYear: Int
        get() = maxOf(birthYear + TFSA_ELIGIBILITY_AGE, FIRST_TFSA_YEAR)
}

/** Snapshot of the room declared on the CRA site, for comparison. */
data class CraSnapshot(
    val id: Long,
    val account: Account,
    val referenceDate: LocalDate,
    val declaredRoom: BigDecimal,
)

data class Settings(
    val craPageUrl: String,
    val lastCheckDate: Instant?,
)

/**
 * Normalizes an amount to 2 decimal places.
 *
 * BigDecimal.equals compares both the value AND the scale, so
 * BigDecimal("6000") is not equal to BigDecimal("6000.00"). Every
 * monetary value the engine produces goes through this function,
 * otherwise test assertions fail on amounts that are actually identical.
 */
fun BigDecimal.toMoney(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

/**
 * Total of the [type] transactions made in [year]. A plain sum, not a
 * regime rule: both engines share it without merging anything.
 */
internal fun sumTransactions(transactions: List<Transaction>, year: Int, type: TransactionType): BigDecimal = transactions
    .filter { it.date.year == year && it.type == type }
    .fold(BigDecimal.ZERO) { total, tx -> total + tx.amount }
