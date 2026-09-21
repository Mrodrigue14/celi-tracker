package dev.celitracker.app.ui.settings

import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
