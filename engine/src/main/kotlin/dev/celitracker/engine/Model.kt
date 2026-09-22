package dev.celitracker.engine

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

enum class Account { TFSA, FHSA }

enum class TransactionType { DEPOSIT, WITHDRAWAL }

const val UNSAVED_ID = 0L

/** The amount is ALWAYS positive; [type] is what carries the sign. */
data class Transaction(
    val account: Account,
    val date: LocalDate,
    val type: TransactionType,
    val amount: BigDecimal,
    val id: Long = UNSAVED_ID,
)

/** False when read from the CRA site and not yet validated: it never enters the room calculation. */
data class AnnualLimit(
    val account: Account,
    val year: Int,
    val amount: BigDecimal,
    val confirmed: Boolean = true,
)

const val FIRST_TFSA_YEAR = 2009

const val TFSA_ELIGIBILITY_AGE = 18

data class Profile(
    val birthYear: Int,
    val fhsaOpeningDate: LocalDate?,
) {
    val tfsaEligibilityYear: Int get() = tfsaEligibilityYear(birthYear)
}

/** Derived, never entered. Assumes Canadian residency since age 18. */
fun tfsaEligibilityYear(birthYear: Int): Int = maxOf(birthYear + TFSA_ELIGIBILITY_AGE, FIRST_TFSA_YEAR)

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

/** Fixed scale of 2, because BigDecimal.equals compares scale: 6000 is not equal to 6000.00. */
fun BigDecimal.toMoney(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

internal fun sumTransactions(transactions: List<Transaction>, year: Int, type: TransactionType): BigDecimal = transactions
    .filter { it.date.year == year && it.type == type }
    .fold(BigDecimal.ZERO) { total, tx -> total + tx.amount }
