package dev.celitracker.engine

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

/**
 * Types de SAISIE du moteur. Rien ici n'est calcule: le profil, la table des
 * plafonds et le journal des transactions sont les trois seules entrees dont
 * les droits de cotisation sont derives.
 */

enum class Compte { CELI, CELIAPP }

enum class TypeTx { DEPOT, RETRAIT }

/** Le montant est TOUJOURS positif; c'est [type] qui porte le sens. */
data class Transaction(
    val compte: Compte,
    val date: LocalDate,
    val type: TypeTx,
    val montant: BigDecimal,
    /** 0 = pas encore persistee. Permet la suppression via [Depot]. */
    val id: Long = 0,
)

/**
 * [confirme] a false = plafond propose par la lecture automatique du site de
 * l'ARC, pas encore valide par l'utilisateur. Un plafond non confirme n'entre
 * jamais dans le calcul des droits.
 */
data class PlafondAnnuel(
    val compte: Compte,
    val annee: Int,
    val montant: BigDecimal,
    val confirme: Boolean = true,
)

/** Le CELI n'existe pas avant 2009: personne n'accumule de droits plus tot. */
const val PREMIERE_ANNEE_CELI = 2009

const val AGE_ADMISSIBILITE_CELI = 18

data class Profil(
    /** Aussi la branche des 71 ans de la periode de participation CELIAPP. */
    val anneeNaissance: Int,
    /** Demarre l'accumulation des droits CELIAPP ET l'horloge des 15 ans. */
    val dateOuvertureCeliapp: LocalDate?,
) {
    /**
     * Derivee, jamais saisie: l'annee des 18 ans, au plus tot 2009. Suppose la
     * residence canadienne depuis cet age, ce qui est le cas de l'unique
     * utilisateur de l'application. Une arrivee au pays plus tard reporterait
     * cette annee et demanderait une saisie separee.
     */
    val anneeAdmissibiliteCeli: Int
        get() = maxOf(anneeNaissance + AGE_ADMISSIBILITE_CELI, PREMIERE_ANNEE_CELI)
}

/** Instantane des droits declares sur le site de l'ARC, pour comparaison. */
data class SnapshotArc(
    val id: Long,
    val compte: Compte,
    val dateReference: LocalDate,
    val droitsDeclares: BigDecimal,
)

data class Reglages(
    val urlPageArc: String,
    val dateDerniereVerification: Instant?,
)

/**
 * Normalise un montant a 2 decimales.
 *
 * BigDecimal.equals compare la valeur ET l'echelle, donc BigDecimal("6000")
 * n'est pas egal a BigDecimal("6000.00"). Toute valeur monetaire produite par
 * le moteur passe par cette fonction, sans quoi les assertions des tests
 * echouent sur des montants pourtant identiques.
 */
fun BigDecimal.argent(): BigDecimal = setScale(2, RoundingMode.HALF_UP)

/**
 * Total des transactions de [type] faites en [annee]. Une simple addition, pas
 * une regle de regime: les deux moteurs la partagent sans rien fusionner.
 */
internal fun sommeTransactions(transactions: List<Transaction>, annee: Int, type: TypeTx): BigDecimal = transactions
    .filter { it.date.year == annee && it.type == type }
    .fold(BigDecimal.ZERO) { total, tx -> total + tx.montant }
