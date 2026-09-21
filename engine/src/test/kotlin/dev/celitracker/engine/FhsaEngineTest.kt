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
     * Guard against the regime's most tempting mistake: believing that
     * three years without contributing accumulate $32000. The
     * carry-forward is capped at $8000 PER YEAR OF ARRIVAL, so the
     * annual limit stabilizes at $16000.
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
        // The critical point: 8000, NOT 16000. The carry-forward does not accumulate.
        assertEquals(toMoney("8000.00"), room.getValue(2024).carryForwardOut)

        assertEquals(toMoney("16000.00"), room.getValue(2025).yearRoom)
        assertEquals(toMoney("8000.00"), room.getValue(2025).carryForwardOut)

        assertEquals(toMoney("16000.00"), room.getValue(2026).yearRoom)
        assertEquals(toMoney("40000.00"), room.getValue(2026).lifetimeLimitLeft)
    }

    @Test
    fun `accumulation starts in the year the account opens`() {
        val room = FhsaEngine.roomByYear(profileOpened2023, emptyList(), upTo = 2026)

        assertEquals(2023, room.first().year)
    }

    @Test
    fun `no room without an open account`() {
        val profileWithoutFhsa = Profile(2001, fhsaOpeningDate = null)

        assertTrue(FhsaEngine.roomByYear(profileWithoutFhsa, emptyList(), 2026).isEmpty())
    }

    @Test
    fun `a withdrawal never restores room`() {
        val transactions = listOf(
            fhsaDeposit("2023-10-01", "8000.00"),
            fhsaWithdrawal("2023-11-01", "8000.00"),
        )

        val room = FhsaEngine
            .roomByYear(profileOpened2023, transactions, upTo = 2024)
            .associateBy { it.year }

        // The withdrawal is recorded...
        assertEquals(toMoney("8000.00"), room.getValue(2023).withdrawals)
        // ...but the 8000 contributed remain consumed for life: 40000 - 8000.
        assertEquals(toMoney("32000.00"), room.getValue(2023).lifetimeLimitLeft)
        // 2023 fully used -> no carry-forward to 2024.
        assertEquals(toMoney("0.00"), room.getValue(2024).carryForwardIn)
        assertEquals(toMoney("8000.00"), room.getValue(2024).yearRoom)
    }

    @Test
    fun `a partial contribution carries forward the unused balance`() {
        val transactions = listOf(fhsaDeposit("2023-10-01", "3000.00"))

        val room = FhsaEngine
            .roomByYear(profileOpened2023, transactions, upTo = 2024)
            .associateBy { it.year }

        // 8000 - 3000 = 5000 unused, under the carry-forward limit.
        assertEquals(toMoney("5000.00"), room.getValue(2023).carryForwardOut)
        assertEquals(toMoney("13000.00"), room.getValue(2024).yearRoom)
    }

    @Test
    fun `the 40000 lifetime limit caps the annual room`() {
        val transactions = listOf(
            fhsaDeposit("2023-10-01", "8000.00"),
            fhsaDeposit("2024-10-01", "16000.00"),
            fhsaDeposit("2025-10-01", "16000.00"),
        )

        val room = FhsaEngine
            .roomByYear(profileOpened2023, transactions, upTo = 2026)
            .associateBy { it.year }

        // 8000 + 16000 + 16000 = 40000 contributed: the lifetime limit is reached.
        assertEquals(toMoney("0.00"), room.getValue(2025).lifetimeLimitLeft)
        // Even though the carry-forward would allow more, nothing is left for life.
        assertEquals(toMoney("0.00"), room.getValue(2026).yearRoom)
    }

    @Test
    fun `the deadline is december 31 of the 15th anniversary year`() {
        // Opened in April 2023 -> 15th anniversary in April 2038.
        // The period ends december 31 of THAT year.
        assertEquals(
            LocalDate.of(2038, 12, 31),
            FhsaEngine.participationPeriodEnd(profileOpened2023),
        )
    }

    @Test
    fun `the age 71 branch wins when it comes sooner`() {
        val olderProfile = Profile(
            birthYear = 1960, // 71 years old in 2031
            fhsaOpeningDate = LocalDate.of(2023, 4, 1), // 15 years -> 2038
        )

        assertEquals(
            LocalDate.of(2031, 12, 31),
            FhsaEngine.participationPeriodEnd(olderProfile),
        )
    }

    @Test
    fun `no deadline without an open account`() {
        val profileWithoutFhsa = Profile(2001, fhsaOpeningDate = null)

        assertEquals(null, FhsaEngine.participationPeriodEnd(profileWithoutFhsa))
    }
}
