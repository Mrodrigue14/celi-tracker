package dev.celitracker.app.ui.accueil

import dev.celitracker.engine.DroitsAnnee
import dev.celitracker.engine.DroitsAnneeCeliapp
import dev.celitracker.engine.ExcedentMensuel
import dev.celitracker.engine.Profil
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AccueilUiStateTest {

    private val profil = Profil(
        anneeAdmissibiliteCeli = 2010,
        anneeNaissance = 1995,
        dateOuvertureCeliapp = LocalDate.of(2023, 4, 1),
    )

    @Test
    fun `etat sans profil est un etat vide`() {
        val etat = AccueilUiState(profil = null, anneeCourante = 2026, moisCourant = 9)

        assertFalse(etat.profilEnregistre)
        assertNull(etat.celiAnneeCourante)
        assertNull(etat.celiappAnneeCourante)
        assertNull(etat.echeanceParticipationCeliapp)
    }

    @Test
    fun `celiAnneeCourante isole la ligne de l'annee courante`() {
        val ligne2025 = ligneCeli(2025)
        val ligne2026 = ligneCeli(2026)
        val etat = AccueilUiState(
            profil = profil,
            anneeCourante = 2026,
            moisCourant = 9,
            droitsCeli = listOf(ligne2025, ligne2026),
        )

        assertEquals(ligne2026, etat.celiAnneeCourante)
    }

    @Test
    fun `excedentCeliCourant filtre sur annee ET mois`() {
        val excedentAout = ExcedentMensuel(2026, 8, BigDecimal("100.00"), BigDecimal("1.00"))
        val excedentSeptembre = ExcedentMensuel(2026, 9, BigDecimal("200.00"), BigDecimal("2.00"))
        val etat = AccueilUiState(
            profil = profil,
            anneeCourante = 2026,
            moisCourant = 9,
            excedentsCeli = listOf(excedentAout, excedentSeptembre),
        )

        assertEquals(excedentSeptembre, etat.excedentCeliCourant)
    }

    @Test
    fun `echeanceParticipationCeliapp derive du profil, pas d'un champ stocke`() {
        val etat = AccueilUiState(profil = profil, anneeCourante = 2026, moisCourant = 9)

        // Ouverture en 2023: 15 ans -> 2038; naissance 1995: 71 ans -> 2066.
        // Le premier des deux l'emporte.
        assertEquals(LocalDate.of(2038, 12, 31), etat.echeanceParticipationCeliapp)
    }

    private fun ligneCeli(annee: Int) = DroitsAnnee(
        annee = annee,
        plafond = BigDecimal("7000.00"),
        droitsDebut = BigDecimal("7000.00"),
        depots = BigDecimal.ZERO,
        retraits = BigDecimal.ZERO,
        droitsFin = BigDecimal("7000.00"),
        plafondManquant = false,
    )
}
