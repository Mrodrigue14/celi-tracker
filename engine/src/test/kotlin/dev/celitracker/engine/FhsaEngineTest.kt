package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FhsaEngineTest {

    private val profileOpened2023 = Profile(
        birthYear = 2001,
        fhsaOpeningDate = LocalDate.of(2023, 4, 1),
    )

    @Test
    fun `scenario fhsa opened 2023 no contributions`() {
        val room = FhsaEngine
            .roomByYear(profileOpened2023, emptyList(), upTo = 2026)
            .associateBy { it.year }

        assertEquals(money("0.00"), room.getValue(2023).carryForwardIn)
        assertEquals(money("8000.00"), room.getValue(2023).yearRoom)
        assertEquals(money("8000.00"), room.getValue(2023).carryForwardOut)

        assertEquals(money("8000.00"), room.getValue(2024).carryForwardIn)
        assertEquals(money("16000.00"), room.getValue(2024).yearRoom)
        // 8000, not 16000: the carry-forward does not accumulate.
        assertEquals(money("8000.00"), room.getValue(2024).carryForwardOut)

        assertEquals(money("16000.00"), room.getValue(2025).yearRoom)
        assertEquals(money("8000.00"), room.getValue(2025).carryForwardOut)

        assertEquals(money("16000.00"), room.getValue(2026).yearRoom)
        assertEquals(money("40000.00"), room.getValue(2026).lifetimeLimitLeft)
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
            deposit("2023-10-01", "8000.00", Account.FHSA),
            withdrawal("2023-11-01", "8000.00", Account.FHSA),
        )

        val room = FhsaEngine
            .roomByYear(profileOpened2023, transactions, upTo = 2024)
            .associateBy { it.year }

        assertEquals(money("8000.00"), room.getValue(2023).withdrawals)
        // 40000 - 8000: contributions stay consumed for life.
        assertEquals(money("32000.00"), room.getValue(2023).lifetimeLimitLeft)
        assertEquals(money("0.00"), room.getValue(2024).carryForwardIn)
        assertEquals(money("8000.00"), room.getValue(2024).yearRoom)
    }

    @Test
    fun `a partial contribution carries forward the unused balance`() {
        val transactions = listOf(deposit("2023-10-01", "3000.00", Account.FHSA))

        val room = FhsaEngine
            .roomByYear(profileOpened2023, transactions, upTo = 2024)
            .associateBy { it.year }

        // 8000 - 3000 = 5000 unused.
        assertEquals(money("5000.00"), room.getValue(2023).carryForwardOut)
        assertEquals(money("13000.00"), room.getValue(2024).yearRoom)
    }

    @Test
    fun `the 40000 lifetime limit caps the annual room`() {
        val transactions = listOf(
            deposit("2023-10-01", "8000.00", Account.FHSA),
            deposit("2024-10-01", "16000.00", Account.FHSA),
            deposit("2025-10-01", "16000.00", Account.FHSA),
        )

        val room = FhsaEngine
            .roomByYear(profileOpened2023, transactions, upTo = 2026)
            .associateBy { it.year }

        // 8000 + 16000 + 16000 = 40000.
        assertEquals(money("0.00"), room.getValue(2025).lifetimeLimitLeft)
        assertEquals(money("0.00"), room.getValue(2026).yearRoom)
    }

    @Test
    fun `the deadline is december 31 of the 15th anniversary year`() {
        assertEquals(
            LocalDate.of(2038, 12, 31),
            FhsaEngine.participationPeriodEnd(profileOpened2023),
        )
    }

    @Test
    fun `the age 71 branch wins when it comes sooner`() {
        val olderProfile = Profile(
            birthYear = 1960, // 1960 + 71 = 2031
            fhsaOpeningDate = LocalDate.of(2023, 4, 1), // 2023 + 15 = 2038
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
