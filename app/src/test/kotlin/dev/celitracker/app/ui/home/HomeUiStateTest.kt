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
    fun `etat sans profil est un etat vide`() {
        val state = HomeUiState(profile = null, currentYear = 2026, currentMonth = 9)

        assertFalse(state.hasProfile)
        assertNull(state.tfsaCurrentYear)
        assertNull(state.fhsaCurrentYear)
        assertNull(state.fhsaParticipationDeadline)
    }

    @Test
    fun `celiAnneeCourante isole la ligne de l'annee courante`() {
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
    fun `excedentCeliCourant filtre sur annee ET mois`() {
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
    fun `echeanceParticipationCeliapp derive du profil, pas d'un champ stocke`() {
        val state = HomeUiState(profile = profile, currentYear = 2026, currentMonth = 9)

        // Ouverture en 2023: 15 ans -> 2038; birth 1995: 71 ans -> 2066.
        // Le earliest des deux l'emporte.
        assertEquals(LocalDate.of(2038, 12, 31), state.fhsaParticipationDeadline)
    }

    @Test
    fun `l'anneau montre la part des droits de l'annee deja cotisee`() {
        val row = tfsaRow(2026).copy(startRoom = BigDecimal("8000.00"), deposits = BigDecimal("2000.00"))
        val state = HomeUiState(profile = profile, currentYear = 2026, currentMonth = 9, tfsaRoom = listOf(row))

        assertEquals(0.25f, state.tfsaUsedFraction)
    }

    @Test
    fun `une sur-cotisation depasse 100 pour cent au lieu d'etre ecretee`() {
        val row = tfsaRow(2026).copy(startRoom = BigDecimal("1000.00"), deposits = BigDecimal("1500.00"))
        val state = HomeUiState(profile = profile, currentYear = 2026, currentMonth = 9, tfsaRoom = listOf(row))

        assertEquals(1.5f, state.tfsaUsedFraction)
    }

    @Test
    fun `sans droits cette annee, pas d'anneau plutot qu'une division par zero`() {
        val row = tfsaRow(2026).copy(startRoom = BigDecimal.ZERO)
        val state = HomeUiState(profile = profile, currentYear = 2026, currentMonth = 9, tfsaRoom = listOf(row))

        assertNull(state.tfsaUsedFraction)
    }

    @Test
    fun `un plafond manquant rend l'utilisation inconnue plutot qu'alarmante`() {
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
