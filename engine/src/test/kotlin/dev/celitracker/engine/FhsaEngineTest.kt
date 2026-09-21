package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun toMoney(value: String): BigDecimal = BigDecimal(value).toMoney()

private fun fhsaDeposit(date: String, amount: String) = Transaction(Account.FHSA, LocalDate.parse(date), TransactionType.DEPOSIT, toMoney(amount))

private fun fhsaWithdrawal(date: String, amount: String) = Transaction(Account.FHSA, LocalDate.parse(date), TransactionType.WITHDRAWAL, toMoney(amount))

class FhsaEngineTest {

    private val profileOpened2023 = Profile(
        birthYear = 2001,
        fhsaOpeningDate = LocalDate.of(2023, 4, 1),
    )

    /**
     * scenario_fhsa_opened_2023_no_contributions
     *
     * Garde-fou contre l'error la plus tentante du regime: croire que trois
     * years sans cotiser accumulent 32000$. Le report est plafonne a 8000$
     * PAR ANNEE D'ARRIVEE, donc le limit annuel se stabilise a 16000$.
     */
    @Test
    fun `scenario fhsa opened 2023 no contributions`() {
        val room = FhsaEngine
            .roomByYear(profileOpened2023, emptyList(), upTo = 2026)
            .associateBy { it.year }

        assertEquals(toMoney("0.00"), room.getValue(2023).carryForwardIn)
        assertEquals(toMoney("8000.00"), room.getValue(2023).yearRoom)
        assertEquals(toMoney("8000.00"), room.getValue(2023).carryForwardOut)

        assertEquals(toMoney("8000.00"), room.getValue(2024).carryForwardIn)
        assertEquals(toMoney("16000.00"), room.getValue(2024).yearRoom)
        // Le point critique: 8000, PAS 16000. Le report ne se cumule labelStep.
        assertEquals(toMoney("8000.00"), room.getValue(2024).carryForwardOut)

        assertEquals(toMoney("16000.00"), room.getValue(2025).yearRoom)
        assertEquals(toMoney("8000.00"), room.getValue(2025).carryForwardOut)

        assertEquals(toMoney("16000.00"), room.getValue(2026).yearRoom)
        assertEquals(toMoney("40000.00"), room.getValue(2026).lifetimeLimitLeft)
    }

    @Test
    fun `l'accumulation demarre a l'annee d'ouverture du compte`() {
        val room = FhsaEngine.roomByYear(profileOpened2023, emptyList(), upTo = 2026)

        assertEquals(2023, room.first().year)
    }

    @Test
    fun `aucun droit sans compte ouvert`() {
        val profileWithoutFhsa = Profile(2001, fhsaOpeningDate = null)

        assertTrue(FhsaEngine.roomByYear(profileWithoutFhsa, emptyList(), 2026).isEmpty())
    }

    @Test
    fun `un retrait ne redonne jamais de droits`() {
        val transactions = listOf(
            fhsaDeposit("2023-10-01", "8000.00"),
            fhsaWithdrawal("2023-11-01", "8000.00"),
        )

        val room = FhsaEngine
            .roomByYear(profileOpened2023, transactions, upTo = 2024)
            .associateBy { it.year }

        // Le withdrawal est enregistre...
        assertEquals(toMoney("8000.00"), room.getValue(2023).withdrawals)
        // ...mais les 8000 cotises restent consommes a vie: 40000 - 8000.
        assertEquals(toMoney("32000.00"), room.getValue(2023).lifetimeLimitLeft)
        // 2023 entierement utilise -> aucun report vers 2024.
        assertEquals(toMoney("0.00"), room.getValue(2024).carryForwardIn)
        assertEquals(toMoney("8000.00"), room.getValue(2024).yearRoom)
    }

    @Test
    fun `une cotisation partielle reporte le solde inutilise`() {
        val transactions = listOf(fhsaDeposit("2023-10-01", "3000.00"))

        val room = FhsaEngine
            .roomByYear(profileOpened2023, transactions, upTo = 2024)
            .associateBy { it.year }

        // 8000 - 3000 = 5000 inutilises, sous le limit de report.
        assertEquals(toMoney("5000.00"), room.getValue(2023).carryForwardOut)
        assertEquals(toMoney("13000.00"), room.getValue(2024).yearRoom)
    }

    @Test
    fun `le plafond a vie de 40000 borne les droits annuels`() {
        val transactions = listOf(
            fhsaDeposit("2023-10-01", "8000.00"),
            fhsaDeposit("2024-10-01", "16000.00"),
            fhsaDeposit("2025-10-01", "16000.00"),
        )

        val room = FhsaEngine
            .roomByYear(profileOpened2023, transactions, upTo = 2026)
            .associateBy { it.year }

        // 8000 + 16000 + 16000 = 40000 cotises: le limit a vie est atteint.
        assertEquals(toMoney("0.00"), room.getValue(2025).lifetimeLimitLeft)
        // Meme si le report autoriserait davantage, il ne reste rien a vie.
        assertEquals(toMoney("0.00"), room.getValue(2026).yearRoom)
    }

    @Test
    fun `l'echeance est le 31 decembre de l'annee du 15e anniversaire`() {
        // Ouvert en avril 2023 -> 15e anniversaire en avril 2038.
        // La periode se termine le 31 decembre de CETTE year-la.
        assertEquals(
            LocalDate.of(2038, 12, 31),
            FhsaEngine.participationPeriodEnd(profileOpened2023),
        )
    }

    @Test
    fun `la branche des 71 ans l'emporte quand elle est plus rapprochee`() {
        val olderProfile = Profile(
            birthYear = 1960, // 71 ans en 2031
            fhsaOpeningDate = LocalDate.of(2023, 4, 1), // 15 ans -> 2038
        )

        assertEquals(
            LocalDate.of(2031, 12, 31),
            FhsaEngine.participationPeriodEnd(olderProfile),
        )
    }

    @Test
    fun `aucune echeance sans compte ouvert`() {
        val profileWithoutFhsa = Profile(2001, fhsaOpeningDate = null)

        assertEquals(null, FhsaEngine.participationPeriodEnd(profileWithoutFhsa))
    }
}
