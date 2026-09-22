package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TfsaEngineTest {

    // Limits 51500 - deposits 9700 = 41800.
    @Test
    fun `scenario 2019 eligible three deposits`() {
        val room = TfsaEngine.roomByYear(AcceptanceScenario.profile, AcceptanceScenario.limits, AcceptanceScenario.deposits, upTo = 2026)
            .associateBy { it.year }

        assertEquals(money("6000.00"), room.getValue(2019).startRoom)
        assertEquals(money("6000.00"), room.getValue(2019).endRoom)

        assertEquals(money("12000.00"), room.getValue(2020).startRoom)
        assertEquals(money("12000.00"), room.getValue(2020).endRoom)

        assertEquals(money("18000.00"), room.getValue(2021).startRoom)
        assertEquals(money("5000.00"), room.getValue(2021).deposits)
        assertEquals(money("13000.00"), room.getValue(2021).endRoom)

        assertEquals(money("19000.00"), room.getValue(2022).startRoom)
        assertEquals(money("19000.00"), room.getValue(2022).endRoom)

        assertEquals(money("25500.00"), room.getValue(2023).startRoom)
        assertEquals(money("3500.00"), room.getValue(2023).deposits)
        assertEquals(money("22000.00"), room.getValue(2023).endRoom)

        assertEquals(money("29000.00"), room.getValue(2024).startRoom)
        assertEquals(money("1200.00"), room.getValue(2024).deposits)
        assertEquals(money("27800.00"), room.getValue(2024).endRoom)

        assertEquals(money("34800.00"), room.getValue(2025).startRoom)
        assertEquals(money("34800.00"), room.getValue(2025).endRoom)

        assertEquals(money("41800.00"), room.getValue(2026).startRoom)
        assertEquals(money("41800.00"), room.getValue(2026).endRoom)
    }

    @Test
    fun `the years go from eligibility to the requested year`() {
        val profile = Profile(2001, null)
        val room = TfsaEngine.roomByYear(
            profile,
            tfsaLimits(2019 to "6000.00", 2020 to "6000.00"),
            emptyList(),
            upTo = 2020,
        )

        assertEquals(listOf(2019, 2020), room.map { it.year })
    }

    @Test
    fun `FHSA transactions are ignored by the TFSA engine`() {
        val profile = Profile(2001, LocalDate.of(2023, 4, 1))
        val transactions = listOf(
            Transaction(Account.FHSA, LocalDate.of(2019, 5, 1), TransactionType.DEPOSIT, money("5000.00")),
        )

        val room = TfsaEngine.roomByYear(
            profile,
            tfsaLimits(2019 to "6000.00"),
            transactions,
            upTo = 2019,
        )

        assertEquals(money("0.00"), room.single().deposits)
        assertEquals(money("6000.00"), room.single().endRoom)
    }

    @Test
    fun `a withdrawal does not restore room in the year of the withdrawal`() {
        val profile = Profile(2001, null)
        val limits = tfsaLimits(2019 to "6000.00", 2020 to "6000.00")
        val transactions = listOf(
            deposit("2020-03-01", "6000.00"),
            withdrawal("2020-08-01", "6000.00"),
        )

        val room = TfsaEngine.roomByYear(profile, limits, transactions, upTo = 2020)
            .associateBy { it.year }

        // 6000 (end 2019) + 6000 (limit 2020) + 0 (2019 withdrawals) = 12000.
        assertEquals(money("12000.00"), room.getValue(2020).startRoom)
        assertEquals(money("6000.00"), room.getValue(2020).withdrawals)
        assertEquals(money("6000.00"), room.getValue(2020).endRoom)
    }

    @Test
    fun `a withdrawal restores room the following january 1`() {
        val profile = Profile(2001, null)
        val limits = tfsaLimits(
            2019 to "6000.00",
            2020 to "6000.00",
            2021 to "6000.00",
        )
        val transactions = listOf(
            deposit("2020-03-01", "6000.00"),
            withdrawal("2020-08-01", "6000.00"),
        )

        val room = TfsaEngine.roomByYear(profile, limits, transactions, upTo = 2021)
            .associateBy { it.year }

        // 6000 (end 2020) + 6000 (limit 2021) + 6000 (2020 withdrawals) = 18000.
        assertEquals(money("18000.00"), room.getValue(2021).startRoom)
        assertEquals(money("18000.00"), room.getValue(2021).endRoom)
    }

    @Test
    fun `an over-contribution carries to the next year without being erased`() {
        val profile = Profile(2001, null)
        val limits = tfsaLimits(2019 to "6000.00", 2020 to "6000.00")
        val transactions = listOf(deposit("2019-05-01", "10000.00"))

        val room = TfsaEngine.roomByYear(profile, limits, transactions, upTo = 2020)
            .associateBy { it.year }

        // 6000 - 10000 = -4000; a MAX(..., 0) would hide it.
        assertEquals(money("-4000.00"), room.getValue(2019).endRoom)
        // -4000 + 6000 = 2000: the excess is absorbed by the next limit.
        assertEquals(money("2000.00"), room.getValue(2020).startRoom)
    }

    @Test
    fun `a year without a limit is flagged and creates no room`() {
        val profile = Profile(2001, null)
        val limits = tfsaLimits(2019 to "6000.00")

        val room = TfsaEngine.roomByYear(profile, limits, emptyList(), upTo = 2020)
            .associateBy { it.year }

        assertEquals(false, room.getValue(2019).limitMissing)
        assertEquals(true, room.getValue(2020).limitMissing)
        assertEquals(money("0.00"), room.getValue(2020).limit)
        assertEquals(money("6000.00"), room.getValue(2020).endRoom)
    }

    @Test
    fun `an unconfirmed limit is treated as absent`() {
        val profile = Profile(2001, null)
        val limits = listOf(
            AnnualLimit(Account.TFSA, 2019, money("6000.00")),
            AnnualLimit(Account.TFSA, 2020, money("6000.00"), confirmed = false),
        )

        val room = TfsaEngine.roomByYear(profile, limits, emptyList(), upTo = 2020)
            .associateBy { it.year }

        assertEquals(true, room.getValue(2020).limitMissing)
        assertEquals(money("0.00"), room.getValue(2020).limit)
        assertEquals(money("6000.00"), room.getValue(2020).endRoom)
    }
}
