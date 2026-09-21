package dev.celitracker.app.ui.settings

import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsUiStateTest {

    private val limits = (2019..2023).map { AnnualLimit(Account.TFSA, it, BigDecimal("6000.00"), confirmed = true) }

    @Test
    fun `only limits from eligibility onward are relevant`() {
        // Born in 2003: eligible for the TFSA in 2021.
        val state = SettingsUiState(birthYear = "2003", limits = limits)

        assertEquals(listOf(2021, 2022, 2023), state.relevantLimits.map { it.year })
        assertEquals(listOf(2019, 2020), state.earlierLimits.map { it.year })
    }

    @Test
    fun `without a birth year, all limits remain visible`() {
        val state = SettingsUiState(limits = limits)

        assertEquals(limits, state.relevantLimits)
        assertEquals(emptyList(), state.earlierLimits)
    }

    @Test
    fun `a CRA figure needs a past date and an amount`() {
        val state = SettingsUiState(snapshotDate = LocalDate.now().toString(), snapshotAmount = "41800")

        assertTrue(state.isNewSnapshotValid)
    }

    @Test
    fun `a CRA figure of zero is valid`() {
        assertTrue(SettingsUiState(snapshotDate = LocalDate.now().toString(), snapshotAmount = "0").isNewSnapshotValid)
    }

    @Test
    fun `a future date is rejected and flagged`() {
        val state = SettingsUiState(snapshotDate = LocalDate.now().plusDays(1).toString(), snapshotAmount = "100")

        assertFalse(state.isNewSnapshotValid)
        assertTrue(state.invalidSnapshotDate)
    }

    @Test
    fun `an empty date is not flagged as an error, only incomplete`() {
        val state = SettingsUiState(snapshotAmount = "100")

        assertFalse(state.isNewSnapshotValid)
        assertFalse(state.invalidSnapshotDate)
    }

    @Test
    fun `a CRA figure without an amount is incomplete`() {
        assertFalse(SettingsUiState(snapshotDate = LocalDate.now().toString()).isNewSnapshotValid)
    }
}
