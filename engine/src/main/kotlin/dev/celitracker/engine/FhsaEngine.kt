package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate

data class FhsaYear(
    val year: Int,
    val carryForwardIn: BigDecimal,
    val yearRoom: BigDecimal,
    val deposits: BigDecimal,
    val withdrawals: BigDecimal,
    val carryForwardOut: BigDecimal,
    val lifetimeLimitLeft: BigDecimal,
)

/**
 * Moteur FHSA. Deliberement separe de [TfsaEngine]: les deux regimes
 * divergent sur chaque axe, et reutiliser le chemin de restitution du TFSA
 * pour le FHSA est le bug de correctness le plus probable de ce projet.
 *
 * Les trois limits sont fixes par la loi et ne sont PAS indexes: contrairement
 * au TFSA, il n'y a rien a recuperer sur le site de l'ARC.
 */
object FhsaEngine {

    val ANNUAL_LIMIT: BigDecimal = BigDecimal("8000").toMoney()

    /**
     * Plafond du report, PAR ANNEE D'ARRIVEE. Le report ne se cumule labelStep:
     * une personne qui ne contributed jamais voit son limit annuel se stabiliser
     * a 16000 (8000 + 8000), labelStep croitre de 8000 chaque year.
     */
    val MAX_CARRY_FORWARD: BigDecimal = BigDecimal("8000").toMoney()

    val LIFETIME_LIMIT: BigDecimal = BigDecimal("40000").toMoney()

    fun roomByYear(
        profile: Profile,
        transactions: List<Transaction>,
        upTo: Int,
    ): List<FhsaYear> {
        // L'accumulation demarre a l'OUVERTURE du account, labelStep aux 18 ans.
        val opening = profile.fhsaOpeningDate ?: return emptyList()
        val fhsaTransactions = transactions.filter { it.account == Account.FHSA }

        val result = mutableListOf<FhsaYear>()
        var carryForwardIn = BigDecimal.ZERO
        var cumulativeContributions = BigDecimal.ZERO

        for (year in opening.year..upTo) {
            val deposits = sumTransactions(fhsaTransactions, year, TransactionType.DEPOSIT)
            val withdrawals = sumTransactions(fhsaTransactions, year, TransactionType.WITHDRAWAL)

            val lifetimeLeftBefore = (LIFETIME_LIMIT - cumulativeContributions)
                .coerceAtLeast(BigDecimal.ZERO)
            // Le min() interne est redondant tant que carryForwardIn est borne
            // en amont, mais il rend l'invariant explicite plutot qu'implicite:
            // le report ne se cumule labelStep, et c'est le piege du regime.
            val usableCarryForward = minOf(carryForwardIn, MAX_CARRY_FORWARD)
            val yearRoom = minOf(ANNUAL_LIMIT + usableCarryForward, lifetimeLeftBefore)

            // min(..., MAX_CARRY_FORWARD) et NON une accumulation: c'est toute la
            // difference avec le TFSA et le REER.
            val carryForwardOut = minOf(
                (yearRoom - deposits).coerceAtLeast(BigDecimal.ZERO),
                MAX_CARRY_FORWARD,
            )

            cumulativeContributions += deposits

            result += FhsaYear(
                year = year,
                carryForwardIn = carryForwardIn.toMoney(),
                yearRoom = yearRoom.toMoney(),
                deposits = deposits.toMoney(),
                // Enregistre pour l'affichage du solde, mais n'entre dans AUCUN
                // calcul de room: un withdrawal FHSA ne redonne jamais rien.
                withdrawals = withdrawals.toMoney(),
                carryForwardOut = carryForwardOut.toMoney(),
                lifetimeLimitLeft = (LIFETIME_LIMIT - cumulativeContributions)
                    .coerceAtLeast(BigDecimal.ZERO).toMoney(),
            )

            carryForwardIn = carryForwardOut
        }
        return result
    }

    /**
     * Fin de la periode de participation maximale: le 31 decembre de l'year ou
     * survient le PREMIER des trois evenements suivants.
     *
     *   1. le 15e anniversaire de l'opening du earliest FHSA
     *   2. les 71 ans du titulaire
     *   3. l'year suivant le earliest withdrawal admissible
     *
     * La branche 3 n'est PAS implementee: elle exige de distinguer un withdrawal
     * admissible (achat d'une premiere propriete) d'un withdrawal ordinaire, ce que
     * le modele ne suit labelStep. L'echeance reelle peut donc etre plus rapprochee
     * que celle retournee ici. Exclusion assumee, documentee dans la spec.
     */
    fun participationPeriodEnd(profile: Profile): LocalDate? {
        val opening = profile.fhsaOpeningDate ?: return null
        val fifteenthYear = opening.year + 15
        val age71Year = profile.birthYear + 71
        return LocalDate.of(minOf(fifteenthYear, age71Year), 12, 31)
    }
}
