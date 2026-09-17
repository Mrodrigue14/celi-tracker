package dev.celitracker.data

import androidx.room.Room
import dev.celitracker.engine.Compte
import dev.celitracker.engine.PLAFONDS_CELI_PUBLIES
import dev.celitracker.engine.PlafondAnnuel
import kotlinx.coroutines.test.runTest
import java.io.File
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlafondsParDefautTest {

    private val fichier = File.createTempFile("celi-tracker-plafonds", ".db")
    private val base = configurerBase(Room.databaseBuilder<CeliTrackerBase>(name = fichier.absolutePath))
    private val depot = Depot(base)

    @AfterTest
    fun fermer() {
        base.close()
        fichier.delete()
    }

    @Test
    fun `les plafonds publies sont inscrits et confirmes`() = runTest {
        depot.garnirPlafondsPublies()

        val enregistres = depot.plafonds().filter { it.compte == Compte.CELI }
        assertEquals(PLAFONDS_CELI_PUBLIES.size, enregistres.size)
        assertTrue(enregistres.all { it.confirme })
        assertEquals(BigDecimal("7000.00"), enregistres.single { it.annee == 2026 }.montant)
        assertEquals(BigDecimal("10000.00"), enregistres.single { it.annee == 2015 }.montant)
    }

    @Test
    fun `un plafond deja saisi n'est pas ecrase`() = runTest {
        depot.enregistrerPlafond(PlafondAnnuel(Compte.CELI, 2026, BigDecimal("6500.00"), confirme = true))

        depot.garnirPlafondsPublies()

        assertEquals(BigDecimal("6500.00"), depot.plafonds().single { it.annee == 2026 }.montant)
    }
}
