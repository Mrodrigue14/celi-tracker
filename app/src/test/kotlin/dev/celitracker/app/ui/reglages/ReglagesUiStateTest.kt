package dev.celitracker.app.ui.reglages

import dev.celitracker.engine.Compte
import dev.celitracker.engine.PlafondAnnuel
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class ReglagesUiStateTest {

    private val plafonds = (2019..2023).map { PlafondAnnuel(Compte.CELI, it, BigDecimal("6000.00"), confirme = true) }

    @Test
    fun `seuls les plafonds depuis l'admissibilite sont pertinents`() {
        // Naissance en 2003: admissible au CELI en 2021.
        val etat = ReglagesUiState(anneeNaissance = "2003", plafonds = plafonds)

        assertEquals(listOf(2021, 2022, 2023), etat.plafondsPertinents.map { it.annee })
        assertEquals(listOf(2019, 2020), etat.plafondsAnterieurs.map { it.annee })
    }

    @Test
    fun `sans annee de naissance, tous les plafonds restent visibles`() {
        val etat = ReglagesUiState(plafonds = plafonds)

        assertEquals(plafonds, etat.plafondsPertinents)
        assertEquals(emptyList(), etat.plafondsAnterieurs)
    }
}
