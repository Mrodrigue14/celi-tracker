package dev.celitracker.app.ui.home

import dev.celitracker.engine.FhsaYear
import dev.celitracker.engine.MonthlyExcess
import dev.celitracker.engine.Profile
import dev.celitracker.engine.TfsaYear
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class HomeUiStateTest {

    private val profile = Profile(
        birthYear = 1995,
        fhsaOpeningDate = LocalDate.of(2023, 4, 1),
    )

    @Test
    fun `state without a profile is an empty state`() {
        val state = HomeUiState(profile = null, currentYear = 2026, currentMonth = 9)

        assertFalse(state.hasProfile)
        assertNull(state.tfsaCurrentYear)
        assertNull(state.fhsaCurrentYear)
        assertNull(state.fhsaParticipationDeadline)
    }

    @Test
    fun `tfsaCurrentYear isolates the current year's row`() {
        val row2025 = tfsaRow(2025)
        val row2026 = tfsaRow(2026)
        val state = HomeUiState(
            profile = profile,
            currentYear = 2026,
            currentMonth = 9,
            tfsaRoom = listOf(row2025, row2026),
        )

        assertEquals(row2026, state.tfsaCurrentYear)
    }

    @Test
    fun `currentTfsaExcess filters by year AND month`() {
        val augustExcess = MonthlyExcess(2026, 8, BigDecimal("100.00"), BigDecimal("1.00"))
        val septemberExcess = MonthlyExcess(2026, 9, BigDecimal("200.00"), BigDecimal("2.00"))
        val state = HomeUiState(
            profile = profile,
            currentYear = 2026,
            currentMonth = 9,
            tfsaExcesses = listOf(augustExcess, septemberExcess),
        )

        assertEquals(septemberExcess, state.currentTfsaExcess)
    }

    @Test
    fun `fhsaParticipationDeadline is derived from the profile, not a stored field`() {
        val state = HomeUiState(profile = profile, currentYear = 2026, currentMonth = 9)

        // Opened 2023 gives 2038, birth 1995 gives 2066: the earlier wins.
        assertEquals(LocalDate.of(2038, 12, 31), state.fhsaParticipationDeadline)
    }

    @Test
    fun `the ring shows the share of the year's room already contributed`() {
        val row = tfsaRow(2026).copy(startRoom = BigDecimal("8000.00"), deposits = BigDecimal("2000.00"))
        val state = HomeUiState(profile = profile, currentYear = 2026, currentMonth = 9, tfsaRoom = listOf(row))

        assertEquals(0.25f, state.tfsaUsedFraction)
    }

    @Test
    fun `an overcontribution exceeds 100 percent instead of being capped`() {
        val row = tfsaRow(2026).copy(startRoom = BigDecimal("1000.00"), deposits = BigDecimal("1500.00"))
        val state = HomeUiState(profile = profile, currentYear = 2026, currentMonth = 9, tfsaRoom = listOf(row))

        assertEquals(1.5f, state.tfsaUsedFraction)
    }

    @Test
    fun `with no room this year, no ring rather than a division by zero`() {
        val row = tfsaRow(2026).copy(startRoom = BigDecimal.ZERO)
        val state = HomeUiState(profile = profile, currentYear = 2026, currentMonth = 9, tfsaRoom = listOf(row))

        assertNull(state.tfsaUsedFraction)
    }

    @Test
    fun `a missing limit makes usage unknown rather than alarming`() {
        val row = tfsaRow(2026).copy(startRoom = BigDecimal.ZERO, deposits = BigDecimal("100.00"), limitMissing = true)
        val state = HomeUiState(profile = profile, currentYear = 2026, currentMonth = 9, tfsaRoom = listOf(row))

        assertNull(state.tfsaUsage)
    }

    private fun tfsaRow(year: Int) = TfsaYear(
        year = year,
        limit = BigDecimal("7000.00"),
        startRoom = BigDecimal("7000.00"),
        deposits = BigDecimal.ZERO,
        withdrawals = BigDecimal.ZERO,
        endRoom = BigDecimal("7000.00"),
        limitMissing = false,
    )
}
