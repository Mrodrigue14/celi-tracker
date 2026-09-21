package dev.celitracker.app.ui.settings

import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsUiStateTest {

    private val limits = (2019..2023).map { AnnualLimit(Account.TFSA, it, BigDecimal("6000.00"), confirmed = true) }

    @Test
    fun `seuls les plafonds depuis l'admissibilite sont pertinents`() {
        // Naissance en 2003: admissible au TFSA en 2021.
        val state = SettingsUiState(birthYear = "2003", limits = limits)

        assertEquals(listOf(2021, 2022, 2023), state.relevantLimits.map { it.year })
        assertEquals(listOf(2019, 2020), state.earlierLimits.map { it.year })
    }

    @Test
    fun `sans annee de naissance, tous les plafonds restent visibles`() {
        val state = SettingsUiState(limits = limits)

        assertEquals(limits, state.relevantLimits)
        assertEquals(emptyList(), state.earlierLimits)
    }
}
