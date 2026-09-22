package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsageTest {

    private fun usage(contributed: String) = Usage(room = BigDecimal("10000.00"), contributed = BigDecimal(contributed))

    @Test
    fun `under 80 percent, nothing to flag`() {
        assertEquals(UsageLevel.NORMAL, usage("7999.99").level)
    }

    @Test
    fun `80 percent exactly triggers the warning`() {
        assertEquals(UsageLevel.WARNING, usage("8000.00").level)
    }

    @Test
    fun `95 percent exactly is critical`() {
        assertEquals(UsageLevel.CRITICAL, usage("9500.00").level)
    }

    @Test
    fun `using exactly all of one's room is not an over-contribution`() {
        assertEquals(UsageLevel.CRITICAL, usage("10000.00").level)
    }

    @Test
    fun `one cent over is an over-contribution`() {
        val exceeded = usage("10000.01")

        assertEquals(UsageLevel.EXCEEDED, exceeded.level)
        assertEquals(BigDecimal("0.01"), exceeded.excess)
        assertEquals(BigDecimal("0.00"), exceeded.remaining)
    }

    @Test
    fun `the percentage is rounded to a whole number`() {
        assertEquals(85, usage("8450.00").percent)
    }

    @Test
    fun `without room, no percentage, and any deposit is an overage`() {
        val withoutRoom = Usage(room = BigDecimal.ZERO, contributed = BigDecimal.ZERO)

        assertNull(withoutRoom.percent)
        assertEquals(UsageLevel.NORMAL, withoutRoom.level)
        assertEquals(UsageLevel.EXCEEDED, withoutRoom.withDeposit(BigDecimal("1.00")).level)
    }

    @Test
    fun `TFSA usage comes from january 1 room and the year's deposits`() {
        // 2008 + 18 = 2026.
        val profile = Profile(birthYear = 2008, fhsaOpeningDate = null)
        val limits = listOf(AnnualLimit(Account.TFSA, 2026, BigDecimal("7000.00")))
        val deposit = Transaction(Account.TFSA, LocalDate.of(2026, 3, 1), TransactionType.DEPOSIT, BigDecimal("6000.00"))

        val usage = tfsaUsage(profile, limits, listOf(deposit), 2026)

        assertEquals(Usage(BigDecimal("7000.00"), BigDecimal("6000.00")), usage)
    }

    @Test
    fun `without an open FHSA, no usage`() {
        val profile = Profile(birthYear = 2000, fhsaOpeningDate = null)

        assertNull(fhsaUsage(profile, emptyList(), 2026))
    }

    @Test
    fun `without a known limit, TFSA usage is unknown rather than wrong`() {
        val profile = Profile(birthYear = 2008, fhsaOpeningDate = null)
        val deposit = Transaction(Account.TFSA, LocalDate.of(2026, 3, 1), TransactionType.DEPOSIT, BigDecimal("100.00"))

        assertNull(tfsaUsage(profile, emptyList(), listOf(deposit), 2026))
    }

    @Test
    fun `a limit missing after the requested year does not hide that year's usage`() {
        val limits = AcceptanceScenario.limits.filter { it.year != 2026 }
        val rows = TfsaEngine.roomByYear(AcceptanceScenario.profile, limits, AcceptanceScenario.deposits, 2026)

        assertEquals(money("29000.00"), tfsaUsage(rows, 2024)?.room)
    }
}
