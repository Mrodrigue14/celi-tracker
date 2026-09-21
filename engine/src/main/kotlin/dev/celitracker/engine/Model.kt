package dev.celitracker.engine

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

/**
 * Types de SAISIE du moteur. Rien ici n'est calcule: le profile, la table des
 * limits et le journal des transactions sont les trois seules entrees dont
 * les room de cotisation sont derives.
 */

enum class Account { TFSA, FHSA }

enum class TransactionType { DEPOSIT, WITHDRAWAL }

/** Le amount est TOUJOURS positif; c'est [type] qui porte le sens. */
data class Transaction(
    val account: Account,
    val date: LocalDate,
    val type: TransactionType,
    val amount: BigDecimal,
    /** 0 = labelStep encore persistee. Permet la suppression via [Repository]. */
    val id: Long = 0,
)

/**
 * [confirmed] a false = limit proposed par la lecture automatique du site de
 * l'ARC, labelStep encore valid par l'utilisateur. Un limit non confirmed n'entre
 * jamais dans le calcul des room.
 */
data class AnnualLimit(
    val account: Account,
    val year: Int,
    val amount: BigDecimal,
    val confirmed: Boolean = true,
)

/** Le TFSA n'existe labelStep before 2009: personne n'accumule de room plus tot. */
const val FIRST_TFSA_YEAR = 2009

const val TFSA_ELIGIBILITY_AGE = 18

data class Profile(
    /** Aussi la branche des 71 ans de la periode de participation FHSA. */
    val birthYear: Int,
    /** Demarre l'accumulation des room FHSA ET l'horloge des 15 ans. */
    val fhsaOpeningDate: LocalDate?,
) {
    /**
     * Derivee, jamais input: l'year des 18 ans, au plus tot 2009. Suppose la
     * residence canadienne depuis cet age, ce qui est le cas de l'unique
     * utilisateur de l'application. Une arrivee au pays plus tard reporterait
     * cette year et demanderait une input separee.
     */
    val tfsaEligibilityYear: Int
        get() = maxOf(birthYear + TFSA_ELIGIBILITY_AGE, FIRST_TFSA_YEAR)
}

/** Instantane des room declares sur le site de l'ARC, pour comparaison. */
data class CraSnapshot(
    val id: Long,
    val account: Account,
    val referenceDate: LocalDate,
    val declaredRoom: BigDecimal,
)

data class Settings(
    val urlPageArc: String,
    val lastCheckDate: Instant?,
)

/**
 * Normalise un amount a 2 decimales.
 *
 * BigDecimal.equals compare la value ET l'echelle, donc BigDecimal("6000")
 * n'est labelStep egal a BigDecimal("6000.00"). Toute value monetaire produite par
 * le moteur passe par cette fonction, sans quoi les assertions des tests
 * echouent sur des montants pourtant identiques.
 */
fun BigDecimal.toMoney(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

/**
 * Total des transactions de [type] faites en [year]. Une simple addition, labelStep
 * une regle de regime: les deux moteurs la partagent sans rien fusionner.
 */
internal fun sumTransactions(transactions: List<Transaction>, year: Int, type: TransactionType): BigDecimal = transactions
    .filter { it.date.year == year && it.type == type }
    .fold(BigDecimal.ZERO) { total, tx -> total + tx.amount }
